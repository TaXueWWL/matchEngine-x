package com.thunder.matchenginex.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddBalanceRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Amount is required")
    @DecimalMin(value = "0.000001", message = "Amount must be greater than 0")
    private String amount;
}