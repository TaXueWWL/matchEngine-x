package com.thunder.matchenginex.constant;

public final class TradingConstants {

    // Currency constants
    public static final String USDT = "USDT";
    public static final String USDT_SUFFIX = "USDT";

    // Order status constants
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_PARTIALLY_FILLED = "PARTIALLY_FILLED";

    // Default symbol
    public static final String DEFAULT_SYMBOL = "BTCUSDT";

    // Prevent instantiation
    private TradingConstants() {
        throw new IllegalStateException("Constants class cannot be instantiated");
    }
}