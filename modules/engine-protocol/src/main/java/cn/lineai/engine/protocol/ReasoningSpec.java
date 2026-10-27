package cn.lineai.engine.protocol;

/** Requested reasoning behaviour, before vendor-specific mapping. */
public final class ReasoningSpec {

    public final String baseUrl;
    public final String modelId;
    public final String providerId;
    public final String requestedEffort;
    public final boolean preserve;

    public ReasoningSpec(String baseUrl, String modelId, String providerId,
                         String requestedEffort, boolean preserve) {
        this.baseUrl = lower(baseUrl);
        this.modelId = lower(modelId);
        this.providerId = lower(providerId);
        this.requestedEffort = requestedEffort == null ? "medium" : requestedEffort;
        this.preserve = preserve;
    }

    public static ReasoningSpec off() {
        return new ReasoningSpec("", "", "", "off", false);
    }

    public boolean enabled() {
        return !"off".equals(compatibleEffort());
    }

    /** Concrete effort after vendor compatibility; {@code auto} becomes {@code medium}. */
    public String effectiveEffort() {
        return "auto".equals(compatibleEffort()) ? "medium" : compatibleEffort();
    }

    /** GLM only distinguishes low/high/max; everything else keeps the normalised value. */
    public String compatibleEffort() {
        String effort = requestedEffort;
        if (!isGlm()) {
            return effort;
        }
        if ("max".equals(effort)) {
            return "max";
        }
        if ("off".equals(effort) || "low".equals(effort)) {
            return "low";
        }
        return "high";
    }

    public int thinkingBudget() {
        String effort = effectiveEffort();
        if ("low".equals(effort)) {
            return 1024;
        }
        if ("high".equals(effort)) {
            return 8192;
        }
        if ("max".equals(effort)) {
            return 16000;
        }
        return 4096;
    }

    public boolean isGlm() {
        return baseUrl.contains("bigmodel") || baseUrl.contains("zhipu")
                || providerId.contains("zhipu") || providerId.contains("glm")
                || modelId.contains("glm");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
