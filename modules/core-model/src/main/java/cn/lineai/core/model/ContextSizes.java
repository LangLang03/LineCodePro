package cn.lineai.core.model;

import java.util.Locale;

/** Parses and formats context-window sizes such as {@code 128k} or {@code 1M}. */
public final class ContextSizes {

    public static final int UNSET = 0;

    private ContextSizes() {
    }

    public static int parse(String input) {
        if (input == null) return UNSET;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return UNSET;
        String lower = trimmed.toLowerCase(Locale.US);
        try {
            double value;
            if (lower.endsWith("k")) {
                value = Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000d;
            } else if (lower.endsWith("m")) {
                value = Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000000d;
            } else {
                value = Double.parseDouble(lower);
            }
            if (value <= 0d) return UNSET;
            long rounded = Math.round(value);
            if (rounded <= 0L || rounded > Integer.MAX_VALUE) return UNSET;
            return (int) rounded;
        } catch (NumberFormatException error) {
            return UNSET;
        }
    }

    public static String format(int size) {
        if (size <= 0) return "";
        if (size >= 1000000 && size % 1000000 == 0) return (size / 1000000) + "M";
        if (size >= 1000000) return decimal(size / 1000000d) + "M";
        if (size >= 1000 && size % 1000 == 0) return (size / 1000) + "K";
        if (size >= 1000) return decimal(size / 1000d) + "K";
        return String.valueOf(size);
    }

    private static String decimal(double value) {
        if (Math.abs(value - Math.round(value)) < 0.00001d) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
