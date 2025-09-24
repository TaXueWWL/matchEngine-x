package com.thunder.matchenginex.service;

import com.thunder.matchenginex.config.TradingPairsConfig;
import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.event.OrderEventProducer;
import com.thunder.matchenginex.orderbook.OrderBookManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试余额冻结功能是否正确工作
 * 验证用户不能下超过可用余额的订单，即使连续下单
 */
public class TradingServiceBalanceTest {

    @Mock
    private OrderEventProducer eventProducer;

    @Mock
    private OrderBookManager orderBookManager;

    @Mock
    private TradingPairsConfig tradingPairsConfig;

    private AccountService accountService;
    private TradingService tradingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 创建真实的AccountService实例
        accountService = new AccountService();

        // 创建TradingService实例
        tradingService = new TradingService(eventProducer, orderBookManager, tradingPairsConfig, accountService);

        // 设置mock行为
        when(tradingPairsConfig.isValidSymbol(anyString())).thenReturn(true);
        when(tradingPairsConfig.getTradingPair(anyString())).thenReturn(null); // 简化测试
    }

    @Test
    @DisplayName("测试用户总余额1000，第一笔订单占用500后，第二笔订单应该只能使用剩余的500")
    void testBalanceFreezingPreventsOverselling() {
        long userId = 1L;
        String symbol = "BTCUSDT";

        // 初始余额设置为1000 USDT
        accountService.addBalance(userId, "USDT", new BigDecimal("1000"));

        // 验证初始余额
        assertEquals(new BigDecimal("1000"), accountService.getAvailableBalance(userId, "USDT"));
        assertEquals(new BigDecimal("1000"), accountService.getTotalBalance(userId, "USDT"));

        // 第一笔买单：500 USDT (100 * 5)
        assertDoesNotThrow(() -> {
            tradingService.validateOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("5"));

            long orderId1 = tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("5"));

            assertTrue(orderId1 > 0);
        });

        // 验证第一笔订单后的余额状态
        assertEquals(new BigDecimal("500"), accountService.getAvailableBalance(userId, "USDT")); // 可用余额减少
        assertEquals(new BigDecimal("1000"), accountService.getTotalBalance(userId, "USDT")); // 总余额不变

        // 第二笔买单：尝试600 USDT (100 * 6) - 应该失败，因为可用余额只有500
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            tradingService.validateOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("6"));
        });

        assertTrue(exception.getMessage().contains("Insufficient balance"));

        // 第三笔买单：500 USDT (100 * 5) - 应该成功，正好用完剩余的可用余额
        assertDoesNotThrow(() -> {
            tradingService.validateOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("5"));

            long orderId2 = tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("5"));

            assertTrue(orderId2 > 0);
        });

        // 验证所有资金都被冻结
        assertEquals(new BigDecimal("0"), accountService.getAvailableBalance(userId, "USDT")); // 可用余额为0
        assertEquals(new BigDecimal("1000"), accountService.getTotalBalance(userId, "USDT")); // 总余额不变

        // 第四笔买单：任何金额都应该失败
        Exception exception2 = assertThrows(IllegalArgumentException.class, () -> {
            tradingService.validateOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("1"), new BigDecimal("1"));
        });

        assertTrue(exception2.getMessage().contains("Insufficient balance"));
    }

    @Test
    @DisplayName("测试卖单的余额冻结功能")
    void testSellOrderBalanceFreezing() {
        long userId = 2L;
        String symbol = "BTCUSDT";

        // 初始BTC余额设置为10 BTC
        accountService.addBalance(userId, "BTC", new BigDecimal("10"));

        // 验证初始余额
        assertEquals(new BigDecimal("10"), accountService.getAvailableBalance(userId, "BTC"));

        // 第一笔卖单：5 BTC
        assertDoesNotThrow(() -> {
            tradingService.validateOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("5"));

            long orderId1 = tradingService.placeOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("5"));

            assertTrue(orderId1 > 0);
        });

        // 验证余额状态
        assertEquals(new BigDecimal("5"), accountService.getAvailableBalance(userId, "BTC")); // 可用余额减少
        assertEquals(new BigDecimal("10"), accountService.getTotalBalance(userId, "BTC")); // 总余额不变

        // 第二笔卖单：尝试6 BTC - 应该失败
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            tradingService.validateOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("6"));
        });

        assertTrue(exception.getMessage().contains("Insufficient balance"));

        // 第三笔卖单：5 BTC - 应该成功
        assertDoesNotThrow(() -> {
            tradingService.validateOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("5"));

            long orderId2 = tradingService.placeOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("5"));

            assertTrue(orderId2 > 0);
        });

        // 验证所有BTC都被冻结
        assertEquals(new BigDecimal("0"), accountService.getAvailableBalance(userId, "BTC"));
        assertEquals(new BigDecimal("10"), accountService.getTotalBalance(userId, "BTC"));
    }

    @Test
    @DisplayName("测试tryPlaceOrder失败时资金解冻功能")
    void testFundsUnfreezeOnFailure() {
        long userId = 3L;
        String symbol = "BTCUSDT";

        // 初始余额设置为1000 USDT
        accountService.addBalance(userId, "USDT", new BigDecimal("1000"));

        // 模拟事件发布失败
        when(eventProducer.tryPublishCommand(any())).thenReturn(false);

        // 尝试下单，应该失败并且资金不被冻结
        boolean result = tradingService.tryPlaceOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("100"), new BigDecimal("5"));

        assertFalse(result); // 订单提交失败
        assertEquals(new BigDecimal("1000"), accountService.getAvailableBalance(userId, "USDT")); // 资金未被冻结
    }

    @Test
    @DisplayName("测试并发下单场景")
    void testConcurrentOrderPlacement() {
        long userId = 4L;
        String symbol = "BTCUSDT";

        // 初始余额设置为1000 USDT
        accountService.addBalance(userId, "USDT", new BigDecimal("1000"));

        // 模拟两个线程同时下单，每个订单600 USDT，只有一个应该成功
        Thread thread1 = new Thread(() -> {
            try {
                tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("100"), new BigDecimal("6"));
            } catch (Exception e) {
                // 预期可能有一个会失败
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("100"), new BigDecimal("6"));
            } catch (Exception e) {
                // 预期可能有一个会失败
            }
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            fail("Thread interrupted");
        }

        // 验证最多只有600 USDT被冻结（一个订单成功）
        BigDecimal availableBalance = accountService.getAvailableBalance(userId, "USDT");
        assertTrue(availableBalance.compareTo(new BigDecimal("400")) >= 0,
            "Available balance should be at least 400 USDT, but was: " + availableBalance);
    }
}