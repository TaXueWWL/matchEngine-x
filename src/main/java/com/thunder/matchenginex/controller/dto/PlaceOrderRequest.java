package com.thunder.matchenginex.controller.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaceOrderRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @NotBlank(message = "Side is required")
    private String side; // BUY, SELL

    @NotBlank(message = "Type is required")
    private String type; // LIMIT, MARKET, IOC, FOK, POST_ONLY

    private String price; // Optional for MARKET orders

    @NotBlank(message = "Quantity is required")
    private String quantity;
}