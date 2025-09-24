package com.thunder.matchenginex.grpc;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.orderbook.OrderBook;
import com.thunder.matchenginex.orderbook.PriceLevel;
import com.thunder.matchenginex.service.TradingService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class TradingGrpcService extends TradingServiceGrpc.TradingServiceImplBase {

    private final TradingService tradingService;

    @Override
    public void placeOrder(TradingServiceProto.PlaceOrderRequest request,
                          StreamObserver<TradingServiceProto.PlaceOrderResponse> responseObserver) {
        try {
            // Validate request
            tradingService.validateOrder(
                    request.getSymbol(),
                    request.getUserId(),
                    convertOrderSide(request.getSide()),
                    convertOrderType(request.getType()),
                    new BigDecimal(request.getPrice()),
                    new BigDecimal(request.getQuantity())
            );

            // Place order
            long orderId = tradingService.placeOrder(
                    request.getSymbol(),
                    request.getUserId(),
                    convertOrderSide(request.getSide()),
                    convertOrderType(request.getType()),
                    new BigDecimal(request.getPrice()),
                    new BigDecimal(request.getQuantity())
            );

            TradingServiceProto.PlaceOrderResponse response = TradingServiceProto.PlaceOrderResponse.newBuilder()
                    .setOrderId(orderId)
                    .setSuccess(true)
                    .setMessage("Order placed successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error placing order: {}", e.getMessage(), e);

            TradingServiceProto.PlaceOrderResponse response = TradingServiceProto.PlaceOrderResponse.newBuilder()
                    .setOrderId(0)
                    .setSuccess(false)
                    .setMessage("Error placing order: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void cancelOrder(TradingServiceProto.CancelOrderRequest request,
                           StreamObserver<TradingServiceProto.CancelOrderResponse> responseObserver) {
        try {
            boolean success = tradingService.cancelOrder(
                    request.getOrderId(),
                    request.getSymbol(),
                    request.getUserId()
            );

            TradingServiceProto.CancelOrderResponse response = TradingServiceProto.CancelOrderResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(success ? "Order cancelled successfully" : "Order not found")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error cancelling order: {}", e.getMessage(), e);

            TradingServiceProto.CancelOrderResponse response = TradingServiceProto.CancelOrderResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error cancelling order: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void modifyOrder(TradingServiceProto.ModifyOrderRequest request,
                           StreamObserver<TradingServiceProto.ModifyOrderResponse> responseObserver) {
        try {
            boolean success = tradingService.modifyOrder(
                    request.getOrderId(),
                    request.getSymbol(),
                    request.getUserId(),
                    new BigDecimal(request.getNewPrice()),
                    new BigDecimal(request.getNewQuantity())
            );

            TradingServiceProto.ModifyOrderResponse response = TradingServiceProto.ModifyOrderResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(success ? "Order modified successfully" : "Order not found")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error modifying order: {}", e.getMessage(), e);

            TradingServiceProto.ModifyOrderResponse response = TradingServiceProto.ModifyOrderResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error modifying order: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void queryOrder(TradingServiceProto.QueryOrderRequest request,
                          StreamObserver<TradingServiceProto.QueryOrderResponse> responseObserver) {
        try {
            Order order = tradingService.queryOrder(request.getOrderId(), request.getSymbol());

            TradingServiceProto.QueryOrderResponse.Builder responseBuilder = TradingServiceProto.QueryOrderResponse.newBuilder()
                    .setFound(order != null);

            if (order != null) {
                responseBuilder.setOrder(convertToProtoOrder(order));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error querying order: {}", e.getMessage(), e);

            TradingServiceProto.QueryOrderResponse response = TradingServiceProto.QueryOrderResponse.newBuilder()
                    .setFound(false)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void queryOrderBook(TradingServiceProto.QueryOrderBookRequest request,
                              StreamObserver<TradingServiceProto.QueryOrderBookResponse> responseObserver) {
        try {
            OrderBook orderBook = tradingService.getOrderBook(request.getSymbol());
            int depth = request.getDepth() > 0 ? request.getDepth() : 10;

            List<PriceLevel> buyLevels = orderBook.getBuyLevels(depth);
            List<PriceLevel> sellLevels = orderBook.getSellLevels(depth);

            TradingServiceProto.QueryOrderBookResponse.Builder responseBuilder = TradingServiceProto.QueryOrderBookResponse.newBuilder()
                    .setSymbol(request.getSymbol());

            for (PriceLevel level : buyLevels) {
                responseBuilder.addBuyLevels(convertToProtoLevel(level));
            }

            for (PriceLevel level : sellLevels) {
                responseBuilder.addSellLevels(convertToProtoLevel(level));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error querying order book: {}", e.getMessage(), e);

            TradingServiceProto.QueryOrderBookResponse response = TradingServiceProto.QueryOrderBookResponse.newBuilder()
                    .setSymbol(request.getSymbol())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private OrderSide convertOrderSide(TradingServiceProto.OrderSide protoSide) {
        return switch (protoSide) {
            case BUY -> OrderSide.BUY;
            case SELL -> OrderSide.SELL;
            default -> throw new IllegalArgumentException("Unknown order side: " + protoSide);
        };
    }

    private OrderType convertOrderType(TradingServiceProto.OrderType protoType) {
        return switch (protoType) {
            case LIMIT -> OrderType.LIMIT;
            case MARKET -> OrderType.MARKET;
            case IOC -> OrderType.IOC;
            case FOK -> OrderType.FOK;
            case POST_ONLY -> OrderType.POST_ONLY;
            default -> throw new IllegalArgumentException("Unknown order type: " + protoType);
        };
    }

    private TradingServiceProto.Order convertToProtoOrder(Order order) {
        return TradingServiceProto.Order.newBuilder()
                .setOrderId(order.getOrderId())
                .setSymbol(order.getSymbol())
                .setUserId(order.getUserId())
                .setSide(convertToProtoOrderSide(order.getSide()))
                .setType(convertToProtoOrderType(order.getType()))
                .setPrice(order.getPrice().toString())
                .setQuantity(order.getQuantity().toString())
                .setFilledQuantity(order.getFilledQuantity().toString())
                .setRemainingQuantity(order.getRemainingQuantity().toString())
                .setStatus(convertToProtoOrderStatus(order.getStatus()))
                .setTimestamp(order.getTimestamp())
                .setSequence(order.getSequence())
                .build();
    }

    private TradingServiceProto.OrderSide convertToProtoOrderSide(OrderSide side) {
        return switch (side) {
            case BUY -> TradingServiceProto.OrderSide.BUY;
            case SELL -> TradingServiceProto.OrderSide.SELL;
        };
    }

    private TradingServiceProto.OrderType convertToProtoOrderType(OrderType type) {
        return switch (type) {
            case LIMIT -> TradingServiceProto.OrderType.LIMIT;
            case MARKET -> TradingServiceProto.OrderType.MARKET;
            case IOC -> TradingServiceProto.OrderType.IOC;
            case FOK -> TradingServiceProto.OrderType.FOK;
            case POST_ONLY -> TradingServiceProto.OrderType.POST_ONLY;
        };
    }

    private TradingServiceProto.OrderStatus convertToProtoOrderStatus(com.thunder.matchenginex.enums.OrderStatus status) {
        return switch (status) {
            case NEW -> TradingServiceProto.OrderStatus.NEW;
            case PARTIALLY_FILLED -> TradingServiceProto.OrderStatus.PARTIALLY_FILLED;
            case FILLED -> TradingServiceProto.OrderStatus.FILLED;
            case CANCELLED -> TradingServiceProto.OrderStatus.CANCELLED;
            case REJECTED -> TradingServiceProto.OrderStatus.REJECTED;
        };
    }

    private TradingServiceProto.PriceLevel convertToProtoLevel(PriceLevel level) {
        return TradingServiceProto.PriceLevel.newBuilder()
                .setPrice(level.getPrice().toString())
                .setTotalQuantity(level.getTotalQuantity().toString())
                .setOrderCount(level.getOrderCount())
                .build();
    }
}