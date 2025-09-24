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

    // Cache to track last sent order book to avoid unnecessary updates - 缓存以跟踪上次发送的OrderBook以避免不必要的更新
    private final Map<String, String> lastOrderBookHash = new ConcurrentHashMap<>();

    // Cache to track last sent price to avoid unnecessary updates - 缓存以跟踪上次发送的价格以避免不必要的更新
    private final Map<String, BigDecimal> lastPrice = new ConcurrentHashMap<>();

    // Track actual last trade prices for each symbol - 跟踪每个交易对的实际最后交易价格
    private final Map<String, BigDecimal> lastTradePrice = new ConcurrentHashMap<>();

    // Track active symbols that have subscriptions - 跟踪有订阅的活跃交易对
    private final Set<String> activeSymbols = new HashSet<>();

    // Individual schedulers for each symbol to prevent thread conflicts - 每个交易对的独立调度器以防止线程冲突
    private final Map<String, ScheduledFuture<?>> symbolSchedulers = new ConcurrentHashMap<>();

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @PostConstruct
    public void initializeSchedulers() {
        log.info("OrderBook WebSocket controller initialized with task scheduler");
    }

    /**
     * Start individual scheduler for a symbol - 为交易对启动独立调度器
     */
    private void startSymbolScheduler(String symbol) {
        if (symbolSchedulers.containsKey(symbol)) {
            return; // Already scheduled - 已经调度
        }

        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
            () -> pushUpdatesForSymbol(symbol),
            1000 // 1 second interval - 1秒间隔
        );

        symbolSchedulers.put(symbol, scheduledTask);
        log.info("Started scheduler for symbol: {}", symbol);
    }

    /**
     * Push order book and price updates for a specific symbol - 为特定交易对推送OrderBook和价格更新
     */
    private void pushUpdatesForSymbol(String symbol) {
        try {
            OrderBook orderBook = tradingService.getOrderBook(symbol);
            if (orderBook != null && orderBook.getTotalOrders() > 0) {
                // Create a simple hash to check if order book changed - 创建简单哈希检查OrderBook是否变化
                String currentHash = createOrderBookHash(orderBook);
                String lastHash = lastOrderBookHash.get(symbol);

                // Only send if order book changed - 仅在OrderBook变化时发送
                if (!currentHash.equals(lastHash)) {
                    OrderBookDto orderBookDto = convertToOrderBookDto(orderBook, symbol);
                    messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBookDto);
                    lastOrderBookHash.put(symbol, currentHash);
                    log.debug("Pushed order book update for {}", symbol);
                }

                // Push latest trade price if available and changed - 如果最新交易价格可用且发生变化则推送
                pushLatestPriceUpdate(symbol, orderBook);
            }
        } catch (Exception e) {
            log.error("Error pushing updates for symbol {}", symbol, e);
        }
    }

    /**
     * Push latest trade price updates - 推送最新交易价格更新
     */
    private void pushLatestPriceUpdate(String symbol, OrderBook orderBook) {
        try {
            BigDecimal currentLastPrice = getLatestTradePrice(symbol, orderBook);
            if (currentLastPrice != null) {
                BigDecimal lastSentPrice = lastPrice.get(symbol);

                // Only send if price changed - 仅在价格变化时发送
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
     * Get the latest trade price based on correct market logic - 根据正确的市场逻辑获取最新交易价格
     * For taker orders: - 对于Taker Order：
     * - Buy taker (price >= best ask): trade price = best ask price - Buy Taker (价格 >= 最佳卖一价)：交易价格 = 最佳卖一价
     * - Sell taker (price <= best bid): trade price = best bid price - Sell Taker (价格 <= 最佳买一价)：交易价格 = 最佳买一价
     *
     * Since we don't have actual trade execution info here, we use order book state - 由于这里没有实际的交易执行信息，我们使用OrderBook状态
     * to determine the likely trade price - 来确定可能的交易价格
     */
    private BigDecimal getLatestTradePrice(String symbol, OrderBook orderBook) {
        // If we have a stored last trade price, use it as priority - 如果有存储的最后交易价格，优先使用它
        return lastTradePrice.get(symbol);
    }

    /**
     * Update last trade price when a trade occurs - 当交易发生时更新最后交易价格
     * This would be called from the trading engine when actual trades happen - 当实际交易发生时，交易引擎会调用此方法
     */
    public void updateLastTradePrice(String symbol, BigDecimal tradePrice) {
        lastTradePrice.put(symbol, tradePrice);
        log.debug("Updated last trade price for {}: {}", symbol, tradePrice);
    }

    /**
     * Handle manual order book refresh requests from clients - 处理来自客户端的手动OrderBook刷新请求
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
     * Handle subscription to a specific symbol - 处理对特定交易对的订阅
     */
    @MessageMapping("/orderbook/subscribe")
    public void subscribeToSymbol(String symbol) {
        try {
            log.info("Client subscribed to order book for {}", symbol);
            activeSymbols.add(symbol);

            // Start individual scheduler for this symbol - 为此交易对启动独立调度器
            startSymbolScheduler(symbol);

            // Send immediate update - 发送立即更新
            OrderBook orderBook = tradingService.getOrderBook(symbol);
            if (orderBook != null) {
                OrderBookDto orderBookDto = convertToOrderBookDto(orderBook, symbol);
                messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBookDto);

                // Also send immediate price update - 同时发送立即价格更新
                pushLatestPriceUpdate(symbol, orderBook);
            }
        } catch (Exception e) {
            log.error("Error subscribing to order book for {}", symbol, e);
        }
    }

    private String createOrderBookHash(OrderBook orderBook) {
        // Simple hash based on best prices and total orders - 基于最佳价格和总订单数的简单哈希
        return String.format("%s_%s_%d_%d",
            orderBook.getBestBuyPrice(),
            orderBook.getBestSellPrice(),
            orderBook.getTotalOrders(),
            System.currentTimeMillis() / 10000); // Change every 10 seconds to ensure freshness - 每10秒改变一次以确保新鲜度
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

    // DTOs for WebSocket messages - WebSocket消息DTO
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