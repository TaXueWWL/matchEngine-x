package com.thunder.matchenginex.util;

import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import org.springframework.stereotype.Component;

/**
 * Central manager for object pools to reduce GC pressure
 */
@Component
public class PoolManager {

    // Order pool for recycling order objects
    private final ObjectPool<Order> orderPool;

    // Trade pool for recycling trade objects
    private final ObjectPool<Trade> tradePool;

    public PoolManager() {
        // Initialize pools with reasonable sizes
        this.orderPool = new ObjectPool<>(
            () -> new Order(), // Empty constructor for pooled objects
            1000 // Max 1000 orders in pool
        );

        this.tradePool = new ObjectPool<>(
            () -> new Trade(), // Empty constructor for pooled objects
            500 // Max 500 trades in pool
        );
    }

    /**
     * Get an Order from the pool
     */
    public Order acquireOrder() {
        return orderPool.acquire();
    }

    /**
     * Return an Order to the pool
     */
    public void releaseOrder(Order order) {
        orderPool.release(order);
    }

    /**
     * Get a Trade from the pool
     */
    public Trade acquireTrade() {
        return tradePool.acquire();
    }

    /**
     * Return a Trade to the pool
     */
    public void releaseTrade(Trade trade) {
        tradePool.release(trade);
    }

    /**
     * Get pool statistics for monitoring
     */
    public String getPoolStats() {
        return String.format("OrderPool: %d, TradePool: %d",
                orderPool.size(), tradePool.size());
    }
}