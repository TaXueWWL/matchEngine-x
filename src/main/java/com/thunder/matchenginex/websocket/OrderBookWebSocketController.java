package com.thunder.matchenginex.websocket;

import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrderBookWebSocketController {

    private final TradingService tradingService;
    private final SimpMessagingTemplate messagingTemplate;

    // Cache to track last sent order book to avoid unnecessary updates
    private final Map<String, String> lastOrderBookHash = new ConcurrentHashMap<>();

    // Cache to track last sent price to avoid unnecessary updates
    private final Map<String, BigDecimal> lastPrice = new ConcurrentHashMap<>();

    // Track actual last trade prices for each symbol
    private final Map<String, BigDecimal> lastTradePrice = new ConcurrentHashMap<>();

    // Track active symbols that have subscriptions
    private final Set<String> activeSymbols = new HashSet<>();

    // Individual schedulers for each symbol to prevent thread conflicts
    private final Map<String, ScheduledFuture<?>> symbolSchedulers = new ConcurrentHashMap<>();

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @PostConstruct
    public void initializeSchedulers() {
        log.info("OrderBook WebSocket controller initialized with task scheduler");
    }

    /**
     * Start individual scheduler for a symbol
     */
    private void startSymbolScheduler(String symbol) {
        if (symbolSchedulers.containsKey(symbol)) {
            return; // Already scheduled
        }

        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
            () -> pushUpdatesForSymbol(symbol),
            1000 // 1 second interval
        );

        symbolSchedulers.put(symbol, scheduledTask);
        log.info("Started scheduler for symbol: {}", symbol);
    }

    /**
     * Push order book and price updates for a specific symbol
     */
    private void pushUpdatesForSymbol(String symbol) {
        try {
            OrderBook orderBook = tradingService.getOrderBook(symbol);
            if (orderBook != null && orderBook.getTotalOrders() > 0) {
                // Create a simple hash to check if order book changed
                String currentHash = createOrderBookHash(orderBook);
                String lastHash = lastOrderBookHash.get(symbol);

                // Only send if order book changed
                if (!currentHash.equals(lastHash)) {
                    OrderBookDto orderBookDto = convertToOrderBookDto(orderBook, symbol);
                    messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBookDto);
                    lastOrderBookHash.put(symbol, currentHash);
                    log.debug("Pushed order book update for {}", symbol);
                }

                // Push latest trade price if available and changed
                pushLatestPriceUpdate(symbol, orderBook);
            }
        } catch (Exception e) {
            log.error("Error pushing updates for symbol {}", symbol, e);
        }
    }

    /**
     * Push latest trade price updates
     */
    private void pushLatestPriceUpdate(String symbol, OrderBook orderBook) {
        try {
            BigDecimal currentLastPrice = getLatestTradePrice(symbol, orderBook);
            if (currentLastPrice != null) {
                BigDecimal lastSentPrice = lastPrice.get(symbol);

                // Only send if price changed
                if (lastSentPrice == null || currentLastPrice.compareTo(lastSentPrice) != 0) {
                    LastPriceDto priceDto = LastPriceDto.builder()
                            .symbol(symbol)
                            .price(currentLastPrice)
                            .timestamp(System.currentTimeMillis())
                            .build();

                    messagingTemplate.convertAndSend("/topic/price/" + symbol, priceDto);
                    lastPrice.put(symbol, currentLastPrice);
                    log.debug("Pushed price update for {}: {}", symbol, currentLastPrice);
                }
            }
        } catch (Exception e) {
            log.error("Error pushing price update for {}", symbol, e);
        }
    }

    /**
     * Get the latest trade price based on correct market logic
     * For taker orders:
     * - Buy taker (price >= best ask): trade price = best ask price
     * - Sell taker (price <= best bid): trade price = best bid price
     *
     * Since we don't have actual trade execution info here, we use order book state
     * to determine the likely trade price
     */
    private BigDecimal getLatestTradePrice(String symbol, OrderBook orderBook) {
        // If we have a stored last trade price, use it as priority
        return lastTradePrice.get(symbol);
    }

    /**
     * Update last trade price when a trade occurs
     * This would be called from the trading engine when actual trades happen
     */
    public void updateLastTradePrice(String symbol, BigDecimal tradePrice) {
        lastTradePrice.put(symbol, tradePrice);
        log.debug("Updated last trade price for {}: {}", symbol, tradePrice);
    }

    /**
     * Handle manual order book refresh requests from clients
     */
    @MessageMapping("/orderbook/refresh")
    public void refreshOrderBook(String symbol) {
        try {
            log.info("Manual order book refresh requested for {}", symbol);
            OrderBook orderBook = tradingService.getOrderBook(symbol);
            if (orderBook != null) {
                OrderBookDto orderBookDto = convertToOrderBookDto(orderBook, symbol);
                messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBookDto);
                activeSymbols.add(symbol);
            }
        } catch (Exception e) {
            log.error("Error refreshing order book for {}", symbol, e);
        }
    }

    /**
     * Handle subscription to a specific symbol
     */
    @MessageMapping("/orderbook/subscribe")
    public void subscribeToSymbol(String symbol) {
        try {
            log.info("Client subscribed to order book for {}", symbol);
            activeSymbols.add(symbol);

            // Start individual scheduler for this symbol
            startSymbolScheduler(symbol);

            // Send immediate update
            OrderBook orderBook = tradingService.getOrderBook(symbol);
            if (orderBook != null) {
                OrderBookDto orderBookDto = convertToOrderBookDto(orderBook, symbol);
                messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBookDto);

                // Also send immediate price update
                pushLatestPriceUpdate(symbol, orderBook);
            }
        } catch (Exception e) {
            log.error("Error subscribing to order book for {}", symbol, e);
        }
    }

    private String createOrderBookHash(OrderBook orderBook) {
        // Simple hash based on best prices and total orders
        return String.format("%s_%s_%d_%d",
            orderBook.getBestBuyPrice(),
            orderBook.getBestSellPrice(),
            orderBook.getTotalOrders(),
            System.currentTimeMillis() / 10000); // Change every 10 seconds to ensure freshness
    }

    private OrderBookDto convertToOrderBookDto(OrderBook orderBook, String symbol) {
        return OrderBookDto.builder()
                .symbol(symbol)
                .buyLevels(orderBook.getBuyLevels(10).stream()
                    .map(level -> PriceLevelDto.builder()
                        .price(level.getPrice())
                        .totalQuantity(level.getTotalQuantity())
                        .orderCount(level.getOrderCount())
                        .build())
                    .toList())
                .sellLevels(orderBook.getSellLevels(10).stream()
                    .map(level -> PriceLevelDto.builder()
                        .price(level.getPrice())
                        .totalQuantity(level.getTotalQuantity())
                        .orderCount(level.getOrderCount())
                        .build())
                    .toList())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // DTOs for WebSocket messages
    @lombok.Builder
    @lombok.Data
    public static class OrderBookDto {
        private String symbol;
        private java.util.List<PriceLevelDto> buyLevels;
        private java.util.List<PriceLevelDto> sellLevels;
        private long timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class PriceLevelDto {
        private java.math.BigDecimal price;
        private java.math.BigDecimal totalQuantity;
        private int orderCount;
    }

    @lombok.Builder
    @lombok.Data
    public static class LastPriceDto {
        private String symbol;
        private java.math.BigDecimal price;
        private long timestamp;
    }
}