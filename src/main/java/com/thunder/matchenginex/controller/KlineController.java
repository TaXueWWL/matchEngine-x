package com.thunder.matchenginex.controller;

import com.thunder.matchenginex.model.Kline;
import com.thunder.matchenginex.service.KlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/kline")
@RequiredArgsConstructor
public class KlineController {

    private final KlineService klineService;

    /**
     * Get K-line data for specific symbol and timeframe - 获取特定symbol和时间框架的K线数据
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<List<Kline>> getKlineData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1m") String timeframe,
            @RequestParam(defaultValue = "100") int limit) {

        log.debug("Getting K-line data for symbol: {}, timeframe: {}, limit: {}", symbol, timeframe, limit);

        List<Kline> klines = klineService.getKlineData(symbol, timeframe, limit);
        log.info("K-line data for symbol: {}, klines: {}", symbol, klines);
        return ResponseEntity.ok(klines);
    }

    /**
     * Get latest K-line for symbol and timeframe - 获取symbol和时间框架的最新K线
     */
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<Kline> getLatestKline(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1m") String timeframe) {

        log.debug("Getting latest K-line for symbol: {}, timeframe: {}", symbol, timeframe);

        Kline kline = klineService.getLatestKline(symbol, timeframe);
        if (kline == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(kline);
    }

    /**
     * Get supported timeframes - 获取支持的时间框架
     */
    @GetMapping("/timeframes")
    public ResponseEntity<Set<String>> getSupportedTimeframes() {
        Set<String> timeframes = klineService.getSupportedTimeframes();
        return ResponseEntity.ok(timeframes);
    }
}