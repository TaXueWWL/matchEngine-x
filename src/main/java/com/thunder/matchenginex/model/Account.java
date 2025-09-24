package com.thunder.matchenginex.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private Long userId;
    private Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private Map<String, BigDecimal> frozen = new ConcurrentHashMap<>();

    @Builder
    public Account(Long userId) {
        this.userId = userId;
        this.balances = new ConcurrentHashMap<>();
        this.frozen = new ConcurrentHashMap<>();
    }

    public BigDecimal getAvailableBalance(String currency) {
        return balances.getOrDefault(currency, BigDecimal.ZERO);
    }

    public BigDecimal getFrozenAmount(String currency) {
        return frozen.getOrDefault(currency, BigDecimal.ZERO);
    }

    public BigDecimal getTotalBalance(String currency) {
        return getAvailableBalance(currency).add(getFrozenAmount(currency));
    }

    public void addBalance(String currency, BigDecimal amount) {
        balances.merge(currency, amount, BigDecimal::add);
    }

    public boolean freezeAmount(String currency, BigDecimal amount) {
        BigDecimal available = getAvailableBalance(currency);
        if (available.compareTo(amount) >= 0) {
            balances.merge(currency, amount.negate(), BigDecimal::add);
            frozen.merge(currency, amount, BigDecimal::add);
            return true;
        }
        return false;
    }

    public void unfreezeAmount(String currency, BigDecimal amount) {
        frozen.merge(currency, amount.negate(), BigDecimal::add);
        balances.merge(currency, amount, BigDecimal::add);
    }
}