package com.thunder.matchenginex.model;

import com.thunder.matchenginex.util.ObjectPool;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Trade implements ObjectPool.Resetable {
    private long tradeId;
    private String symbol;
    private long buyOrderId;
    private long sellOrderId;
    private long buyUserId;
    private long sellUserId;
    private BigDecimal price;
    private BigDecimal quantity;
    private long timestamp;

    @Override
    public void reset() {
        this.tradeId = 0;
        this.symbol = null;
        this.buyOrderId = 0;
        this.sellOrderId = 0;
        this.buyUserId = 0;
        this.sellUserId = 0;
        this.price = null;
        this.quantity = null;
        this.timestamp = 0;
    }
}