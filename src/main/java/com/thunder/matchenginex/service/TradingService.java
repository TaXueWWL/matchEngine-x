package com.thunder.matchenginex.service;

import com.thunder.matchenginex.config.TradingPair;
import com.thunder.matchenginex.config.TradingPairsConfig;
import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.event.OrderEventProducer;
import com.thunder.matchenginex.model.Command;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.orderbook.OrderBookManager;
import com.thunder.matchenginex.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final AtomicLong orderIdGenerator = new AtomicLong(1);

    public long placeOrder(String symbol, long userId, OrderSide side, OrderType orderType,
                          BigDecimal price, BigDecimal quantity) {
        long orderId = orderIdGenerator.getAndIncrement();

        // Immediately freeze the required funds to prevent overselling
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

        // Try to freeze funds first
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
            // If command publishing fails, unfreeze the funds
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

        // Validate symbol is supported
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

        // Price validation for non-market orders
        if (orderType != OrderType.MARKET) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price must be positive for non-market orders");
            }
        }

        // Validate against trading pair constraints
        TradingPair tradingPair = tradingPairsConfig.getTradingPair(symbol);
        if (tradingPair != null) {
            validateTradingPairConstraints(tradingPair, price, quantity, orderType);
        }

        // Validate user balance
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

    private void validateTradingPairConstraints(TradingPair tradingPair,
                                              BigDecimal price, BigDecimal quantity, OrderType orderType) {
        // Price constraints
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

        // Quantity constraints
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
        // For buy orders, check if user has enough quote currency (e.g., USDT for BTCUSDT)
        if (side == OrderSide.BUY) {
            String quoteCurrency = extractQuoteCurrency(symbol);
            BigDecimal requiredAmount;

            if (orderType == OrderType.MARKET) {
                // For market orders, we need to estimate the cost
                // For simplicity, we'll use a high estimate
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                BigDecimal estimatedPrice = orderBook.getBestSellPrice();
                if (estimatedPrice == null) {
                    estimatedPrice = price != null ? price : BigDecimal.valueOf(50000); // fallback
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
        // For sell orders, check if user has enough base currency (e.g., BTC for BTCUSDT)
        else if (side == OrderSide.SELL) {
            String baseCurrency = extractBaseCurrency(symbol);

            if (!accountService.hasEnoughBalance(userId, baseCurrency, quantity)) {
                throw new IllegalArgumentException(
                    String.format("Insufficient balance. Required: %s %s, Available: %s %s",
                        quantity, baseCurrency,
                        accountService.getAvailableBalance(userId, baseCurrency), baseCurrency));
            }
        }
    }

    private String extractBaseCurrency(String symbol) {
        // For symbols like BTCUSDT, extract BTC
        if (symbol.endsWith("USDT")) {
            return symbol.substring(0, symbol.length() - 4);
        }
        // Add other quote currency patterns as needed
        return symbol.substring(0, 3); // fallback
    }

    private String extractQuoteCurrency(String symbol) {
        // For symbols like BTCUSDT, extract USDT
        if (symbol.endsWith("USDT")) {
            return "USDT";
        }
        // Add other quote currency patterns as needed
        return "USDT"; // fallback
    }

    private void freezeOrderFunds(String symbol, long userId, OrderSide side, OrderType orderType,
                                BigDecimal price, BigDecimal quantity) {
        if (side == OrderSide.BUY) {
            String quoteCurrency = extractQuoteCurrency(symbol);
            BigDecimal requiredAmount;

            if (orderType == OrderType.MARKET) {
                // For market orders, estimate the cost
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                BigDecimal estimatedPrice = orderBook.getBestSellPrice();
                if (estimatedPrice == null) {
                    estimatedPrice = price != null ? price : BigDecimal.valueOf(50000); // fallback
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
            String baseCurrency = extractBaseCurrency(symbol);

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
            String quoteCurrency = extractQuoteCurrency(symbol);
            BigDecimal requiredAmount;

            if (orderType == OrderType.MARKET) {
                // For market orders, estimate the cost
                OrderBook orderBook = orderBookManager.getOrderBook(symbol);
                BigDecimal estimatedPrice = orderBook.getBestSellPrice();
                if (estimatedPrice == null) {
                    estimatedPrice = price != null ? price : BigDecimal.valueOf(50000); // fallback
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
            String baseCurrency = extractBaseCurrency(symbol);

            accountService.unfreezeBalance(userId, baseCurrency, quantity);
            log.info("Unfrozen balance for sell order: userId={}, currency={}, amount={}",
                userId, baseCurrency, quantity);
        }
    }
}