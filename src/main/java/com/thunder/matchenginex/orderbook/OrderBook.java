package com.thunder.matchenginex.orderbook;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.model.Order;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class OrderBook {
    private final String symbol;

    // Buy orders (highest price first)
    private final NavigableMap<BigDecimal, PriceLevel> buyLevels = new TreeMap<>(Collections.reverseOrder());

    // Sell orders (lowest price first)
    private final NavigableMap<BigDecimal, PriceLevel> sellLevels = new TreeMap<>();

    // Fast order lookup
    private final MutableLongObjectMap<Order> orderMap = new LongObjectHashMap<>();

    // Track price level for each order for fast removal
    private final Map<Long, PriceLevel> orderToPriceLevelMap = new ConcurrentHashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void addOrder(Order order) {
        orderMap.put(order.getOrderId(), order);

        NavigableMap<BigDecimal, PriceLevel> levels = order.getSide() == OrderSide.BUY ? buyLevels : sellLevels;

        PriceLevel priceLevel = levels.computeIfAbsent(order.getPrice(), PriceLevel::new);
        priceLevel.addOrder(order);
        orderToPriceLevelMap.put(order.getOrderId(), priceLevel);

        log.debug("Added order {} to {} side at price {}",
                order.getOrderId(), order.getSide(), order.getPrice());
    }

    public boolean removeOrder(long orderId) {
        Order order = orderMap.remove(orderId);
        if (order == null) {
            return false;
        }

        PriceLevel priceLevel = orderToPriceLevelMap.remove(orderId);
        if (priceLevel != null) {
            priceLevel.removeOrder(order);

            // Remove empty price level
            if (priceLevel.isEmpty()) {
                NavigableMap<BigDecimal, PriceLevel> levels =
                    order.getSide() == OrderSide.BUY ? buyLevels : sellLevels;
                levels.remove(priceLevel.getPrice());
            }
        }

        log.debug("Removed order {} from {} side", orderId, order.getSide());
        return true;
    }

    public Order getOrder(long orderId) {
        return orderMap.get(orderId);
    }

    public PriceLevel getBestBuyLevel() {
        return buyLevels.isEmpty() ? null : buyLevels.firstEntry().getValue();
    }

    public PriceLevel getBestSellLevel() {
        return sellLevels.isEmpty() ? null : sellLevels.firstEntry().getValue();
    }

    public BigDecimal getBestBuyPrice() {
        PriceLevel level = getBestBuyLevel();
        return level != null ? level.getPrice() : null;
    }

    public BigDecimal getBestSellPrice() {
        PriceLevel level = getBestSellLevel();
        return level != null ? level.getPrice() : null;
    }

    public List<PriceLevel> getBuyLevels(int maxLevels) {
        return buyLevels.values().stream()
                .limit(maxLevels)
                .toList();
    }

    public List<PriceLevel> getSellLevels(int maxLevels) {
        return sellLevels.values().stream()
                .limit(maxLevels)
                .toList();
    }

    public boolean isEmpty() {
        return buyLevels.isEmpty() && sellLevels.isEmpty();
    }

    public int getTotalOrders() {
        return orderMap.size();
    }

    public void updateOrderQuantity(long orderId, BigDecimal newQuantity) {
        Order order = orderMap.get(orderId);
        if (order != null) {
            PriceLevel priceLevel = orderToPriceLevelMap.get(orderId);
            if (priceLevel != null) {
                BigDecimal oldQuantity = order.getRemainingQuantity();
                order.setRemainingQuantity(newQuantity);
                priceLevel.updateQuantity(order, oldQuantity, newQuantity);
            }
        }
    }

    public BigDecimal getMidPrice() {
        BigDecimal bestBid = getBestBuyPrice();
        BigDecimal bestAsk = getBestSellPrice();

        if (bestBid != null && bestAsk != null) {
            return bestBid.add(bestAsk).divide(BigDecimal.valueOf(2));
        }
        return null;
    }

    public BigDecimal getSpread() {
        BigDecimal bestBid = getBestBuyPrice();
        BigDecimal bestAsk = getBestSellPrice();

        if (bestBid != null && bestAsk != null) {
            return bestAsk.subtract(bestBid);
        }
        return null;
    }
}