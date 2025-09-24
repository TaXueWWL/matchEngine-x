package com.thunder.matchenginex.config;

import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;

@Data
public class TradingPair {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal priceStep;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private BigDecimal quantityStep;

    @Getter
    private boolean enabled;
}