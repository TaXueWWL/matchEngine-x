package com.thunder.matchenginex.orderbook;

import com.thunder.matchenginex.model.Order;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Queue;

@Data
public class PriceLevel {
    private BigDecimal price;
    private BigDecimal totalQuantity;
    private Queue<Order> orders;

    public PriceLevel(BigDecimal price) {
        this.price = price;
        this.totalQuantity = BigDecimal.ZERO;
        // ArrayDeque provides better cache locality and performance than LinkedList - ArrayDeque比LinkedList提供更好的缓存局部性和性能
        this.orders = new ArrayDeque<>();
    }

    public void addOrder(Order order) {
        orders.offer(order);
        totalQuantity = totalQuantity.add(order.getRemainingQuantity());
    }

    public void removeOrder(Order order) {
        if (orders.remove(order)) {
            totalQuantity = totalQuantity.subtract(order.getRemainingQuantity());
        }
    }

    public Order peekFirstOrder() {
        return orders.peek();
    }

    public Order pollFirstOrder() {
        Order order = orders.poll();
        if (order != null) {
            totalQuantity = totalQuantity.subtract(order.getRemainingQuantity());
        }
        return order;
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public int getOrderCount() {
        return orders.size();
    }

    public void updateQuantity(Order order, BigDecimal oldQuantity, BigDecimal newQuantity) {
        totalQuantity = totalQuantity.subtract(oldQuantity).add(newQuantity);
    }
}