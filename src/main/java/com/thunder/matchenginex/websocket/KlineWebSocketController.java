package com.thunder.matchenginex.websocket;

import com.thunder.matchenginex.model.Kline;
import com.thunder.matchenginex.service.KlineService;
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

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Controller
@RequiredArgsConstructor
public class KlineWebSocketController {

    private final KlineService klineService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TradingService tradingService;

    // Track active K-line subscriptions: Map<Symbol, Map<Timeframe, Set<SessionId>>> - 跟踪活跃的K线订阅
    private final Map<String, Map<String, Set<String>>> activeSubscriptions = new ConcurrentHashMap<>();

    // Cache last sent K-line data to avoid unnecessary updates - 缓存最后发送的K线数据以避免不必要的更新
    private final Map<String, Map<String, Kline>> lastSentKlines = new ConcurrentHashMap<>();

    // Individual schedulers for each symbol-timeframe combination - 每个symbol-时间框架组合的独立调度器
    private final Map<String, ScheduledFuture<?>> klineSchedulers = new ConcurrentHashMap<>();

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * Get supported timeframes for K-line data - 获取K线数据支持的时间框架
     */
    private List<String> getSupportedTimeframes() {
        // This could be moved to configuration in the future
        return List.of("1s", "5s", "30s", "1m", "5m", "15m", "1h");
    }

    @PostConstruct
    public void initializeSchedulers() {
        log.info("🚀 Initializing automatic K-line schedulers for all supported symbols and timeframes");

        // Get supported symbols and timeframes dynamically
        List<String> supportedSymbols = tradingService.getSupportedSymbols();
        List<String> supportedTimeframes = getSupportedTimeframes();

        log.info("📋 Found {} supported symbols: {}", supportedSymbols.size(), supportedSymbols);
        log.info("📋 Found {} supported timeframes: {}", supportedTimeframes.size(), supportedTimeframes);

        // Start schedulers for all supported combinations automatically
        for (String symbol : supportedSymbols) {
            for (String timeframe : supportedTimeframes) {
                String schedulerKey = symbol + "_" + timeframe;
                startKlineScheduler(symbol, timeframe, schedulerKey);
                log.info("✅ Auto-started K-line scheduler for: {} {}", symbol, timeframe);
            }
        }

        log.info("🎯 Total {} K-line schedulers started automatically",
                supportedSymbols.size() * supportedTimeframes.size());
    }

    /**
     * Handle K-line subscription from clients - 处理来自客户端的K线订阅
     */
    @MessageMapping("/kline/subscribe")
    public void subscribeToKline(KlineSubscriptionMessage message) {
        try {
            String symbol = message.getSymbol();
            String timeframe = message.getTimeframe();
            String sessionId = message.getSessionId();

            log.info("Client subscribed to K-line: symbol={}, timeframe={}, session={}", symbol, timeframe, sessionId);

            // Add to active subscriptions - 添加到活跃订阅
            activeSubscriptions.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(timeframe, k -> new HashSet<>())
                    .add(sessionId);

            // Note: Schedulers are now auto-started for all supported symbols/timeframes
            // No need to manually start schedulers here
            String schedulerKey = symbol + "_" + timeframe;
            log.info("Client subscribed to K-line updates (scheduler already running): {}", schedulerKey);

            // Send immediate K-line data - 发送立即的K线数据
            sendInitialKlineData(symbol, timeframe);

        } catch (Exception e) {
            log.error("Error subscribing to K-line for {}", message, e);
        }
    }

    /**
     * Handle K-line unsubscription - 处理K线取消订阅
     */
    @MessageMapping("/kline/unsubscribe")
    public void unsubscribeFromKline(KlineSubscriptionMessage message) {
        try {
            String symbol = message.getSymbol();
            String timeframe = message.getTimeframe();
            String sessionId = message.getSessionId();

            log.info("Client unsubscribed from K-line: symbol={}, timeframe={}, session={}", symbol, timeframe, sessionId);

            // Remove from active subscriptions - 从活跃订阅中移除
            Map<String, Set<String>> symbolSubscriptions = activeSubscriptions.get(symbol);
            if (symbolSubscriptions != null) {
                Set<String> timeframeSubscriptions = symbolSubscriptions.get(timeframe);
                if (timeframeSubscriptions != null) {
                    timeframeSubscriptions.remove(sessionId);

                    // Note: Keep schedulers running even when no active subscriptions
                    // This ensures continuous data availability for new subscribers
                    if (timeframeSubscriptions.isEmpty()) {
                        String schedulerKey = symbol + "_" + timeframe;
                        log.info("No active subscribers for {} {}, but keeping scheduler running", symbol, timeframe);
                        symbolSubscriptions.remove(timeframe);
                    }
                }

                if (symbolSubscriptions.isEmpty()) {
                    activeSubscriptions.remove(symbol);
                }
            }

        } catch (Exception e) {
            log.error("Error unsubscribing from K-line for {}", message, e);
        }
    }

    /**
     * Start individual scheduler for symbol-timeframe combination - 为symbol-时间框架组合启动独立调度器
     */
    private void startKlineScheduler(String symbol, String timeframe, String schedulerKey) {
        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
            () -> pushKlineUpdate(symbol, timeframe),
            1000 // 1 second interval - 1秒间隔
        );

        klineSchedulers.put(schedulerKey, scheduledTask);
        log.info("Started K-line scheduler for: {} {}", symbol, timeframe);
    }

    /**
     * Stop scheduler for symbol-timeframe combination - 停止symbol-时间框架组合的调度器
     */
    private void stopKlineScheduler(String schedulerKey) {
        ScheduledFuture<?> scheduler = klineSchedulers.remove(schedulerKey);
        if (scheduler != null) {
            scheduler.cancel(false);
            log.info("Stopped K-line scheduler for: {}", schedulerKey);
        }
    }

    /**
     * Push K-line updates for specific symbol and timeframe - 推送特定symbol和时间框架的K线更新
     */
    private void pushKlineUpdate(String symbol, String timeframe) {
        try {
            // Get latest K-line data - 获取最新K线数据
            Kline latestKline = klineService.getLatestKline(symbol, timeframe);

            // If no actual K-line data, create empty placeholder K-line
            if (latestKline == null) {
                latestKline = createEmptyKline(symbol, timeframe);
//                log.debug("Created empty K-line placeholder for {} {}", symbol, timeframe);
            }

            // Check if data has changed - 检查数据是否已更改
            Kline lastSent = lastSentKlines.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                    .get(timeframe);

            // For empty K-lines (all prices are 0), always push if timestamp is different
            boolean isEmptyKline = latestKline.getOpen().compareTo(BigDecimal.ZERO) == 0 &&
                                   latestKline.getVolume().compareTo(BigDecimal.ZERO) == 0;

            if (lastSent != null && klineEquals(lastSent, latestKline)) {
                // Skip only if it's not an empty K-line or if timestamp is the same
                if (!isEmptyKline || lastSent.getTimestamp() == latestKline.getTimestamp()) {
                    return; // No change, skip update - 没有变化，跳过更新
                }
            }

            // Send update to all subscribers - 向所有订阅者发送更新
            String topic = "/topic/kline/" + symbol + "/" + timeframe;
//            log.info("🚀 PUSHING K-line update to {}: OHLC[{},{},{},{}] vol:{} ts:{}",
//                    topic, latestKline.getOpen(), latestKline.getHigh(),
//                    latestKline.getLow(), latestKline.getClose(),
//                    latestKline.getVolume(), latestKline.getTimestamp());

            try {
                messagingTemplate.convertAndSend(topic, latestKline);
            } catch (Exception e) {
                log.error("❌ Failed to send K-line message to {}: {}", topic, e.getMessage(), e);
            }

            // Update cache - 更新缓存
            lastSentKlines.get(symbol).put(timeframe, latestKline);

        } catch (Exception e) {
            log.error("Error pushing K-line update for {} {}", symbol, timeframe, e);
        }
    }

    /**
     * Create empty K-line placeholder when no actual data exists
     */
    private Kline createEmptyKline(String symbol, String timeframe) {
        long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds

        // Align timestamp to timeframe boundaries
        long alignedTimestamp = alignTimestampToTimeframe(currentTime, timeframe);

//        log.debug("Creating empty K-line for {} {} at timestamp {}", symbol, timeframe, alignedTimestamp);

        return Kline.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .timestamp(alignedTimestamp)
                .open(BigDecimal.ZERO)
                .high(BigDecimal.ZERO)
                .low(BigDecimal.ZERO)
                .close(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .tradeCount(0)
                .build();
    }

    /**
     * Align timestamp to timeframe boundaries
     */
    private long alignTimestampToTimeframe(long timestamp, String timeframe) {
        long intervalSeconds = switch (timeframe) {
            case "1s" -> 1;
            case "5s" -> 5;
            case "30s" -> 30;
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3600;
            case "1d" -> 86400;
            default -> 60; // Default to 1 minute
        };

        return (timestamp / intervalSeconds) * intervalSeconds;
    }

    /**
     * Send initial K-line data to new subscriber - 向新订阅者发送初始K线数据
     */
    private void sendInitialKlineData(String symbol, String timeframe) {
        try {
            List<Kline> klines = klineService.getKlineData(symbol, timeframe, 100);
            if (!klines.isEmpty()) {
                String topic = "/topic/kline/" + symbol + "/" + timeframe + "/initial";
                messagingTemplate.convertAndSend(topic, klines);
                log.debug("Sent initial K-line data for {} {}: {} records", symbol, timeframe, klines.size());
            }
        } catch (Exception e) {
            log.error("Error sending initial K-line data for {} {}", symbol, timeframe, e);
        }
    }

    /**
     * Compare two K-lines for equality - 比较两个K线是否相等
     */
    private boolean klineEquals(Kline k1, Kline k2) {
        return k1.getTimestamp() == k2.getTimestamp() &&
               k1.getOpen().compareTo(k2.getOpen()) == 0 &&
               k1.getHigh().compareTo(k2.getHigh()) == 0 &&
               k1.getLow().compareTo(k2.getLow()) == 0 &&
               k1.getClose().compareTo(k2.getClose()) == 0 &&
               k1.getVolume().compareTo(k2.getVolume()) == 0;
    }

    /**
     * K-line subscription message DTO - K线订阅消息DTO
     */
    @lombok.Data
    public static class KlineSubscriptionMessage {
        private String symbol;
        private String timeframe;
        private String sessionId;
    }
}