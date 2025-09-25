package com.thunder.matchenginex.disruptor;

import com.lmax.disruptor.EventHandler;
import com.thunder.matchenginex.websocket.OrderWebSocketController;
import com.thunder.matchenginex.websocket.OrderBookWebSocketController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket推送事件处理器
 * WebSocket Push Event Handler for Disruptor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPushEventHandler implements EventHandler<WebSocketPushEvent> {

    private final OrderWebSocketController orderWebSocketController;
    private final OrderBookWebSocketController orderBookWebSocketController;

    @Override
    public void onEvent(WebSocketPushEvent event, long sequence, boolean endOfBatch) throws Exception {
        try {
            switch (event.getEventType()) {
                case ORDER_UPDATE:
                    handleOrderUpdate(event);
                    break;
                case CURRENT_ORDERS_UPDATE:
                    handleCurrentOrdersUpdate(event);
                    break;
                case TRADE_UPDATE:
                    handleTradeUpdate(event);
                    break;
                case ORDER_BOOK_UPDATE:
                    handleOrderBookUpdate(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", event.getEventType());
            }

            // 统计处理延迟
            long processingDelay = System.currentTimeMillis() - event.getTimestamp();
            if (processingDelay > 100) { // 超过100ms记录警告
                log.warn("🐌 WebSocket push processing delayed: {}ms for event type: {}, sequence: {}",
                    processingDelay, event.getEventType(), sequence);
            }

            // 批次结束时的额外处理
            if (endOfBatch) {
                log.debug("📦 Batch completed at sequence: {}", sequence);
            }

        } catch (Exception e) {
            log.error("❌ Error processing WebSocket push event: type={}, sequence={}, error={}",
                event.getEventType(), sequence, e.getMessage(), e);
        }
    }

    private void handleOrderUpdate(WebSocketPushEvent event) {
        if (event.getOrder() != null && orderWebSocketController != null) {
            orderWebSocketController.pushOrderUpdateToRelevantUsers(event.getOrder());
            log.debug("✅ Processed order update via Disruptor: orderId={}, userId={}",
                event.getOrder().getOrderId(), event.getUserId());
        }
    }

    private void handleCurrentOrdersUpdate(WebSocketPushEvent event) {
        if (event.getUserId() != null && orderWebSocketController != null) {
            orderWebSocketController.pushCurrentOrdersUpdate(event.getUserId());
            log.debug("✅ Processed current orders update via Disruptor: userId={}",
                event.getUserId());
        }
    }

    private void handleTradeUpdate(WebSocketPushEvent event) {
        if (event.getTrade() != null && orderWebSocketController != null) {
            // 推送给交易双方用户
            orderWebSocketController.pushCurrentOrdersUpdate(event.getTrade().getBuyUserId());
            orderWebSocketController.pushCurrentOrdersUpdate(event.getTrade().getSellUserId());
            log.debug("✅ Processed trade update via Disruptor: tradeId={}, buyUser={}, sellUser={}",
                event.getTrade().getTradeId(), event.getTrade().getBuyUserId(), event.getTrade().getSellUserId());
        }
    }

    private void handleOrderBookUpdate(WebSocketPushEvent event) {
        if (event.getSymbol() != null && orderBookWebSocketController != null) {
            orderBookWebSocketController.pushImmediateUpdate(event.getSymbol());
            log.debug("✅ Processed order book update via Disruptor: symbol={}",
                event.getSymbol());
        }
    }
}