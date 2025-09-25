package com.thunder.matchenginex.orderbook;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.model.Order;
import com.thunder.matchenginex.model.Trade;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2ObjectHashMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class OrderBook {
    private final String symbol;

    // Buy orders (highest price first) - Buy Order (最高价优先)
    private final NavigableMap<BigDecimal, PriceLevel> buyLevels = new TreeMap<>(Collections.reverseOrder());

    // Sell orders (lowest price first) - Sell Order (最低价优先)
    private final NavigableMap<BigDecimal, PriceLevel> sellLevels = new TreeMap<>();

    // Fast order lookup - 快速Order查找
    private final MutableLongObjectMap<Order> orderMap = new LongObjectHashMap<>();

    // Track price level for each order for fast removal - using Agrona for better performance - 跟踪每个Order的价格层级以实现快速移除 - 使用Agrona获得更好的性能
    private final Long2ObjectHashMap<PriceLevel> orderToPriceLevelMap = new Long2ObjectHashMap<>();

    // Historical orders (completed orders: FILLED, CANCELLED, REJECTED) - 历史Order (已完成的Order：FILLED, CANCELLED, REJECTED)
    private final MutableLongObjectMap<Order> historicalOrders = new LongObjectHashMap<>();

    // Recent trades (up to 30 recent trades) - 最近成交记录 (最多30笔)
    private final LinkedList<Trade> recentTrades = new LinkedList<>();
    private static final int MAX_RECENT_TRADES = 30;

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

        // Print order book when new order is added - 有新订单进入时打印订单簿
        printOrderBookOnNewOrder(order);
    }

    public boolean removeOrder(long orderId) {
        Order order = orderMap.remove(orderId);
        if (order == null) {
            return false;
        }

        PriceLevel priceLevel = orderToPriceLevelMap.remove(orderId);
        if (priceLevel != null) {
            priceLevel.removeOrder(order);

            // Remove empty price level - 移除空的价格层级
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

    public void addHistoricalOrder(Order order) {
        historicalOrders.put(order.getOrderId(), order);
        log.debug("Added order {} to historical orders with status {}", order.getOrderId(), order.getStatus());
    }

    public List<Order> getUserOrders(long userId) {
        List<Order> userOrders = new ArrayList<>();

        // Get active orders - 获取活跃Order
        for (Order order : orderMap.values()) {
            if (order.getUserId() == userId) {
                userOrders.add(order);
            }
        }

        // Get historical orders - 获取历史Order
        for (Order order : historicalOrders.values()) {
            if (order.getUserId() == userId) {
                userOrders.add(order);
            }
        }

        // Sort by timestamp (newest first) - 按时间戳排序 (最新的优先)
        userOrders.sort((o1, o2) -> Long.compare(o2.getTimestamp(), o1.getTimestamp()));

        return userOrders;
    }

    public List<Order> getUserHistoricalOrders(long userId) {
        List<Order> userOrders = new ArrayList<>();

        for (Order order : historicalOrders.values()) {
            if (order.getUserId() == userId) {
                userOrders.add(order);
            }
        }

        // Sort by timestamp (newest first) - 按时间戳排序 (最新的优先)
        userOrders.sort((o1, o2) -> Long.compare(o2.getTimestamp(), o1.getTimestamp()));

        return userOrders;
    }

    public Order getHistoricalOrder(long orderId) {
        return historicalOrders.get(orderId);
    }

    /**
     * 创建订单簿的快照副本用于推送，避免并发访问问题
     * Create a snapshot copy of the order book for push operations to avoid concurrent access issues
     */
    public OrderBookSnapshot createSnapshot() {
        // 创建价格层级的快照（深拷贝价格和数量信息，但不拷贝Order对象引用）
        List<PriceLevelSnapshot> buyLevelsSnapshot = buyLevels.entrySet().stream()
                .map(entry -> new PriceLevelSnapshot(
                    entry.getKey(),
                    entry.getValue().getTotalQuantity(),
                    entry.getValue().getOrderCount()
                ))
                .toList();

        List<PriceLevelSnapshot> sellLevelsSnapshot = sellLevels.entrySet().stream()
                .map(entry -> new PriceLevelSnapshot(
                    entry.getKey(),
                    entry.getValue().getTotalQuantity(),
                    entry.getValue().getOrderCount()
                ))
                .toList();

        return new OrderBookSnapshot(symbol, buyLevelsSnapshot, sellLevelsSnapshot);
    }

    /**
     * 订单簿快照数据结构
     */
    public static class OrderBookSnapshot {
        private final String symbol;
        private final List<PriceLevelSnapshot> buyLevels;
        private final List<PriceLevelSnapshot> sellLevels;

        public OrderBookSnapshot(String symbol, List<PriceLevelSnapshot> buyLevels, List<PriceLevelSnapshot> sellLevels) {
            this.symbol = symbol;
            this.buyLevels = buyLevels;
            this.sellLevels = sellLevels;
        }

        public String getSymbol() { return symbol; }
        public List<PriceLevelSnapshot> getBuyLevels(int maxLevels) {
            return buyLevels.stream().limit(maxLevels).toList();
        }
        public List<PriceLevelSnapshot> getSellLevels(int maxLevels) {
            return sellLevels.stream().limit(maxLevels).toList();
        }
    }

    /**
     * 价格层级快照数据结构
     */
    public static class PriceLevelSnapshot {
        private final BigDecimal price;
        private final BigDecimal totalQuantity;
        private final int orderCount;

        public PriceLevelSnapshot(BigDecimal price, BigDecimal totalQuantity, int orderCount) {
            this.price = price;
            this.totalQuantity = totalQuantity;
            this.orderCount = orderCount;
        }

        public BigDecimal getPrice() { return price; }
        public BigDecimal getTotalQuantity() { return totalQuantity; }
        public int getOrderCount() { return orderCount; }
    }

    /**
     * Add a trade to recent trades list
     * 将成交记录添加到最近成交列表
     */
    public synchronized void addTrade(Trade trade) {
        recentTrades.addFirst(trade); // Add to front for chronological order (newest first)

        // Keep only the most recent MAX_RECENT_TRADES trades
        while (recentTrades.size() > MAX_RECENT_TRADES) {
            recentTrades.removeLast();
        }

        log.debug("Added trade {} to recent trades list for symbol {}", trade.getTradeId(), symbol);
    }

    /**
     * Get recent trades list (newest first)
     * 获取最近成交列表 (最新的在前)
     */
    public synchronized List<Trade> getRecentTrades() {
        return new ArrayList<>(recentTrades);
    }

    /**
     * Get recent trades with limit
     * 获取指定数量的最近成交记录
     */
    public synchronized List<Trade> getRecentTrades(int limit) {
        if (limit >= recentTrades.size()) {
            return new ArrayList<>(recentTrades);
        }
        return recentTrades.stream().limit(limit).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Print order book when new order is added - 有新订单进入时打印订单簿
     */
    private void printOrderBookOnNewOrder(Order newOrder) {
        try {
            // 获取卖单（价格从低到高）- 最多10层
            List<PriceLevel> sellLevels = getSellLevels(10);

            // 获取买单（价格从高到低）- 最多10层
            List<PriceLevel> buyLevels = getBuyLevels(10);

            log.info("📊 ==================== 新订单进入订单簿: {} ====================", symbol);
            log.info("🆕 新订单: OrderId={}, Side={}, 价格={}, 数量={}",
                    newOrder.getOrderId(),
                    newOrder.getSide(),
                    formatPrice(newOrder.getPrice()),
                    formatQuantity(newOrder.getQuantity()));

            // 打印卖单 - 从卖10到卖1（价格从高到低显示，但实际是从低价到高价的倒序）
            if (sellLevels.isEmpty()) {
                log.info("📈 无卖单");
            } else {
                // 倒序打印，让最低价格在最下面（接近当前价格）
                for (int i = sellLevels.size() - 1; i >= 0; i--) {
                    PriceLevel level = sellLevels.get(i);
                    log.info("卖{} - 价格: {} USDT, 数量: {} BTC, 订单数: {}",
                        (sellLevels.size() - i),
                        formatPrice(level.getPrice()),
                        formatQuantity(level.getTotalQuantity()),
                        level.getOrderCount());
                }
            }

            log.info("💰 ----------------------------------------");

            // 打印买单 - 从买1到买10（价格从高到低）
            if (buyLevels.isEmpty()) {
                log.info("📉 无买单");
            } else {
                for (int i = 0; i < buyLevels.size(); i++) {
                    PriceLevel level = buyLevels.get(i);
                    log.info("买{} - 价格: {} USDT, 数量: {} BTC, 订单数: {}",
                        (i + 1),
                        formatPrice(level.getPrice()),
                        formatQuantity(level.getTotalQuantity()),
                        level.getOrderCount());
                }
            }

            // 打印价差信息
            BigDecimal midPrice = getMidPrice();
            BigDecimal spread = getSpread();
            if (midPrice != null && spread != null) {
                log.info("💰 中间价: {} USDT, 价差: {} USDT", formatPrice(midPrice), formatPrice(spread));
            }

            log.info("📊 订单总数: {}", getTotalOrders());
            log.info("📊 ========================================================");

        } catch (Exception e) {
            log.error("❌ Error printing order book on new order {}: {}", newOrder.getOrderId(), e.getMessage());
        }
    }

    /**
     * 格式化价格显示
     */
    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "N/A";
        }
        return String.format("%.2f", price);
    }

    /**
     * 格式化数量显示
     */
    private String formatQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return "N/A";
        }
        return String.format("%.6f", quantity);
    }
}