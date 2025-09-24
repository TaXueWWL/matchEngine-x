package com.thunder.matchenginex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "trading")
@EnableConfigurationProperties
public class TradingPairsConfig {

    private Map<String, TradingPair> pairs;

    public boolean isValidSymbol(String symbol) {
        TradingPair pair = pairs.get(symbol);
        return pair != null && pair.isEnabled();
    }

    public TradingPair getTradingPair(String symbol) {
        return pairs.get(symbol);
    }

    public List<String> getEnabledSymbols() {
        return pairs.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
    }
}