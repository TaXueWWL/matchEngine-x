package com.thunder.matchenginex.websocket;

import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrderWebSocketController {

    private final TradingService tradingService;
    private final SimpMessagingTemplate messagingTemplate;

    // Track users who are subscribed to order updates - 跟踪订阅订单更新的用户
    private final Set<Long> subscribedUsers = ConcurrentHashMap.newKeySet();

    /**
     * Subscribe to order updates for a user - 订阅用户的订单更新
     */
    @MessageMapping("/orders/subscribe")
    public void subscribeToOrderUpdates(String userIdStr) {
        try {
            log.info("Received order subscription request with payload: {}", userIdStr);

            Long userId;
            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.error("Invalid userId format for order subscription: {}", userIdStr, e);
                return;
            }

            if (userId <= 0) {
                log.warn("Invalid userId for order subscription: {}", userId);
                return;
            }

            subscribedUsers.add(userId);
            log.info("✅ User {} successfully subscribed to order updates. Total subscribers: {}, All subscribers: {}",
                userId, subscribedUsers.size(), subscribedUsers);

            // Send immediate current orders update - 发送立即的当前订单更新
            pushCurrentOrdersUpdate(userId);

        } catch (Exception e) {
            log.error("Error subscribing user to order updates with payload {}: {}", userIdStr, e.getMessage(), e);
        }
    }

    /**
     * Unsubscribe from order updates - 取消订阅订单更新
     */
    @MessageMapping("/orders/unsubscribe")
    public void unsubscribeFromOrderUpdates(String userIdStr) {
        try {
            log.info("Received order unsubscription request with payload: {}", userIdStr);

            Long userId;
            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.error("Invalid userId format for order unsubscription: {}", userIdStr, e);
                return;
            }

            if (userId <= 0) {
                log.warn("Invalid userId for order unsubscription: {}", userId);
                return;
            }

            subscribedUsers.remove(userId);
            log.info("✅ User {} unsubscribed from order updates. Total subscribers: {}, Remaining subscribers: {}",
                userId, subscribedUsers.size(), subscribedUsers);

        } catch (Exception e) {
            log.error("Error unsubscribing user from order updates with payload {}: {}", userIdStr, e.getMessage(), e);
        }
    }

    /**
     * Push order update to specific user - 推送订单更新给特定用户
     */
    public void pushOrderUpdate(Long userId, Order order) {
        if (!subscribedUsers.contains(userId)) {
            return; // User not subscribed - 用户未订阅
        }

        log.warn("⚠️ User {}  Current subscribers: {}", order.getOrderId(), subscribedUsers.size());

        try {
            OrderUpdateDto updateDto = convertToOrderUpdateDto(order);
            messagingTemplate.convertAndSend("/user/" + userId + "/queue/orders", updateDto);

            log.info("✅ Pushed order update to user {}: orderId={}, status={}, filledQty={}",
                userId, order.getOrderId(), order.getStatus(), order.getFilledQuantity());

        } catch (Exception e) {
            log.error("Error pushing order update to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Push current orders update to user - 推送当前订单更新给用户
     */
    public void pushCurrentOrdersUpdate(Long userId) {
        if (!subscribedUsers.contains(userId)) {
            return; // User not subscribed - 用户未订阅
        }

        log.warn("⚠️  Current subscribers: {}", subscribedUsers);

        try {
            // Get current orders for all symbols for this user - 获取该用户所有交易对的当前订单
            List<Order> currentOrders = tradingService.getAllUserCurrentOrders(userId);

            CurrentOrdersUpdateDto updateDto = CurrentOrdersUpdateDto.builder()
                .userId(userId)
                .orders(currentOrders.stream()
                    .map(this::convertToOrderUpdateDto)
                    .toList())
                .timestamp(System.currentTimeMillis())
                .build();

            messagingTemplate.convertAndSend("/user/" + userId + "/queue/current-orders", updateDto);

            log.info("✅ Pushed current orders update to user {}: {} orders", userId, currentOrders.size());

        } catch (Exception e) {
            log.error("Error pushing current orders update to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Push order update to all subscribed users who have this order - 推送订单更新给所有相关的订阅用户
     */
    public void pushOrderUpdateToRelevantUsers(Order order) {
        if (order == null) {
            return;
        }

        // Push to the order owner - 推送给订单所有者
        pushOrderUpdate(order.getUserId(), order);

        // Also refresh current orders for the user - 同时刷新用户的当前订单列表
        pushCurrentOrdersUpdate(order.getUserId());
    }

    /**
     * Convert Order to DTO for WebSocket transmission - 转换Order为WebSocket传输的DTO
     */
    private OrderUpdateDto convertToOrderUpdateDto(Order order) {
        return OrderUpdateDto.builder()
            .orderId(order.getOrderId())
            .symbol(order.getSymbol())
            .userId(order.getUserId())
            .side(order.getSide().name())
            .type(order.getType().name())
            .status(order.getStatus().name())
            .price(order.getPrice())
            .quantity(order.getQuantity())
            .filledQuantity(order.getFilledQuantity())
            .remainingQuantity(order.getRemainingQuantity())
            .timestamp(order.getTimestamp())
            .updateTime(System.currentTimeMillis())
            .build();
    }

    /**
     * Get subscribed users count - 获取订阅用户数量
     */
    public int getSubscribedUsersCount() {
        return subscribedUsers.size();
    }

    /**
     * Get all subscribed user IDs for debugging - 获取所有订阅用户ID用于调试
     */
    public Set<Long> getSubscribedUsers() {
        return Set.copyOf(subscribedUsers);
    }

    /**
     * Check if a specific user is subscribed - 检查特定用户是否已订阅
     */
    public boolean isUserSubscribed(Long userId) {
        return subscribedUsers.contains(userId);
    }

    // DTOs for WebSocket messages - WebSocket消息的DTO类

    @lombok.Builder
    @lombok.Data
    public static class OrderUpdateDto {
        private Long orderId;
        private String symbol;
        private Long userId;
        private String side;
        private String type;
        private String status;
        private BigDecimal price;
        private BigDecimal quantity;
        private BigDecimal filledQuantity;
        private BigDecimal remainingQuantity;
        private Long timestamp;
        private Long updateTime;
    }

    @lombok.Builder
    @lombok.Data
    public static class CurrentOrdersUpdateDto {
        private Long userId;
        private java.util.List<OrderUpdateDto> orders;
        private Long timestamp;
    }
}