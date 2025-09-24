package com.thunder.matchenginex.orderbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class OrderBookManager {

    private final ConcurrentMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    public boolean hasOrderBook(String symbol) {
        return orderBooks.containsKey(symbol);
    }

    public void removeOrderBook(String symbol) {
        OrderBook removed = orderBooks.remove(symbol);
        if (removed != null) {
            log.info("Removed order book for symbol: {}", symbol);
        }
    }

    public int getOrderBookCount() {
        return orderBooks.size();
    }

    public void clearAll() {
        orderBooks.clear();
        log.info("Cleared all order books");
    }
}