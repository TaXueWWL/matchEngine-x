package com.thunder.matchenginex.model;

import com.thunder.matchenginex.enums.CommandType;
import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Command {
    private CommandType commandType;
    private long orderId;
    private String symbol;
    private long userId;
    private OrderSide side;
    private OrderType orderType;
    private BigDecimal price;
    private BigDecimal quantity;
    private long timestamp;
    private long sequence;

    public static Command placeOrder(long orderId, String symbol, long userId,
                                   OrderSide side, OrderType orderType,
                                   BigDecimal price, BigDecimal quantity) {
        return Command.builder()
                .commandType(CommandType.PLACE_ORDER)
                .orderId(orderId)
                .symbol(symbol)
                .userId(userId)
                .side(side)
                .orderType(orderType)
                .price(price)
                .quantity(quantity)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static Command cancelOrder(long orderId, String symbol, long userId) {
        return Command.builder()
                .commandType(CommandType.CANCEL_ORDER)
                .orderId(orderId)
                .symbol(symbol)
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static Command modifyOrder(long orderId, String symbol, long userId,
                                    BigDecimal newPrice, BigDecimal newQuantity) {
        return Command.builder()
                .commandType(CommandType.MODIFY_ORDER)
                .orderId(orderId)
                .symbol(symbol)
                .userId(userId)
                .price(newPrice)
                .quantity(newQuantity)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}