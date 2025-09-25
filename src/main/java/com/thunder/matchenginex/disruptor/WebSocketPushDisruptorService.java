package com.thunder.matchenginex.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * WebSocket推送Disruptor服务
 * High-performance WebSocket push service using Disruptor
 */
@Slf4j
@Service
public class WebSocketPushDisruptorService {

    // Disruptor配置
    private static final int RING_BUFFER_SIZE = 8192; // 必须是2的幂次

    private Disruptor<WebSocketPushEvent> disruptor;
    private RingBuffer<WebSocketPushEvent> ringBuffer;

    @Autowired
    private WebSocketPushEventHandler eventHandler;

    @PostConstruct
    public void start() {
        try {
            log.info("🚀 Starting WebSocket Push Disruptor Service...");

            // 创建Disruptor实例
            disruptor = new Disruptor<>(
                new WebSocketPushEventFactory(),
                RING_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI, // 多生产者模式 - 支持并发推送
                new BlockingWaitStrategy() // 阻塞等待策略 - 低延迟
            );

            // 设置事件处理器
            disruptor.handleEventsWith(eventHandler);

            // 设置异常处理器
            disruptor.setDefaultExceptionHandler(new WebSocketPushExceptionHandler());

            // 启动Disruptor
            disruptor.start();
            ringBuffer = disruptor.getRingBuffer();

            log.info("✅ WebSocket Push Disruptor Service started successfully with ring buffer size: {}", RING_BUFFER_SIZE);

        } catch (Exception e) {
            log.error("❌ Failed to start WebSocket Push Disruptor Service", e);
            throw new RuntimeException("Failed to initialize Disruptor", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            log.info("🛑 Shutting down WebSocket Push Disruptor Service...");
            try {
                disruptor.shutdown();
                log.info("✅ WebSocket Push Disruptor Service shutdown completed");
            } catch (Exception e) {
                log.error("❌ Error during Disruptor shutdown", e);
            }
        }
    }

    /**
     * 推送订单更新事件到Disruptor
     */
    public void publishOrderUpdate(Order order) {
        if (ringBuffer == null) {
            log.warn("⚠️ Disruptor not initialized, skipping order update push");
            return;
        }

        long sequence = ringBuffer.next();
        try {
            WebSocketPushEvent event = ringBuffer.get(sequence);
            event.setOrderUpdateEvent(order);
        } catch (Exception e) {
            log.error("❌ Failed to publish order update event", e);
        } finally {
            ringBuffer.publish(sequence);
        }

        log.debug("📤 Published order update to Disruptor: orderId={}, sequence={}",
            order.getOrderId(), sequence);
    }

    /**
     * 推送当前订单更新事件到Disruptor
     */
    public void publishCurrentOrdersUpdate(Long userId) {
        if (ringBuffer == null) {
            log.warn("⚠️ Disruptor not initialized, skipping current orders update push");
            return;
        }

        long sequence = ringBuffer.next();
        try {
            WebSocketPushEvent event = ringBuffer.get(sequence);
            event.setCurrentOrdersUpdateEvent(userId);
        } catch (Exception e) {
            log.error("❌ Failed to publish current orders update event", e);
        } finally {
            ringBuffer.publish(sequence);
        }

        log.debug("📤 Published current orders update to Disruptor: userId={}, sequence={}",
            userId, sequence);
    }

    /**
     * 推送交易更新事件到Disruptor
     */
    public void publishTradeUpdate(Trade trade) {
        if (ringBuffer == null) {
            log.warn("⚠️ Disruptor not initialized, skipping trade update push");
            return;
        }

        long sequence = ringBuffer.next();
        try {
            WebSocketPushEvent event = ringBuffer.get(sequence);
            event.setTradeUpdateEvent(trade);
        } catch (Exception e) {
            log.error("❌ Failed to publish trade update event", e);
        } finally {
            ringBuffer.publish(sequence);
        }

        log.debug("📤 Published trade update to Disruptor: tradeId={}, sequence={}",
            trade.getTradeId(), sequence);
    }

    /**
     * 推送订单簿更新事件到Disruptor
     */
    public void publishOrderBookUpdate(String symbol) {
        if (ringBuffer == null) {
            log.warn("⚠️ Disruptor not initialized, skipping order book update push");
            return;
        }

        long sequence = ringBuffer.next();
        try {
            WebSocketPushEvent event = ringBuffer.get(sequence);
            event.setOrderBookUpdateEvent(symbol);
        } catch (Exception e) {
            log.error("❌ Failed to publish order book update event", e);
        } finally {
            ringBuffer.publish(sequence);
        }

        log.debug("📤 Published order book update to Disruptor: symbol={}, sequence={}",
            symbol, sequence);
    }

    /**
     * 获取Disruptor状态统计
     */
    public String getDisruptorStats() {
        if (ringBuffer == null) {
            return "Disruptor not initialized";
        }

        long cursor = ringBuffer.getCursor();
        long remainingCapacity = ringBuffer.remainingCapacity();

        return String.format("Disruptor Stats - Cursor: %d, Remaining Capacity: %d, Buffer Size: %d",
            cursor, remainingCapacity, RING_BUFFER_SIZE);
    }
}