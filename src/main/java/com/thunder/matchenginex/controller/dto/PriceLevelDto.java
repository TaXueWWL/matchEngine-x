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
public class PriceLevelDto {
    private BigDecimal price;
    private BigDecimal totalQuantity;
    private Integer orderCount;
}