package com.thunder.matchenginex.disruptor;

import com.lmax.disruptor.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket推送Disruptor异常处理器
 * Exception handler for WebSocket Push Disruptor
 */
@Slf4j
public class WebSocketPushExceptionHandler implements ExceptionHandler<WebSocketPushEvent> {

    @Override
    public void handleEventException(Throwable ex, long sequence, WebSocketPushEvent event) {
        log.error("❌ Exception processing WebSocket push event at sequence {}: eventType={}, error={}",
            sequence, event != null ? event.getEventType() : "null", ex.getMessage(), ex);

        // 这里可以添加额外的错误处理逻辑，比如：
        // 1. 记录到错误队列
        // 2. 发送告警
        // 3. 尝试重新处理
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        log.error("❌ Exception during Disruptor start", ex);
        throw new RuntimeException("Failed to start Disruptor", ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        log.error("❌ Exception during Disruptor shutdown", ex);
    }
}