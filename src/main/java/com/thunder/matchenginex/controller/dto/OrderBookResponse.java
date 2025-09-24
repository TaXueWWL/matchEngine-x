package com.thunder.matchenginex.controller.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderBookResponse {
    private String symbol;
    private List<PriceLevelDto> buyLevels;
    private List<PriceLevelDto> sellLevels;
}