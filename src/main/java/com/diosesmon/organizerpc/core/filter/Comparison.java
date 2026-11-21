package com.diosesmon.organizerpc.core.filter;

public enum Comparison {
    EQ("="), GE("≥"), LE("≤");

    public final String symbol;

    Comparison(String symbol) {
        this.symbol = symbol;
    }

    public boolean test(int actual, int expected) {
        return switch (this) {
            case EQ -> actual == expected;
            case GE -> actual >= expected;
            case LE -> actual <= expected;
        };
    }

    public Comparison next() {
        Comparison[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
