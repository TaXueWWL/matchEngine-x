package com.thunder.matchenginex.util;

import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import org.springframework.stereotype.Component;

/**
 * Central manager for object pools to reduce GC pressure - 对象池的中央管理器，用于减少GC压力
 */
@Component
public class PoolManager {

    // Order pool for recycling order objects - 用于回收Order对象的对象池
    private final ObjectPool<Order> orderPool;

    // Trade pool for recycling trade objects - 用于回收Trade对象的对象池
    private final ObjectPool<Trade> tradePool;

    public PoolManager() {
        // Initialize pools with reasonable sizes - 使用合理的大小初始化对象池
        this.orderPool = new ObjectPool<>(
            () -> new Order(), // Empty constructor for pooled objects - 用于池化对象的空构造函数
            1000 // Max 1000 orders in pool - 池中最多1000个Order
        );

        this.tradePool = new ObjectPool<>(
            () -> new Trade(), // Empty constructor for pooled objects - 用于池化对象的空构造函数
            500 // Max 500 trades in pool - 池中最多500个Trade
        );
    }

    /**
     * Get an Order from the pool - 从对象池获取Order
     */
    public Order acquireOrder() {
        return orderPool.acquire();
    }

    /**
     * Return an Order to the pool - 将Order返回对象池
     */
    public void releaseOrder(Order order) {
        orderPool.release(order);
    }

    /**
     * Get a Trade from the pool - 从对象池获取Trade
     */
    public Trade acquireTrade() {
        return tradePool.acquire();
    }

    /**
     * Return a Trade to the pool - 将Trade返回对象池
     */
    public void releaseTrade(Trade trade) {
        tradePool.release(trade);
    }

    /**
     * Get pool statistics for monitoring - 获取用于监控的对象池统计信息
     */
    public String getPoolStats() {
        return String.format("OrderPool: %d, TradePool: %d",
                orderPool.size(), tradePool.size());
    }
}