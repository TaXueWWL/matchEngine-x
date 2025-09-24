package com.thunder.matchenginex.controller;

import com.thunder.matchenginex.config.TradingPair;
import com.thunder.matchenginex.config.TradingPairsConfig;
import com.thunder.matchenginex.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@RestController
@RequestMapping("/api/trading-pairs")
@RequiredArgsConstructor
public class TradingPairsController {

    private final TradingService tradingService;

    @GetMapping
    public ResponseEntity<List<String>> getSupportedSymbols() {
        try {
            List<String> symbols = tradingService.getSupportedSymbols();
            return ResponseEntity.ok(symbols);
        } catch (Exception e) {
            log.error("Error getting supported symbols: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<String>> getSupportedCurrencies() {
        try {
            List<String> symbols = tradingService.getSupportedSymbols();
            Set<String> currencies = new HashSet<>();

            // Extract unique currencies from trading pairs
            for (String symbol : symbols) {
                // For symbols like BTCUSDT, extract BTC and USDT
                if (symbol.endsWith("USDT")) {
                    currencies.add(symbol.substring(0, symbol.length() - 4)); // Base currency
                    currencies.add("USDT"); // Quote currency
                }
                // Add other patterns as needed
            }

            return ResponseEntity.ok(currencies.stream().sorted().toList());
        } catch (Exception e) {
            log.error("Error getting supported currencies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<TradingPair> getTradingPairInfo(@PathVariable String symbol) {
        try {
            TradingPair tradingPair = tradingService.getTradingPairInfo(symbol);
            if (tradingPair == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(tradingPair);
        } catch (Exception e) {
            log.error("Error getting trading pair info: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}