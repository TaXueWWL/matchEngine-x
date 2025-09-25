package com.thunder.matchenginex.service;

import com.thunder.matchenginex.model.Kline;
import com.thunder.matchenginex.model.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KlineService {

    // 存储各个时间框架的K线数据 - Map<Symbol, Map<Timeframe, Map<Timestamp, Kline>>>
    private final Map<String, Map<String, Map<Long, Kline>>> klineData = new ConcurrentHashMap<>();

    // 支持的时间框架（秒为单位）
    private static final Map<String, Long> TIMEFRAMES = Map.of(
            "1s", 1L,
            "5s", 5L,
            "10s", 10L,
            "30s", 30L,
            "1m", 60L,
            "5m", 300L,
            "15m", 900L,
            "1h", 3600L,
            "4h", 14400L,
            "1d", 86400L
    );

    /**
     * Process trade and update K-line data - 处理交易并更新K线数据
     */
    public void processTrade(Trade trade) {
        String symbol = trade.getSymbol();
        BigDecimal price = trade.getPrice();
        BigDecimal volume = trade.getQuantity();
        long tradeTime = trade.getTimestamp();

        // Initialize symbol data if not exists - 如果不存在则初始化symbol数据
        klineData.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());

        // Process each timeframe - 处理每个时间框架
        for (Map.Entry<String, Long> timeframe : TIMEFRAMES.entrySet()) {
            String tf = timeframe.getKey();
            long intervalSeconds = timeframe.getValue();

            updateKlineForTimeframe(symbol, tf, intervalSeconds, tradeTime, price, volume);
        }

        log.debug("Updated K-line data for trade: {} @ {} qty: {}", symbol, price, volume);
    }

    /**
     * Update K-line data for specific timeframe - 更新特定时间框架的K线数据
     */
    private void updateKlineForTimeframe(String symbol, String timeframe, long intervalSeconds,
                                       long tradeTime, BigDecimal price, BigDecimal volume) {

        // Calculate K-line timestamp (aligned to interval) - 计算K线时间戳（对齐到间隔）
        long klineTimestamp = alignToInterval(tradeTime, intervalSeconds);

        // Get or create timeframe map - 获取或创建时间框架map
        Map<Long, Kline> timeframeData = klineData.get(symbol).computeIfAbsent(timeframe, k -> new ConcurrentHashMap<>());

        // Get existing K-line or create new one - 获取现有K线或创建新的
        Kline kline = timeframeData.get(klineTimestamp);

        if (kline == null) {
            // Create new K-line - 创建新K线
            kline = Kline.create(symbol, timeframe, klineTimestamp, price, volume);
            timeframeData.put(klineTimestamp, kline);
        } else {
            // Update existing K-line - 更新现有K线
            kline.update(price, volume);
        }
    }

    /**
     * Align timestamp to interval boundary - 将时间戳对齐到间隔边界
     */
    private long alignToInterval(long timestamp, long intervalSeconds) {
        long timestampSeconds = timestamp / 1000;
        return (timestampSeconds / intervalSeconds) * intervalSeconds;
    }

    /**
     * Get K-line data for specific symbol and timeframe - 获取特定symbol和时间框架的K线数据
     */
    public List<Kline> getKlineData(String symbol, String timeframe, int limit) {
        Map<String, Map<Long, Kline>> symbolData = klineData.get(symbol);
        if (symbolData == null) {
            // 如果没有数据，生成最近的几个空K线周期作为初始化数据
            return generateInitialEmptyKlines(symbol, timeframe, Math.min(limit, 10));
        }

        Map<Long, Kline> timeframeData = symbolData.get(timeframe);
        if (timeframeData == null) {
            // 如果该时间框架没有数据，生成初始化空K线
            log.info("📊 No {} data found for {}, generating initial empty K-line periods", timeframe, symbol);
            return generateInitialEmptyKlines(symbol, timeframe, Math.min(limit, 10));
        }

        List<Kline> existingData = timeframeData.values().stream()
                .sorted(Comparator.comparingLong(Kline::getTimestamp))
                .skip(Math.max(0, timeframeData.size() - limit))
                .collect(Collectors.toList());

        // 如果现有数据少于要求，补充一些空K线
        if (existingData.size() < 5) {
            List<Kline> emptyKlines = generateInitialEmptyKlines(symbol, timeframe, 10 - existingData.size());
            List<Kline> combined = new ArrayList<>();
            combined.addAll(emptyKlines);
            combined.addAll(existingData);
            return combined;
        }

        return existingData;
    }

    /**
     * Generate initial empty K-line data for visualization - 生成用于可视化的初始空K线数据
     */
    private List<Kline> generateInitialEmptyKlines(String symbol, String timeframe, int count) {
        List<Kline> emptyKlines = new ArrayList<>();
        Long intervalSeconds = TIMEFRAMES.get(timeframe);
        if (intervalSeconds == null) {
            intervalSeconds = 60L; // 默认1分钟
        }

        long currentTime = System.currentTimeMillis();

        // Use a small default price instead of zero to avoid frontend chart issues
        // 使用小的默认价格而不是零，以避免前端图表问题
        BigDecimal defaultPrice = new BigDecimal("0.001");

        for (int i = count - 1; i >= 0; i--) {
            long klineTime = currentTime - (i * intervalSeconds * 1000);
            long alignedTimestamp = alignToInterval(klineTime, intervalSeconds);

            Kline emptyKline = Kline.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .timestamp(alignedTimestamp)
                .open(defaultPrice)
                .high(defaultPrice)
                .low(defaultPrice)
                .close(defaultPrice)
                .volume(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .tradeCount(0)
                .build();

            emptyKlines.add(emptyKline);
        }

//        log.debug("Generated {} initial empty K-lines for {} {} with default price {}",
//                count, symbol, timeframe, defaultPrice);
        return emptyKlines;
    }

    /**
     * Get latest K-line for symbol and timeframe - 获取symbol和时间框架的最新K线
     */
    public Kline getLatestKline(String symbol, String timeframe) {
        List<Kline> klines = getKlineData(symbol, timeframe, 1);
        return klines.isEmpty() ? null : klines.get(0);
    }

    /**
     * Get all supported timeframes - 获取所有支持的时间框架
     */
    public Set<String> getSupportedTimeframes() {
        return TIMEFRAMES.keySet();
    }

    /**
     * Clear old K-line data to prevent memory overflow - 清理旧的K线数据以防止内存溢出
     */
    public void clearOldData(String symbol, String timeframe, int keepCount) {
        Map<String, Map<Long, Kline>> symbolData = klineData.get(symbol);
        if (symbolData == null) return;

        Map<Long, Kline> timeframeData = symbolData.get(timeframe);
        if (timeframeData == null || timeframeData.size() <= keepCount) return;

        // Keep only the latest 'keepCount' K-lines - 只保留最新的'keepCount'条K线
        List<Long> timestamps = timeframeData.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (int i = 0; i < timestamps.size() - keepCount; i++) {
            timeframeData.remove(timestamps.get(i));
        }

        log.debug("Cleaned old K-line data for {} {}, kept {} records", symbol, timeframe, keepCount);
    }
}