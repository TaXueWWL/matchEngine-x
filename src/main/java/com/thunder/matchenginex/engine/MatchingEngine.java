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

    // Lazy injection to avoid circular dependency
    @Autowired
    @Lazy
    private com.thunder.matchenginex.websocket.OrderBookWebSocketController webSocketController;

    public void placeOrder(Command command) {
        Order order = createOrderFromCommand(command);
        OrderBook orderBook = orderBookManager.getOrderBook(command.getSymbol());

        log.info("Placing order: {} {} {} @ {} qty: {}",
                order.getOrderId(), order.getSymbol(), order.getSide(),
                order.getPrice(), order.getQuantity());

        // Handle different order types
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
        boolean removed = orderBook.removeOrder(command.getOrderId());

        if (removed) {
            log.info("Cancelled order: {}", command.getOrderId());
        } else {
            log.warn("Order not found for cancellation: {}", command.getOrderId());
        }
    }

    public void modifyOrder(Command command) {
        // Modify order by cancelling and placing new order
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

        // Add remaining quantity to order book if not fully filled
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0
                && order.getStatus() != OrderStatus.CANCELLED) {
            orderBook.addOrder(order);
        }

        processTrades(trades);
    }

    private void processMarketOrder(OrderBook orderBook, Order order) {
        List<Trade> trades = tryMatchOrder(orderBook, order);

        // Market orders should not be added to order book
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.CANCELLED);
            log.warn("Market order {} partially filled and cancelled. Remaining: {}",
                    order.getOrderId(), order.getRemainingQuantity());
        }

        processTrades(trades);
    }

    private void processIocOrder(OrderBook orderBook, Order order) {
        List<Trade> trades = tryMatchOrder(orderBook, order);

        // IOC orders are immediately cancelled if not fully filled
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.CANCELLED);
        }

        processTrades(trades);
    }

    private void processFokOrder(OrderBook orderBook, Order order) {
        // Check if the entire order can be filled
        if (canFillEntireOrder(orderBook, order)) {
            List<Trade> trades = tryMatchOrder(orderBook, order);
            processTrades(trades);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            log.info("FOK order {} cancelled - cannot fill entire quantity", order.getOrderId());
        }
    }

    private void processPostOnlyOrder(OrderBook orderBook, Order order) {
        // Check if order would immediately match
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

            // Create trade
            Trade trade = createTrade(incomingOrder, bestOrder, tradePrice, tradeQuantity);
            trades.add(trade);

            // Update orders
            incomingOrder.fill(tradeQuantity);
            bestOrder.fill(tradeQuantity);

            // Update last trade price for WebSocket push
            // The trade price is determined by the maker order (bestOrder) price
            // This follows the correct logic:
            // - For buy taker: trade price = sell maker price (ask price)
            // - For sell taker: trade price = buy maker price (bid price)
            if (webSocketController != null) {
                webSocketController.updateLastTradePrice(incomingOrder.getSymbol(), tradePrice);
                log.debug("Updated last trade price for {}: {}", incomingOrder.getSymbol(), tradePrice);
            }

            // Remove fully filled order from order book
            if (bestOrder.isFullyFilled()) {
                bestLevel.pollFirstOrder();
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

            // Move to next price level
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
        }
    }
}