package com.thunder.matchenginex.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Kline {
    private String symbol;
    private String timeframe; // 1m, 5m, 15m, 1h, 1d
    private long timestamp; // K线开始时间戳
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume; // 成交量
    private BigDecimal amount; // 成交额
    private int tradeCount; // 成交笔数

    // 创建新的K线
    public static Kline create(String symbol, String timeframe, long timestamp, BigDecimal price, BigDecimal volume) {
        return Kline.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .timestamp(timestamp)
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .volume(volume)
                .amount(price.multiply(volume))
                .tradeCount(1)
                .build();
    }

    // 更新K线数据
    public void update(BigDecimal price, BigDecimal volume) {
        this.close = price;
        if (price.compareTo(this.high) > 0) {
            this.high = price;
        }
        if (price.compareTo(this.low) < 0) {
            this.low = price;
        }
        this.volume = this.volume.add(volume);
        this.amount = this.amount.add(price.multiply(volume));
        this.tradeCount++;
    }
}