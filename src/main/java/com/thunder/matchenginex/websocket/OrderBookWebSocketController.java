package com.thunder.matchenginex.websocket;

import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.math.BigDecimal;

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

    // Track active symbols that have subscriptions
    private final Set<String> activeSymbols = new HashSet<>();

    /**
     * Periodically push order book updates to subscribed clients
     */
    @Scheduled(fixedRate = 100) // Push every 1 second
    public void pushOrderBookUpdates() {
        try {
            // Get all available symbols from trading service
            Set<String> allSymbols = tradingService.getAllActiveSymbols();

            // Add default symbols if none are found
            if (allSymbols.isEmpty()) {
                allSymbols = Set.of("BTCUSDT", "ETHUSDT", "ADAUSDT");
            }

            for (String symbol : allSymbols) {
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
                    log.error("Error pushing order book update for symbol {}", symbol, e);
                }
            }
        } catch (Exception e) {
            log.error("Error in pushOrderBookUpdates", e);
        }
    }

    /**
     * Push latest trade price updates
     */
    private void pushLatestPriceUpdate(String symbol, OrderBook orderBook) {
        try {
            BigDecimal currentLastPrice = getLatestTradePrice(orderBook);
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
     * Get the latest trade price from order book
     * This is a simplified implementation - in a real system, you'd track actual trades
     */
    private BigDecimal getLatestTradePrice(OrderBook orderBook) {
        // For now, use mid price between best bid and ask as approximation
        // In a real implementation, you'd track actual executed trades
        BigDecimal bestBid = orderBook.getBestBuyPrice();
        BigDecimal bestAsk = orderBook.getBestSellPrice();

        if (bestBid != null && bestAsk != null) {
            return bestBid.add(bestAsk).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        } else if (bestBid != null) {
            return bestBid;
        } else if (bestAsk != null) {
            return bestAsk;
        }

        return null;
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

            // Send immediate update
            OrderBook orderBook = tradingService.getOrderBook(symbol);
            if (orderBook != null) {
                OrderBookDto orderBookDto = convertToOrderBookDto(orderBook, symbol);
                messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBookDto);
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