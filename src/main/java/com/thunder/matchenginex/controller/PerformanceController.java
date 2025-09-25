package com.thunder.matchenginex.controller;

import com.thunder.matchenginex.disruptor.WebSocketPushDisruptorService;
import com.thunder.matchenginex.engine.MatchingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能监控控制器
 * Performance monitoring controller
 */
@Slf4j
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final MatchingEngine matchingEngine;
    private final WebSocketPushDisruptorService disruptorService;

    /**
     * 获取WebSocket推送性能统计信息
     * Get WebSocket push performance statistics
     */
    @GetMapping("/websocket-push")
    public ResponseEntity<Map<String, Object>> getWebSocketPushStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Disruptor统计信息
            if (disruptorService != null) {
                stats.put("disruptor", disruptorService.getDisruptorStats());
            } else {
                stats.put("disruptor", "Service not available");
            }

            // 整体性能统计
            if (matchingEngine != null) {
                stats.put("overall", matchingEngine.getPerformanceStats());
            } else {
                stats.put("overall", "MatchingEngine not available");
            }

            stats.put("timestamp", System.currentTimeMillis());
            stats.put("status", "healthy");

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting WebSocket push statistics", e);

            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", e.getMessage());
            errorStats.put("status", "error");
            errorStats.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.internalServerError().body(errorStats);
        }
    }

    /**
     * 获取详细的Disruptor性能指标
     * Get detailed Disruptor performance metrics
     */
    @GetMapping("/disruptor")
    public ResponseEntity<Map<String, Object>> getDisruptorMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            if (disruptorService != null) {
                metrics.put("disruptorStats", disruptorService.getDisruptorStats());
                metrics.put("ringBufferSize", 8192); // From configuration
                metrics.put("strategy", "BlockingWaitStrategy");
                metrics.put("producerType", "MULTI");
                metrics.put("status", "running");
            } else {
                metrics.put("status", "not_available");
                metrics.put("error", "Disruptor service not initialized");
            }

            metrics.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            log.error("Error getting Disruptor metrics", e);

            Map<String, Object> errorMetrics = new HashMap<>();
            errorMetrics.put("error", e.getMessage());
            errorMetrics.put("status", "error");
            errorMetrics.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.internalServerError().body(errorMetrics);
        }
    }

    /**
     * 获取系统健康状态检查
     * Get system health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // 检查Disruptor服务状态
            boolean disruptorHealthy = disruptorService != null;
            health.put("disruptor", disruptorHealthy ? "healthy" : "unhealthy");

            // 检查MatchingEngine状态
            boolean matchingEngineHealthy = matchingEngine != null;
            health.put("matchingEngine", matchingEngineHealthy ? "healthy" : "unhealthy");

            // 整体状态
            boolean overallHealthy = disruptorHealthy && matchingEngineHealthy;
            health.put("status", overallHealthy ? "healthy" : "degraded");

            health.put("timestamp", System.currentTimeMillis());
            health.put("uptime", System.currentTimeMillis()); // 简化版的运行时间

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Error performing health check", e);

            health.put("status", "error");
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.internalServerError().body(health);
        }
    }
}