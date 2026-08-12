package com.snipeyfresh.incogshop.util;

import java.util.Locale;

public final class Money {
    private Money() {}

    /** Parses values like 10000, 10k, 2.5m, 1.2b and 1t. Returns -1 when invalid/non-positive. */
    public static double parsePositive(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
        if (s.isEmpty()) return -1;
        double multiplier = 1.0;
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            multiplier = switch (last) {
                case 'k' -> 1_000.0;
                case 'm' -> 1_000_000.0;
                case 'b' -> 1_000_000_000.0;
                case 't' -> 1_000_000_000_000.0;
                default -> -1.0;
            };
            if (multiplier < 0) return -1;
            s = s.substring(0, s.length() - 1).trim();
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            return Double.isFinite(value) && value > 0 ? Math.round(value * 100.0) / 100.0 : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
