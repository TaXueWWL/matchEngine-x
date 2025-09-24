package com.thunder.matchenginex.engine;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderStatus;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.model.Command;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.orderbook.OrderBookManager;
import com.thunder.matchenginex.orderbook.PriceLevel;
import com.thunder.matchenginex.service.AccountService;
import com.thunder.matchenginex.util.CurrencyUtils;
import com.thunder.matchenginex.websocket.OrderBookWebSocketController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingEngine {

    private final OrderBookManager orderBookManager;
    private final AtomicLong tradeIdGenerator = new AtomicLong(1);

    // Lazy injection to avoid circular dependency - 延迟注入以避免循环依赖
    @Autowired
    @Lazy
    private OrderBookWebSocketController webSocketController;

    @Autowired
    @Lazy
    private AccountService accountService;

    @Autowired
    @Lazy
    private CurrencyUtils currencyUtils;

    public void placeOrder(Command command) {
        Order order = createOrderFromCommand(command);
        OrderBook orderBook = orderBookManager.getOrderBook(command.getSymbol());

        log.info("Placing order: {} {} {} @ {} qty: {}",
                order.getOrderId(), order.getSymbol(), order.getSide(),
                order.getPrice(), order.getQuantity());

        // Handle different order types - 处理不同的Order类型
        switch (order.getType()) {
            case LIMIT:
                processLimitOrder(orderBook, order);
                break;
            case MARKET:
                processMarketOrder(orderBook, order);
                break;
            case IOC:
                processIocOrder(orderBook, order);
                break;
            case FOK:
                processFokOrder(orderBook, order);
                break;
            case POST_ONLY:
                processPostOnlyOrder(orderBook, order);
                break;
            default:
                log.error("Unsupported order type: {}", order.getType());
                order.setStatus(OrderStatus.REJECTED);
        }
    }

    public void cancelOrder(Command command) {
        OrderBook orderBook = orderBookManager.getOrderBook(command.getSymbol());
        Order order = orderBook.getOrder(command.getOrderId());

        if (order == null) {
            log.warn("Order not found for cancellation: {}", command.getOrderId());
            return;
        }

        // Set order status to cancelled - 设置Order状态为取消
        order.cancel();

        // Remove from order book - 从OrderBook中移除
        boolean removed = orderBook.removeOrder(command.getOrderId());

        if (removed) {
            // Release frozen funds for the cancelled order - 释放已取消Order的Frozen资金
            releaseFrozenFunds(command.getSymbol(), order);

            // Add cancelled order to historical orders - 将已取消的Order添加到历史订单
            orderBook.addHistoricalOrder(order);

            log.info("Cancelled order: {} and released frozen funds", command.getOrderId());
        } else {
            log.warn("Failed to remove order from order book: {}", command.getOrderId());
        }
    }

    public void modifyOrder(Command command) {
        // Modify order by cancelling and placing new order - 通过取消并放置新Order来修改订单
        OrderBook orderBook = orderBookManager.getOrderBook(command.getSymbol());
        Order existingOrder = orderBook.getOrder(command.getOrderId());

        if (existingOrder == null) {
            log.warn("Order not found for modification: {}", command.getOrderId());
            return;
        }

        // Remove existing order
        orderBook.removeOrder(command.getOrderId());

        // Create new order with modified parameters
        Order newOrder = Order.builder()
                .orderId(command.getOrderId())
                .symbol(command.getSymbol())
                .userId(command.getUserId())
                .side(existingOrder.getSide())
                .type(existingOrder.getType())
                .price(command.getPrice() != null ? command.getPrice() : existingOrder.getPrice())
                .quantity(command.getQuantity() != null ? command.getQuantity() : existingOrder.getQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .remainingQuantity(command.getQuantity() != null ? command.getQuantity() : existingOrder.getQuantity())
                .status(OrderStatus.NEW)
                .timestamp(Instant.now().toEpochMilli())
                .build();

        log.info("Modified order: {} new price: {} new qty: {}",
                command.getOrderId(), newOrder.getPrice(), newOrder.getQuantity());

        // Process the modified order
        processLimitOrder(orderBook, newOrder);
    }

    public void queryOrder(Command command) {
        OrderBook orderBook = orderBookManager.getOrderBook(command.getSymbol());
        Order order = orderBook.getOrder(command.getOrderId());

        if (order != null) {
            log.info("Order query result: {}", order);
        } else {
            log.info("Order not found: {}", command.getOrderId());
        }
    }

    public void queryOrderBook(Command command) {
        OrderBook orderBook = orderBookManager.getOrderBook(command.getSymbol());
        log.info("Order book for {}: {} buy levels, {} sell levels",
                command.getSymbol(),
                orderBook.getBuyLevels(10).size(),
                orderBook.getSellLevels(10).size());
    }

    private Order createOrderFromCommand(Command command) {
        return new Order(
                command.getOrderId(),
                command.getSymbol(),
                command.getUserId(),
                command.getSide(),
                command.getOrderType(),
                command.getPrice(),
                command.getQuantity()
        );
    }

    private void processLimitOrder(OrderBook orderBook, Order order) {
        List<Trade> trades = tryMatchOrder(orderBook, order);

        // Add remaining quantity to order book if not fully filled - 如果未完全成交，将剩余数量添加到OrderBook
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0
                && order.getStatus() != OrderStatus.CANCELLED) {
            orderBook.addOrder(order);
        }

        processTrades(trades);

        // Add taker order to historical orders if fully filled - 如果完全成交，将Taker Order添加到历史订单
        if (order.getStatus() == OrderStatus.FILLED) {
            orderBook.addHistoricalOrder(order);
        }
    }

    private void processMarketOrder(OrderBook orderBook, Order order) {
        List<Trade> trades = tryMatchOrder(orderBook, order);

        // Market orders should not be added to order book - Market Order不应添加到OrderBook
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.CANCELLED);
            log.warn("Market order {} partially filled and cancelled. Remaining: {}",
                    order.getOrderId(), order.getRemainingQuantity());
        }

        processTrades(trades);

        // Add market order to historical orders if fully filled or cancelled - 如果完全成交或取消，将Market Order添加到历史订单
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.CANCELLED) {
            orderBook.addHistoricalOrder(order);
        }
    }

    private void processIocOrder(OrderBook orderBook, Order order) {
        List<Trade> trades = tryMatchOrder(orderBook, order);

        // IOC orders are immediately cancelled if not fully filled - IOC Order如果未完全成交会立即取消
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.CANCELLED);
        }

        processTrades(trades);

        // Add IOC order to historical orders if fully filled or cancelled - 如果完全成交或取消，将IOC Order添加到历史订单
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.CANCELLED) {
            orderBook.addHistoricalOrder(order);
        }
    }

    private void processFokOrder(OrderBook orderBook, Order order) {
        // Check if the entire order can be filled - 检查是否可以完全成交整个Order
        if (canFillEntireOrder(orderBook, order)) {
            List<Trade> trades = tryMatchOrder(orderBook, order);
            processTrades(trades);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            log.info("FOK order {} cancelled - cannot fill entire quantity", order.getOrderId());
        }

        // Add FOK order to historical orders if fully filled or cancelled - 如果完全成交或取消，将FOK Order添加到历史订单
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.CANCELLED) {
            orderBook.addHistoricalOrder(order);
        }
    }

    private void processPostOnlyOrder(OrderBook orderBook, Order order) {
        // Check if order would immediately match - 检查Order是否会立即匹配
        if (wouldImmediatelyMatch(orderBook, order)) {
            order.setStatus(OrderStatus.CANCELLED);
            log.info("Post-only order {} cancelled - would immediately match", order.getOrderId());
        } else {
            orderBook.addOrder(order);
        }
    }

    private List<Trade> tryMatchOrder(OrderBook orderBook, Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();

        while (incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            PriceLevel bestLevel = getBestMatchingLevel(orderBook, incomingOrder);
            if (bestLevel == null || !canMatch(incomingOrder, bestLevel.getPrice())) {
                break;
            }

            Order bestOrder = bestLevel.peekFirstOrder();
            if (bestOrder == null) {
                break;
            }

            BigDecimal tradeQuantity = incomingOrder.getRemainingQuantity()
                    .min(bestOrder.getRemainingQuantity());
            BigDecimal tradePrice = bestOrder.getPrice(); // Price priority - this is the actual trade price

            // Create trade - 创建交易
            Trade trade = createTrade(incomingOrder, bestOrder, tradePrice, tradeQuantity);
            trades.add(trade);

            // Update orders - 更新Order
            BigDecimal bestOrderOldRemaining = bestOrder.getRemainingQuantity();
            incomingOrder.fill(tradeQuantity);
            bestOrder.fill(tradeQuantity);

            // Update PriceLevel total quantity for the maker order - 更新Maker Order的PriceLevel总数量
            bestLevel.updateQuantity(bestOrder, bestOrderOldRemaining, bestOrder.getRemainingQuantity());

            // Update last trade price for WebSocket push - 为WebSocket推送更新最新交易价格
            // The trade price is determined by the maker order (bestOrder) price - 交易价格由Maker Order (bestOrder) 价格决定
            // This follows the correct logic: - 这遵循正确的逻辑：
            // - For buy taker: trade price = sell maker price (ask price) - 买方Taker：交易价格 = 卖方Maker价格 (卖一价)
            // - For sell taker: trade price = buy maker price (bid price) - 卖方Taker：交易价格 = 买方Maker价格 (买一价)
            if (webSocketController != null) {
                webSocketController.updateLastTradePrice(incomingOrder.getSymbol(), tradePrice);
                log.debug("Updated last trade price for {}: {}", incomingOrder.getSymbol(), tradePrice);
            }

            // Remove fully filled order from order book and add to historical orders - 从 OrderBook 移除已完全成交的 Order 并添加到历史订单
            if (bestOrder.isFullyFilled()) {
                bestLevel.pollFirstOrder();
                orderBook.addHistoricalOrder(bestOrder); // Add completed maker order to historical orders - 将完成的Maker Order添加到历史订单
                if (bestLevel.isEmpty()) {
                    if (incomingOrder.getSide() == OrderSide.BUY) {
                        orderBook.getSellLevels().remove(bestLevel.getPrice());
                    } else {
                        orderBook.getBuyLevels().remove(bestLevel.getPrice());
                    }
                }
            }

            log.debug("Trade executed: {} @ {} qty: {}", trade.getSymbol(), tradePrice, tradeQuantity);
        }

        return trades;
    }

    private PriceLevel getBestMatchingLevel(OrderBook orderBook, Order order) {
        return order.getSide() == OrderSide.BUY ?
                orderBook.getBestSellLevel() : orderBook.getBestBuyLevel();
    }

    private boolean canMatch(Order order, BigDecimal price) {
        if (order.getSide() == OrderSide.BUY) {
            return order.getPrice().compareTo(price) >= 0;
        } else {
            return order.getPrice().compareTo(price) <= 0;
        }
    }

    private boolean wouldImmediatelyMatch(OrderBook orderBook, Order order) {
        PriceLevel bestLevel = getBestMatchingLevel(orderBook, order);
        return bestLevel != null && canMatch(order, bestLevel.getPrice());
    }

    private boolean canFillEntireOrder(OrderBook orderBook, Order order) {
        BigDecimal remainingQuantity = order.getQuantity();
        PriceLevel bestLevel = getBestMatchingLevel(orderBook, order);

        while (bestLevel != null && remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            if (!canMatch(order, bestLevel.getPrice())) {
                break;
            }

            remainingQuantity = remainingQuantity.subtract(
                    bestLevel.getTotalQuantity().min(remainingQuantity)
            );

            // Move to next price level - 移动到下一个价格层级
            if (order.getSide() == OrderSide.BUY) {
                bestLevel = orderBook.getSellLevels().higherEntry(bestLevel.getPrice()).getValue();
            } else {
                bestLevel = orderBook.getBuyLevels().lowerEntry(bestLevel.getPrice()).getValue();
            }
        }

        return remainingQuantity.compareTo(BigDecimal.ZERO) == 0;
    }

    private Trade createTrade(Order buyOrder, Order sellOrder, BigDecimal price, BigDecimal quantity) {
        Order actualBuyOrder = buyOrder.getSide() == OrderSide.BUY ? buyOrder : sellOrder;
        Order actualSellOrder = buyOrder.getSide() == OrderSide.SELL ? buyOrder : sellOrder;

        return Trade.builder()
                .tradeId(tradeIdGenerator.getAndIncrement())
                .symbol(buyOrder.getSymbol())
                .buyOrderId(actualBuyOrder.getOrderId())
                .sellOrderId(actualSellOrder.getOrderId())
                .buyUserId(actualBuyOrder.getUserId())
                .sellUserId(actualSellOrder.getUserId())
                .price(price)
                .quantity(quantity)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }

    private void processTrades(List<Trade> trades) {
        for (Trade trade : trades) {
            log.info("Trade executed: ID={}, Symbol={}, Price={}, Quantity={}, BuyOrder={}, SellOrder={}",
                    trade.getTradeId(), trade.getSymbol(), trade.getPrice(), trade.getQuantity(),
                    trade.getBuyOrderId(), trade.getSellOrderId());

            // Execute the actual fund transfer - 执行实际的资金转移
            executeFundTransfer(trade);
        }
    }

    private void executeFundTransfer(Trade trade) {
        String baseCurrency = currencyUtils.extractBaseCurrency(trade.getSymbol());
        String quoteCurrency = currencyUtils.extractQuoteCurrency(trade.getSymbol());

        BigDecimal tradeAmount = trade.getPrice().multiply(trade.getQuantity()); // Total USDT value - 总USDT价值
        BigDecimal tradeQuantity = trade.getQuantity(); // BTC quantity - BTC数量

        long buyUserId = trade.getBuyUserId();
        long sellUserId = trade.getSellUserId();

        // Transfer USDT from buyer's frozen balance to seller's available balance - 从Buyer的Frozen Balance转移USDT到Seller的Available Balance
        boolean usdtTransferSuccess = accountService.transferFromFrozen(buyUserId, sellUserId, quoteCurrency, tradeAmount);
        if (!usdtTransferSuccess) {
            log.error("Failed to transfer {} {} from buy user {} to sell user {}",
                    tradeAmount, quoteCurrency, buyUserId, sellUserId);
        }

        // Transfer BTC from seller's frozen balance to buyer's available balance - 从Seller的Frozen Balance转移BTC到Buyer的Available Balance
        boolean btcTransferSuccess = accountService.transferFromFrozen(sellUserId, buyUserId, baseCurrency, tradeQuantity);
        if (!btcTransferSuccess) {
            log.error("Failed to transfer {} {} from sell user {} to buy user {}",
                    tradeQuantity, baseCurrency, sellUserId, buyUserId);
        }

        if (usdtTransferSuccess && btcTransferSuccess) {
            log.info("Fund transfer executed successfully: Buy user {} received {} {}, Sell user {} received {} {}",
                    buyUserId, tradeQuantity, baseCurrency,
                    sellUserId, tradeAmount, quoteCurrency);
        }
    }

    private void releaseFrozenFunds(String symbol, Order order) {
        if (order.getSide() == OrderSide.BUY) {
            String quoteCurrency = currencyUtils.extractQuoteCurrency(symbol);
            // For buy orders, release price * remaining quantity - 对于Buy Order，释放价格 * 剩余数量
            BigDecimal frozenAmount = order.getPrice().multiply(order.getRemainingQuantity());

            accountService.unfreezeBalance(order.getUserId(), quoteCurrency, frozenAmount);
            log.info("Released frozen balance for cancelled buy order: userId={}, currency={}, amount={}",
                order.getUserId(), quoteCurrency, frozenAmount);
        }
        else if (order.getSide() == OrderSide.SELL) {
            String baseCurrency = currencyUtils.extractBaseCurrency(symbol);
            // For sell orders, release remaining quantity - 对于Sell Order，释放剩余数量
            accountService.unfreezeBalance(order.getUserId(), baseCurrency, order.getRemainingQuantity());
            log.info("Released frozen balance for cancelled sell order: userId={}, currency={}, amount={}",
                order.getUserId(), baseCurrency, order.getRemainingQuantity());
        }
    }
}