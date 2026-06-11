package cn.lineai.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModelContextParser {
    private static final int DEFAULT_CONTEXT_TOKENS = 250000;
    private static final Pattern CONTEXT_SUFFIX = Pattern.compile("\\[([0-9]+(?:\\.[0-9]+)?)([kKmM]?)\\]$");

    private ModelContextParser() {
    }

    public static ModelContextInfo parse(String modelId) {
        String trimmed = modelId == null ? "" : modelId.trim();
        Matcher matcher = CONTEXT_SUFFIX.matcher(trimmed);
        if (!matcher.find()) {
            return new ModelContextInfo(trimmed, DEFAULT_CONTEXT_TOKENS, formatContextSize(DEFAULT_CONTEXT_TOKENS));
        }

        double rawNumber;
        try {
            rawNumber = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            rawNumber = DEFAULT_CONTEXT_TOKENS;
        }
        String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.US);
        double multiplier = "m".equals(unit) ? 1000000d : "k".equals(unit) ? 1000d : 1d;
        int contextTokens = Math.max(1, (int) Math.round(rawNumber * multiplier));
        String apiModelId = trimmed.substring(0, matcher.start()).trim();
        if (apiModelId.length() == 0) {
            apiModelId = trimmed;
        }
        return new ModelContextInfo(apiModelId, contextTokens, formatContextSize(contextTokens));
    }

    public static String apiModelId(String modelId) {
        return parse(modelId).getApiModelId();
    }

    public static String formatContextSize(int tokens) {
        if (tokens >= 1000000) {
            double value = tokens / 1000000d;
            return formatNumber(value) + "m";
        }
        if (tokens >= 1000) {
            double value = tokens / 1000d;
            return formatNumber(value) + "k";
        }
        return String.valueOf(tokens);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.00001d) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
