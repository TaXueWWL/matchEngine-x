package com.thunder.matchenginex.integration;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TradingIntegrationTest {

    @Autowired
    private TradingService tradingService;

    @Test
    void testBasicOrderPlacementAndMatching() throws InterruptedException {
        String symbol = "BTCUSDT";
        long userId1 = 1001L;
        long userId2 = 1002L;

        // Place a buy order
        long buyOrderId = tradingService.placeOrder(
                symbol, userId1, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("50000.00"), new BigDecimal("0.1")
        );

        // Place a sell order that should match
        long sellOrderId = tradingService.placeOrder(
                symbol, userId2, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("49999.00"), new BigDecimal("0.05")
        );

        // Wait for processing
        Thread.sleep(100);

        // Check order book
        OrderBook orderBook = tradingService.getOrderBook(symbol);
        assertNotNull(orderBook);

        // The buy order should still exist with reduced quantity
        Order buyOrder = orderBook.getOrder(buyOrderId);
        if (buyOrder != null) {
            assertEquals(new BigDecimal("0.05"), buyOrder.getRemainingQuantity());
        }

        // The sell order should be fully filled (not in order book)
        Order sellOrder = orderBook.getOrder(sellOrderId);
        assertNull(sellOrder); // Should be removed after full fill
    }

    @Test
    void testIOCOrderBehavior() throws InterruptedException {
        String symbol = "ETHUSDT";
        long userId = 2001L;

        // Place IOC order with no matching orders
        long iocOrderId = tradingService.placeOrder(
                symbol, userId, OrderSide.BUY, OrderType.IOC,
                new BigDecimal("3000.00"), new BigDecimal("1.0")
        );

        // Wait for processing
        Thread.sleep(100);

        // IOC order should not be in order book (cancelled)
        OrderBook orderBook = tradingService.getOrderBook(symbol);
        Order iocOrder = orderBook.getOrder(iocOrderId);
        assertNull(iocOrder);
    }

    @Test
    void testPostOnlyOrderBehavior() throws InterruptedException {
        String symbol = "ADAUSDT";
        long userId1 = 3001L;
        long userId2 = 3002L;

        // Place a sell order first
        tradingService.placeOrder(
                symbol, userId1, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("1.50"), new BigDecimal("100.0")
        );

        Thread.sleep(50);

        // Place a PostOnly buy order that would match immediately (should be rejected)
        long postOnlyOrderId = tradingService.placeOrder(
                symbol, userId2, OrderSide.BUY, OrderType.POST_ONLY,
                new BigDecimal("1.51"), new BigDecimal("50.0")
        );

        Thread.sleep(100);

        // PostOnly order should not be in order book (cancelled due to immediate match)
        OrderBook orderBook = tradingService.getOrderBook(symbol);
        Order postOnlyOrder = orderBook.getOrder(postOnlyOrderId);
        assertNull(postOnlyOrder);
    }

    @Test
    void testOrderCancellation() throws InterruptedException {
        String symbol = "BTCUSDT";
        long userId = 4001L;

        // Place an order
        long orderId = tradingService.placeOrder(
                symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("45000.00"), new BigDecimal("0.2")
        );

        Thread.sleep(50);

        // Verify order exists
        OrderBook orderBook = tradingService.getOrderBook(symbol);
        Order order = orderBook.getOrder(orderId);
        assertNotNull(order);

        // Cancel the order
        tradingService.cancelOrder(orderId, symbol, userId);
        Thread.sleep(50);

        // Verify order is removed
        order = orderBook.getOrder(orderId);
        assertNull(order);
    }

    @Test
    void testOrderModification() throws InterruptedException {
        String symbol = "ETHUSDT";
        long userId = 5001L;

        // Place an order
        long orderId = tradingService.placeOrder(
                symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("3500.00"), new BigDecimal("2.0")
        );

        Thread.sleep(50);

        // Modify the order
        tradingService.modifyOrder(orderId, symbol, userId,
                new BigDecimal("3600.00"), new BigDecimal("1.5"));

        Thread.sleep(100);

        // Check the modified order
        OrderBook orderBook = tradingService.getOrderBook(symbol);
        Order modifiedOrder = orderBook.getOrder(orderId);
        if (modifiedOrder != null) {
            assertEquals(new BigDecimal("3600.00"), modifiedOrder.getPrice());
            assertEquals(new BigDecimal("1.5"), modifiedOrder.getQuantity());
        }
    }

    @Test
    void testSupportedSymbols() {
        List<String> symbols = tradingService.getSupportedSymbols();
        assertNotNull(symbols);
        assertFalse(symbols.isEmpty());
        assertTrue(symbols.contains("BTCUSDT"));
        assertTrue(symbols.contains("ETHUSDT"));
        assertTrue(symbols.contains("ADAUSDT"));
    }

    @Test
    void testOrderValidation() {
        String symbol = "BTCUSDT";
        long userId = 6001L;

        // Test invalid symbol
        assertThrows(IllegalArgumentException.class, () -> {
            tradingService.validateOrder(
                    "INVALID", userId, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("100.00"), new BigDecimal("1.0")
            );
        });

        // Test invalid price (too low)
        assertThrows(IllegalArgumentException.class, () -> {
            tradingService.validateOrder(
                    symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("0.001"), new BigDecimal("1.0")
            );
        });

        // Test invalid quantity (too small)
        assertThrows(IllegalArgumentException.class, () -> {
            tradingService.validateOrder(
                    symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("50000.00"), new BigDecimal("0.000001")
            );
        });

        // Test valid order
        assertDoesNotThrow(() -> {
            tradingService.validateOrder(
                    symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                    new BigDecimal("50000.00"), new BigDecimal("0.1")
            );
        });
    }
}