package cn.lineai.core.model;

import java.util.Locale;

/**
 * Parses and formats context-window sizes such as {@code 128k}, {@code 1M} or {@code 100K}.
 *
 * <p>Parsing is case-insensitive and tolerates surrounding or embedded whitespace, so
 * {@code " 1 m "} and {@code "1M"} both mean one million tokens. Formatting is lossless:
 * {@code parse(format(n)) == n} for every accepted positive size, because a scaled form is
 * emitted only when it reads back exactly.</p>
 */
public final class ContextSizes {

    /** Sentinel for "no value / unparseable"; callers fall back to their own default. */
    public static final int UNSET = 0;

    private static final double KILO = 1000d;
    private static final double MEGA = 1000000d;

    private ContextSizes() {
    }

    public static int parse(String input) {
        if (input == null) return UNSET;
        String compact = compact(input);
        if (compact.isEmpty()) return UNSET;
        double scale = 1d;
        char unit = compact.charAt(compact.length() - 1);
        String number = compact;
        if (unit == 'k') {
            scale = KILO;
            number = compact.substring(0, compact.length() - 1);
        } else if (unit == 'm') {
            scale = MEGA;
            number = compact.substring(0, compact.length() - 1);
        }
        if (number.isEmpty()) return UNSET;
        try {
            double value = Double.parseDouble(number) * scale;
            if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0d) return UNSET;
            long rounded = Math.round(value);
            if (rounded <= 0L || rounded > Integer.MAX_VALUE) return UNSET;
            return (int) rounded;
        } catch (NumberFormatException error) {
            return UNSET;
        }
    }

    /**
     * Friendly text that always reads back to {@code size} through {@link #parse(String)}.
     *
     * <p>Whole millions become {@code 3M}, whole thousands {@code 128K}, and other sizes use up
     * to three trimmed decimals ({@code 131.072K}) only when that representation is exact;
     * anything else stays a plain token count such as {@code 131072}.</p>
     */
    public static String format(int size) {
        if (size <= 0) return "";
        if (size >= 1000000 && size % 1000000 == 0) return (size / 1000000) + "M";
        if (size >= 1000000) {
            String mega = scaled(size / MEGA, "M");
            if (parse(mega) == size) return mega;
        }
        if (size >= 1000 && size % 1000 == 0) return (size / 1000) + "K";
        if (size >= 1000) {
            String kilo = scaled(size / KILO, "K");
            if (parse(kilo) == size) return kilo;
        }
        return String.valueOf(size);
    }

    private static String scaled(double value, String suffix) {
        String text = String.format(Locale.US, "%.3f", value);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end) + suffix;
    }

    private static String compact(String input) {
        String trimmed = input.trim().toLowerCase(Locale.US);
        if (trimmed.indexOf(' ') < 0 && trimmed.indexOf('\t') < 0) return trimmed;
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char value = trimmed.charAt(index);
            if (!Character.isWhitespace(value)) out.append(value);
        }
        return out.toString();
    }
}
