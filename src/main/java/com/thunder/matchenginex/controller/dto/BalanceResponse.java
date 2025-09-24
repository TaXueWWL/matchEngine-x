package com.thunder.matchenginex.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {
    private Long userId;
    private String currency;
    private BigDecimal availableBalance;
    private BigDecimal totalBalance;
    private BigDecimal frozenBalance;
}