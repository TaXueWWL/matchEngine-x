package com.thunder.matchenginex.util;

import com.thunder.matchenginex.config.TradingPair;
import com.thunder.matchenginex.config.TradingPairsConfig;
import com.thunder.matchenginex.constant.TradingConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CurrencyUtils {

    @Autowired
    private TradingPairsConfig tradingPairsConfig;

    public String extractBaseCurrency(String symbol) {
        TradingPair tradingPair = tradingPairsConfig.getTradingPair(symbol);
        if (tradingPair != null) {
            return tradingPair.getBaseAsset();
        }
        // Fallback to string parsing if trading pair not found
        log.warn("Trading pair not found for symbol: {}, using fallback parsing", symbol);
        if (symbol.endsWith(TradingConstants.USDT_SUFFIX)) {
            return symbol.substring(0, symbol.length() - TradingConstants.USDT_SUFFIX.length());
        }
        return symbol.substring(0, 3);
    }

    public String extractQuoteCurrency(String symbol) {
        TradingPair tradingPair = tradingPairsConfig.getTradingPair(symbol);
        if (tradingPair != null) {
            return tradingPair.getQuoteAsset();
        }
        // Fallback to string parsing if trading pair not found
        log.warn("Trading pair not found for symbol: {}, using fallback parsing", symbol);
        if (symbol.endsWith(TradingConstants.USDT_SUFFIX)) {
            return TradingConstants.USDT;
        }
        return TradingConstants.USDT;
    }

    public TradingPair getTradingPair(String symbol) {
        return tradingPairsConfig.getTradingPair(symbol);
    }
}