package com.snipeyfresh.incogshop.market;

public enum MarketMode {
    BUY_SELL,
    SELL_ONLY,
    DISABLED;

    public MarketMode next() {
        return switch (this) {
            case BUY_SELL -> SELL_ONLY;
            case SELL_ONLY -> DISABLED;
            case DISABLED -> BUY_SELL;
        };
    }
}
