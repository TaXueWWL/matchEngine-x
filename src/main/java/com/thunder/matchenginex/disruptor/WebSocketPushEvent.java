package com.thunder.matchenginex.disruptor;

import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import lombok.Data;

/**
 * WebSocket推送事件 - 用于Disruptor的高性能事件处理
 * WebSocket Push Event - High-performance event processing for Disruptor
 */
@Data
public class WebSocketPushEvent {

    /**
     * 事件类型枚举
     */
    public enum EventType {
        ORDER_UPDATE,           // 单个订单更新
        CURRENT_ORDERS_UPDATE,  // 当前订单列表更新
        TRADE_UPDATE,          // 交易更新
        ORDER_BOOK_UPDATE      // 订单簿更新
    }

    private EventType eventType;

    // 订单相关数据
    private Order order;
    private Long userId;

    // 交易相关数据
    private Trade trade;

    // 订单簿相关数据
    private String symbol;

    // 事件时间戳
    private long timestamp;

    /**
     * 重置事件数据 - 用于对象复用
     * Reset event data - for object reuse
     */
    public void reset() {
        this.eventType = null;
        this.order = null;
        this.userId = null;
        this.trade = null;
        this.symbol = null;
        this.timestamp = 0;
    }

    /**
     * 设置订单更新事件
     */
    public void setOrderUpdateEvent(Order order) {
        reset();
        this.eventType = EventType.ORDER_UPDATE;
        this.order = order;
        this.userId = order.getUserId();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 设置当前订单更新事件
     */
    public void setCurrentOrdersUpdateEvent(Long userId) {
        reset();
        this.eventType = EventType.CURRENT_ORDERS_UPDATE;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 设置交易更新事件
     */
    public void setTradeUpdateEvent(Trade trade) {
        reset();
        this.eventType = EventType.TRADE_UPDATE;
        this.trade = trade;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 设置订单簿更新事件
     */
    public void setOrderBookUpdateEvent(String symbol) {
        reset();
        this.eventType = EventType.ORDER_BOOK_UPDATE;
        this.symbol = symbol;
        this.timestamp = System.currentTimeMillis();
    }
}