package com.thunder.matchenginex.model;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderStatus;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.util.ObjectPool;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order implements ObjectPool.Resetable {
    private long orderId;
    private String symbol;
    private long userId;
    private OrderSide side;
    private OrderType type;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal remainingQuantity;
    private OrderStatus status;
    private long timestamp;
    private long sequence;

    public Order(long orderId, String symbol, long userId, OrderSide side,
                 OrderType type, BigDecimal price, BigDecimal quantity) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.userId = userId;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = BigDecimal.ZERO;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.NEW;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public boolean isFullyFilled() {
        return remainingQuantity.compareTo(BigDecimal.ZERO) == 0;
    }

    public void fill(BigDecimal filledAmount) {
        this.filledQuantity = this.filledQuantity.add(filledAmount);
        this.remainingQuantity = this.quantity.subtract(this.filledQuantity);

        if (isFullyFilled()) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public boolean canMatch(Order other) {
        if (!this.symbol.equals(other.symbol)) return false;
        if (this.side == other.side) return false;

        if (this.side == OrderSide.BUY) {
            return this.price.compareTo(other.price) >= 0;
        } else {
            return this.price.compareTo(other.price) <= 0;
        }
    }

    @Override
    public void reset() {
        this.orderId = 0;
        this.symbol = null;
        this.userId = 0;
        this.side = null;
        this.type = null;
        this.price = null;
        this.quantity = null;
        this.filledQuantity = BigDecimal.ZERO;
        this.remainingQuantity = null;
        this.status = OrderStatus.NEW;
        this.timestamp = 0;
        this.sequence = 0;
    }
}