package com.thunder.matchenginex.service;

import com.thunder.matchenginex.config.TradingPair;
import com.thunder.matchenginex.config.TradingPairsConfig;
import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderStatus;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.event.OrderEventProducer;
import com.thunder.matchenginex.model.Command;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.orderbook.OrderBookManager;
import com.thunder.matchenginex.service.AccountService;
import com.thunder.matchenginex.constant.TradingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {

    private final OrderEventProducer eventProducer;
    private final OrderBookManager orderBookManager;
    private final TradingPairsConfig tradingPairsConfig;
    private final AccountService accountService;
    private final com.thunder.matchenginex.util.CurrencyUtils currencyUtils;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);

    public long placeOrder(String symbol, long userId, OrderSide side, OrderType orderType,
                          BigDecimal price, BigDecimal quantity) {
        long orderId = orderIdGenerator.getAndIncrement();

        // Immediately freeze the required funds to prevent overselling - 立即冻结所需资金以防止过度销售
        freezeOrderFunds(symbol, userId, side, orderType, price, quantity);

        Command command = Command.placeOrder(orderId, symbol, userId, side, orderType, price, quantity);
        eventProducer.publishCommand(command);

        log.info("Submitted place order: {} {} {} @ {} qty: {}",
                orderId, symbol, side, price, quantity);

        return orderId;
    }

    public boolean cancelOrder(long orderId, String symbol, long userId) {
        Command command = Command.cancelOrder(orderId, symbol, userId);
        eventProducer.publishCommand(command);

        log.info("Submitted cancel order: {}", orderId);
        return true;
    }

    public boolean modifyOrder(long orderId, String symbol, long userId,
                             BigDecimal newPrice, BigDecimal newQuantity) {
        Command command = Command.modifyOrder(orderId, symbol, userId, newPrice, newQuantity);
        eventProducer.publishCommand(command);

        log.info("Submitted modify order: {} new price: {} new qty: {}",
                orderId, newPrice, newQuantity);
        return true;
    }

    public Order queryOrder(long orderId, String symbol) {
        OrderBook orderBook = orderBookManager.getOrderBook(symbol);
        return orderBook.getOrder(orderId);
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBookManager.getOrderBook(symbol);
    }

    public boolean tryPlaceOrder(String symbol, long userId, OrderSide side, OrderType orderType,
                               BigDecimal price, BigDecimal quantity) {
        long orderId = orderIdGenerator.getAndIncrement();

        // Try to freeze funds first - 先尝试冻结资金
        try {
            freezeOrderFunds(symbol, userId, side, orderType, price, quantity);
        } catch (Exception e) {
            log.warn("Failed to freeze funds for order: {}", e.getMessage());
            return false;
        }

        Command command = Command.placeOrder(orderId, symbol, userId, side, orderType, price, quantity);
        boolean success = eventProducer.tryPublishCommand(command);

        if (success) {
            log.info("Successfully submitted place order: {} {} {} @ {} qty: {}",
                    orderId, symbol, side, price, quantity);
        } else {
            // If command publishing fails, unfreeze the funds - 如果命令发布失败，解冻资金
            unfreezeOrderFunds(symbol, userId, side, orderType, price, quantity);
            log.warn("Failed to submit place order - ring buffer full, funds unfrozen");
        }

        return success;
    }

    public void validateOrder(String symbol, long userId, OrderSide side, OrderType orderType,
                            BigDecimal price, BigDecimal quantity) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        // Validate symbol is supported - 验证支持的交易对
        if (!tradingPairsConfig.isValidSymbol(symbol)) {
            throw new IllegalArgumentException("Unsupported trading pair: " + symbol);
        }

        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }

        if (orderType == null) {
            throw new IllegalArgumentException("Order type cannot be null");
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // Price validation for non-market orders - 非 Market Order 的价格验证
        if (orderType != OrderType.MARKET) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price must be positive for non-market orders");
            }
        }

        // Validate against trading pair constraints - 根据交易对约束验证
        TradingPair tradingPair = tradingPairsConfig.getTradingPair(symbol);
        if (tradingPair != null) {
            validateTradingPairConstraints(tradingPair, price, quantity, orderType);
        }

        // Validate user balance - 验证用户余额
        validateUserBalance(symbol, userId, side, orderType, price, quantity);

        log.debug("Order validation passed for symbol: {}, side: {}, type: {}, price: {}, quantity: {}",
                symbol, side, orderType, price, quantity);
    }

    public List<String> getSupportedSymbols() {
        return tradingPairsConfig.getEnabledSymbols();
    }

    public TradingPair getTradingPairInfo(String symbol) {
        return tradingPairsConfig.getTradingPair(symbol);
    }

    public List<Order> getUserOrders(long userId, String symbol) {
        OrderBook orderBook = orderBookManager.getOrderBook(symbol);
        return orderBook.getUserOrders(userId);
    }

    public List<Order> getAllUserOrders(long userId) {
        List<Order> allOrders = new ArrayList<>();
        List<String> symbols = getSupportedSymbols();

        for (String symbol : symbols) {
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);
            List<Order> userOrders = orderBook.getUserOrders(userId);
            allOrders.addAll(userOrders);
        }

        return allOrders;
    }

    public List<Order> getUserCurrentOrders(long userId, String symbol) {
        OrderBook orderBook = orderBookManager.getOrderBook(symbol);
        return orderBook.getUserOrders(userId).stream()
                .filter(order -> order.getStatus() == OrderStatus.NEW ||
                               order.getStatus() == OrderStatus.PARTIALLY_FILLED)
                .toList();
    }

    public List<Order> getAllUserCurrentOrders(long userId) {
        List<Order> currentOrders = new ArrayList<>();
        List<String> symbols = getSupportedSymbols();

        for (String symbol : symbols) {
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);
            List<Order> userOrders = orderBook.getUserOrders(userId).stream()
                    .filter(order -> order.getStatus() == OrderStatus.NEW ||
                                   order.getStatus() == OrderStatus.PARTIALLY_FILLED)
                    .toList();
            currentOrders.addAll(userOrders);
        }

        return currentOrders;
    }

    public List<Order> getUserHistoryOrders(long userId, String symbol) {
        OrderBook orderBook = orderBookManager.getOrderBook(symbol);
        return orderBook.getUserHistoricalOrders(userId);
    }

    public List<Order> getAllUserHistoryOrders(long userId) {
        List<Order> historyOrders = new ArrayList<>();
        List<String> symbols = getSupportedSymbols();

        for (String symbol : symbols) {
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);
            List<Order> userHistoricalOrders = orderBook.getUserHistoricalOrders(userId);
            historyOrders.addAll(userHistoricalOrders);
        }

        return historyOrders;
    }

    private void validateTradingPairConstraints(TradingPair tradingPair,
                                              BigDecimal price, BigDecimal quantity, OrderType orderType) {
        // Price constraints - 价格约束
        if (orderType != OrderType.MARKET && price != null) {
            if (price.compareTo(tradingPair.getMinPrice()) < 0) {
                throw new IllegalArgumentException(
                        String.format("Price %s is below minimum %s for %s",
                                price, tradingPair.getMinPrice(), tradingPair.getSymbol()));
            }
            if (price.compareTo(tradingPair.getMaxPrice()) > 0) {
                throw new IllegalArgumentException(
                        String.format("Price %s is above maximum %s for %s",
                                price, tradingPair.getMaxPrice(), tradingPair.getSymbol()));
            }
        }

        // Quantity constraints - 数量约束
        if (quantity.compareTo(tradingPair.getMinQuantity()) < 0) {
            throw new IllegalArgumentException(
                    String.format("Quantity %s is below minimum %s for %s",
                            quantity, tradingPair.getMinQuantity(), tradingPair.getSymbol()));
        }
        if (quantity.compareTo(tradingPair.getMaxQuantity()) > 0) {
            throw new IllegalArgumentException(
                    String.format("Quantity %s is above maximum %s for %s",
                            quantity, tradingPair.getMaxQuantity(), tradingPair.getSymbol()));
        }
    }

    private void validateUserBalance(String symbol, long userId, OrderSide side, OrderType orderType,
                                   BigDecimal price, BigDecimal quantity) {
        // For buy orders, check if user has enough quote currency (e.g., USDT for BTCUSDT) - 对于Buy Order，检查用户是否有足够的计价货币 (例如，BTCUSDT中的USDT)
        if (side == OrderSide.BUY) {
            String quoteCurrency = currencyUtils.extractQuoteCurrency(symbol);
            BigDecimal requiredAmount;

            if (orderType == OrderType.MARKET) {
                // For market orders, we need to estimate the cost - 对于Market Order，需要估算成本
                // For simplicity, we'll use a high estimate - 为简化，使用高估算
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                BigDecimal estimatedPrice = orderBook.getBestSellPrice();
                if (estimatedPrice == null) {
                    estimatedPrice = price != null ? price : BigDecimal.valueOf(50000); // fallback - 退补值
                }
                requiredAmount = estimatedPrice.multiply(quantity);
            } else {
                requiredAmount = price.multiply(quantity);
            }

            if (!accountService.hasEnoughBalance(userId, quoteCurrency, requiredAmount)) {
                throw new IllegalArgumentException(
                    String.format("Insufficient balance. Required: %s %s, Available: %s %s",
                        requiredAmount, quoteCurrency,
                        accountService.getAvailableBalance(userId, quoteCurrency), quoteCurrency));
            }
        }
        // For sell orders, check if user has enough base currency (e.g., BTC for BTCUSDT) - 对于Sell Order，检查用户是否有足够的基础货币 (例如，BTCUSDT中的BTC)
        else if (side == OrderSide.SELL) {
            String baseCurrency = currencyUtils.extractBaseCurrency(symbol);

            if (!accountService.hasEnoughBalance(userId, baseCurrency, quantity)) {
                throw new IllegalArgumentException(
                    String.format("Insufficient balance. Required: %s %s, Available: %s %s",
                        quantity, baseCurrency,
                        accountService.getAvailableBalance(userId, baseCurrency), baseCurrency));
            }
        }
    }


    private void freezeOrderFunds(String symbol, long userId, OrderSide side, OrderType orderType,
                                BigDecimal price, BigDecimal quantity) {
        if (side == OrderSide.BUY) {
            String quoteCurrency = currencyUtils.extractQuoteCurrency(symbol);
            BigDecimal requiredAmount;

            if (orderType == OrderType.MARKET) {
                // For market orders, estimate the cost
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                BigDecimal estimatedPrice = orderBook.getBestSellPrice();
                if (estimatedPrice == null) {
                    estimatedPrice = price != null ? price : BigDecimal.valueOf(50000); // fallback - 退补值
                }
                requiredAmount = estimatedPrice.multiply(quantity);
            } else {
                requiredAmount = price.multiply(quantity);
            }

            boolean success = accountService.freezeBalance(userId, quoteCurrency, requiredAmount);
            if (!success) {
                throw new IllegalArgumentException(
                    String.format("Failed to freeze balance for buy order. Required: %s %s",
                        requiredAmount, quoteCurrency));
            }

            log.info("Frozen balance for buy order: userId={}, currency={}, amount={}",
                userId, quoteCurrency, requiredAmount);
        }
        else if (side == OrderSide.SELL) {
            String baseCurrency = currencyUtils.extractBaseCurrency(symbol);

            boolean success = accountService.freezeBalance(userId, baseCurrency, quantity);
            if (!success) {
                throw new IllegalArgumentException(
                    String.format("Failed to freeze balance for sell order. Required: %s %s",
                        quantity, baseCurrency));
            }

            log.info("Frozen balance for sell order: userId={}, currency={}, amount={}",
                userId, baseCurrency, quantity);
        }
    }

    private void unfreezeOrderFunds(String symbol, long userId, OrderSide side, OrderType orderType,
                                  BigDecimal price, BigDecimal quantity) {
        if (side == OrderSide.BUY) {
            String quoteCurrency = currencyUtils.extractQuoteCurrency(symbol);
            BigDecimal requiredAmount;

            if (orderType == OrderType.MARKET) {
                // For market orders, estimate the cost
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                BigDecimal estimatedPrice = orderBook.getBestSellPrice();
                if (estimatedPrice == null) {
                    estimatedPrice = price != null ? price : BigDecimal.valueOf(50000); // fallback - 退补值
                }
                requiredAmount = estimatedPrice.multiply(quantity);
            } else {
                requiredAmount = price.multiply(quantity);
            }

            accountService.unfreezeBalance(userId, quoteCurrency, requiredAmount);
            log.info("Unfrozen balance for buy order: userId={}, currency={}, amount={}",
                userId, quoteCurrency, requiredAmount);
        }
        else if (side == OrderSide.SELL) {
            String baseCurrency = currencyUtils.extractBaseCurrency(symbol);

            accountService.unfreezeBalance(userId, baseCurrency, quantity);
            log.info("Unfrozen balance for sell order: userId={}, currency={}, amount={}",
                userId, baseCurrency, quantity);
        }
    }

    /**
     * Get all active symbols that have order books - 获取所有有OrderBook的活跃交易对
     */
    public java.util.Set<String> getAllActiveSymbols() {
        java.util.Set<String> activeSymbols = new java.util.HashSet<>();

        // Get symbols from trading pairs config - 从交易对配置中获取交易对
        List<String> supportedSymbols = getSupportedSymbols();

        for (String symbol : supportedSymbols) {
            try {
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                // Consider symbol active if it has an order book - 如果有OrderBook则认为交易对活跃
                if (orderBook != null) {
                    activeSymbols.add(symbol);
                }
            } catch (Exception e) {
                log.debug("Could not get order book for symbol {}: {}", symbol, e.getMessage());
            }
        }

        return activeSymbols;
    }

}