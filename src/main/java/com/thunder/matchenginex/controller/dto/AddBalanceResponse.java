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
public class AddBalanceResponse {
    private boolean success;
    private String message;
    private BigDecimal newBalance;
}