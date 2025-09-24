package com.thunder.matchenginex.service;

import com.thunder.matchenginex.controller.dto.OrderBookResponse;
import com.thunder.matchenginex.model.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastOrderBookUpdate(String symbol, OrderBookResponse orderBook) {
        try {
            messagingTemplate.convertAndSend("/topic/orderbook/" + symbol, orderBook);
            log.info("Broadcasted order book update for symbol: {}", symbol);
        } catch (Exception e) {
            log.error("Error broadcasting order book update: {}", e.getMessage(), e);
        }
    }

    public void broadcastTrade(String symbol, Trade trade) {
        try {
            messagingTemplate.convertAndSend("/topic/trades/" + symbol, trade);
            log.info("Broadcasted trade for symbol: {}", symbol);
        } catch (Exception e) {
            log.error("Error broadcasting trade: {}", e.getMessage(), e);
        }
    }

    public void sendUserNotification(Long userId, Object notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                notification
            );
            log.info("Sent notification to user: {}", userId);
        } catch (Exception e) {
            log.error("Error sending user notification: {}", e.getMessage(), e);
        }
    }
}