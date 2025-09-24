package com.thunder.matchenginex.service;

import com.thunder.matchenginex.model.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class AccountService {

    private final ConcurrentMap<Long, Account> accounts = new ConcurrentHashMap<>();

    public Account getAccount(Long userId) {
        return accounts.computeIfAbsent(userId, id ->
            Account.builder().userId(id).build()
        );
    }

    public boolean addBalance(Long userId, String currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Account account = getAccount(userId);
        account.addBalance(currency, amount);
        log.info("Added balance: userId={}, currency={}, amount={} available={}", userId, currency, amount, account.getTotalBalance(currency));
        return true;
    }

    public boolean hasEnoughBalance(Long userId, String currency, BigDecimal amount) {
        Account account = getAccount(userId);
        return account.getAvailableBalance(currency).compareTo(amount) >= 0;
    }

    public boolean freezeBalance(Long userId, String currency, BigDecimal amount) {
        Account account = getAccount(userId);
        boolean success = account.freezeAmount(currency, amount);
        if (success) {
            log.info("Frozen balance: userId={}, currency={}, amount={}", userId, currency, amount);
        }
        return success;
    }

    public void unfreezeBalance(Long userId, String currency, BigDecimal amount) {
        Account account = getAccount(userId);
        account.unfreezeAmount(currency, amount);
        log.info("Unfrozen balance: userId={}, currency={}, amount={}", userId, currency, amount);
    }

    public BigDecimal getAvailableBalance(Long userId, String currency) {
        return getAccount(userId).getAvailableBalance(currency);
    }

    public BigDecimal getTotalBalance(Long userId, String currency) {
        return getAccount(userId).getTotalBalance(currency);
    }

    /**
     * Transfer funds from frozen balance of fromUser to available balance of toUser - 从发送用户的冻结余额转移资金到接收用户的可用余额
     * Used for trade settlement - 用于交易结算
     */
    public boolean transferFromFrozen(Long fromUserId, Long toUserId, String currency, BigDecimal amount) {
        Account fromAccount = getAccount(fromUserId);
        Account toAccount = getAccount(toUserId);

        // Check if fromUser has enough frozen balance - 检查从用户是否有足够的冻结余额
        if (fromAccount.getFrozenAmount(currency).compareTo(amount) < 0) {
            log.error("Insufficient frozen balance for transfer: userId={}, currency={}, required={}, frozen={}",
                    fromUserId, currency, amount, fromAccount.getFrozenAmount(currency));
            return false;
        }

        // Remove from sender's frozen balance (don't add to available) - 从发送者的冻结余额中移除 (不添加到可用余额)
        fromAccount.getFrozen().merge(currency, amount.negate(), BigDecimal::add);

        // Add to receiver's available balance - 添加到接收者的可用余额
        toAccount.addBalance(currency, amount);

        log.info("Transferred from frozen: from userId={} to userId={}, currency={}, amount={}",
                fromUserId, toUserId, currency, amount);
        return true;
    }
}