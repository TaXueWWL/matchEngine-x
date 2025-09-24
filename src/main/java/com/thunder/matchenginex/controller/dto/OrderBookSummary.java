package com.thunder.matchenginex.controller.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderBookSummary {
    private String symbol;
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal midPrice;
    private BigDecimal spread;
    private Integer totalOrders;
}