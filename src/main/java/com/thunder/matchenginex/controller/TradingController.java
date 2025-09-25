package com.thunder.matchenginex.controller;

import com.thunder.matchenginex.controller.dto.*;
import org.springframework.web.bind.annotation.PathVariable;
import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.orderbook.PriceLevel;
import com.thunder.matchenginex.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
@Validated
public class TradingController {

    private final TradingService tradingService;

    @PostMapping("/orders")
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        try {
            // Validate order
            tradingService.validateOrder(
                    request.getSymbol(),
                    request.getUserId(),
                    OrderSide.valueOf(request.getSide().toUpperCase()),
                    OrderType.valueOf(request.getType().toUpperCase()),
                    new BigDecimal(request.getPrice()),
                    new BigDecimal(request.getQuantity())
            );

            // Place order
            long orderId = tradingService.placeOrder(
                    request.getSymbol(),
                    request.getUserId(),
                    OrderSide.valueOf(request.getSide().toUpperCase()),
                    OrderType.valueOf(request.getType().toUpperCase()),
                    new BigDecimal(request.getPrice()),
                    new BigDecimal(request.getQuantity())
            );

            PlaceOrderResponse response = PlaceOrderResponse.builder()
                    .orderId(orderId)
                    .success(true)
                    .message("Order placed successfully")
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid order request: {}", e.getMessage());
            PlaceOrderResponse response = PlaceOrderResponse.builder()
                    .orderId(0L)
                    .success(false)
                    .message("Invalid order: " + e.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("Error placing order: {}", e.getMessage(), e);
            PlaceOrderResponse response = PlaceOrderResponse.builder()
                    .orderId(0L)
                    .success(false)
                    .message("Error placing order: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<CancelOrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam String symbol,
            @RequestParam Long userId) {
        try {
            boolean success = tradingService.cancelOrder(orderId, symbol, userId);

            CancelOrderResponse response = CancelOrderResponse.builder()
                    .success(success)
                    .message(success ? "Order cancelled successfully" : "Order not found")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cancelling order: {}", e.getMessage(), e);
            CancelOrderResponse response = CancelOrderResponse.builder()
                    .success(false)
                    .message("Error cancelling order: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/orders/{orderId}")
    public ResponseEntity<ModifyOrderResponse> modifyOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody ModifyOrderRequest request) {
        try {
            boolean success = tradingService.modifyOrder(
                    orderId,
                    request.getSymbol(),
                    request.getUserId(),
                    request.getNewPrice() != null ? new BigDecimal(request.getNewPrice()) : null,
                    request.getNewQuantity() != null ? new BigDecimal(request.getNewQuantity()) : null
            );

            ModifyOrderResponse response = ModifyOrderResponse.builder()
                    .success(success)
                    .message(success ? "Order modified successfully" : "Order not found")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error modifying order: {}", e.getMessage(), e);
            ModifyOrderResponse response = ModifyOrderResponse.builder()
                    .success(false)
                    .message("Error modifying order: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<QueryOrderResponse> queryOrder(
            @PathVariable Long orderId,
            @RequestParam String symbol) {
        try {
            Order order = tradingService.queryOrder(orderId, symbol);

            QueryOrderResponse response = QueryOrderResponse.builder()
                    .order(order != null ? convertToOrderDto(order) : null)
                    .found(order != null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error querying order: {}", e.getMessage(), e);
            QueryOrderResponse response = QueryOrderResponse.builder()
                    .order(null)
                    .found(false)
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") Integer depth) {
        try {
            OrderBook orderBook = tradingService.getOrderBook(symbol);

            List<PriceLevel> buyLevels = orderBook.getBuyLevels(depth);
            List<PriceLevel> sellLevels = orderBook.getSellLevels(depth);

            OrderBookResponse response = OrderBookResponse.builder()
                    .symbol(symbol)
                    .buyLevels(buyLevels.stream().map(this::convertToPriceLevelDto).toList())
                    .sellLevels(sellLevels.stream().map(this::convertToPriceLevelDto).toList())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error querying order book: {}", e.getMessage(), e);
            OrderBookResponse response = OrderBookResponse.builder()
                    .symbol(symbol)
                    .buyLevels(List.of())
                    .sellLevels(List.of())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/orderbook/{symbol}/summary")
    public ResponseEntity<OrderBookSummary> getOrderBookSummary(@PathVariable String symbol) {
        try {
            OrderBook orderBook = tradingService.getOrderBook(symbol);

            OrderBookSummary summary = OrderBookSummary.builder()
                    .symbol(symbol)
                    .bestBid(orderBook.getBestBuyPrice())
                    .bestAsk(orderBook.getBestSellPrice())
                    .midPrice(orderBook.getMidPrice())
                    .spread(orderBook.getSpread())
                    .totalOrders(orderBook.getTotalOrders())
                    .build();

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("Error getting order book summary: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders/user/{userId}")
    public ResponseEntity<List<OrderDto>> getUserOrders(
            @PathVariable Long userId,
            @RequestParam(required = false) String symbol) {
        try {
            List<Order> orders;
            if (symbol != null && !symbol.isEmpty()) {
                orders = tradingService.getUserOrders(userId, symbol);
            } else {
                orders = tradingService.getAllUserOrders(userId);
            }

            List<OrderDto> orderDtos = orders.stream()
                    .map(this::convertToOrderDto)
                    .toList();

            return ResponseEntity.ok(orderDtos);

        } catch (Exception e) {
            log.error("Error getting user orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders/user/{userId}/current")
    public ResponseEntity<List<OrderDto>> getUserCurrentOrders(
            @PathVariable Long userId,
            @RequestParam(required = false) String symbol) {
        try {
            List<Order> orders;
            if (symbol != null && !symbol.isEmpty()) {
                orders = tradingService.getUserCurrentOrders(userId, symbol);
            } else {
                orders = tradingService.getAllUserCurrentOrders(userId);
            }

            List<OrderDto> orderDtos = orders.stream()
                    .map(this::convertToOrderDto)
                    .toList();

            return ResponseEntity.ok(orderDtos);

        } catch (Exception e) {
            log.error("Error getting user current orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders/user/{userId}/history")
    public ResponseEntity<List<OrderDto>> getUserHistoryOrders(
            @PathVariable Long userId,
            @RequestParam(required = false) String symbol) {
        try {
            List<Order> orders;
            if (symbol != null && !symbol.isEmpty()) {
                orders = tradingService.getUserHistoryOrders(userId, symbol);
            } else {
                orders = tradingService.getAllUserHistoryOrders(userId);
            }

            List<OrderDto> orderDtos = orders.stream()
                    .map(this::convertToOrderDto)
                    .toList();

            return ResponseEntity.ok(orderDtos);

        } catch (Exception e) {
            log.error("Error getting user history orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/trades/{symbol}")
    public ResponseEntity<List<TradeDto>> getRecentTrades(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") Integer limit) {
        try {
            List<Trade> trades = tradingService.getRecentTrades(symbol, Math.min(limit, 100)); // Limit max 100

            List<TradeDto> tradeDtos = trades.stream()
                    .map(this::convertToTradeDto)
                    .toList();

            return ResponseEntity.ok(tradeDtos);

        } catch (Exception e) {
            log.error("Error getting recent trades for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private OrderDto convertToOrderDto(Order order) {
        return OrderDto.builder()
                .orderId(order.getOrderId())
                .symbol(order.getSymbol())
                .userId(order.getUserId())
                .side(order.getSide().name())
                .type(order.getType().name())
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .filledQuantity(order.getFilledQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .status(order.getStatus().name())
                .timestamp(order.getTimestamp())
                .sequence(order.getSequence())
                .build();
    }

    private PriceLevelDto convertToPriceLevelDto(PriceLevel priceLevel) {
        return PriceLevelDto.builder()
                .price(priceLevel.getPrice())
                .totalQuantity(priceLevel.getTotalQuantity())
                .orderCount(priceLevel.getOrderCount())
                .build();
    }

    private TradeDto convertToTradeDto(Trade trade) {
        // For display purposes, we show trades from the taker's perspective
        // Since we don't store the taker info directly, we'll use a simple approach:
        // Show alternating buy/sell or determine based on trade ID
        // 为了显示目的，我们从taker角度显示交易
        String side = (trade.getTradeId() % 2 == 0) ? "BUY" : "SELL";

        return TradeDto.builder()
                .tradeId(trade.getTradeId())
                .symbol(trade.getSymbol())
                .buyOrderId(trade.getBuyOrderId())
                .sellOrderId(trade.getSellOrderId())
                .buyUserId(trade.getBuyUserId())
                .sellUserId(trade.getSellUserId())
                .price(trade.getPrice())
                .quantity(trade.getQuantity())
                .timestamp(trade.getTimestamp())
                .side(side)
                .build();
    }
}