package com.thunder.matchenginex.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Trade {
    private long tradeId;
    private String symbol;
    private long buyOrderId;
    private long sellOrderId;
    private long buyUserId;
    private long sellUserId;
    private BigDecimal price;
    private BigDecimal quantity;
    private long timestamp;
}