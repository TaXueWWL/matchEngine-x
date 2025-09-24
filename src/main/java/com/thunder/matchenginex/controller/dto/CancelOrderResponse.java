package com.thunder.matchenginex.controller.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelOrderResponse {
    private Boolean success;
    private String message;
}