package cn.lineai.engine.protocol;

/** Requested reasoning behaviour, before vendor-specific mapping. */
public final class ReasoningSpec {

    /** Lowercased base URL used for provider detection. */
    public final String baseUrl;
    /** Lowercased model identifier used for provider detection. */
    public final String modelId;
    /** Lowercased provider identifier used for provider detection. */
    public final String providerId;
    /** Requested effort level; defaults to {@code medium} when absent. */
    public final String requestedEffort;
    /** Whether reasoning content must be preserved across follow-up requests. */
    public final boolean preserve;

    /**
     * Creates a spec; identity fields are lowercased for matching and a null effort becomes
     * {@code medium}.
     *
     * @param baseUrl provider base URL, may be null
     * @param modelId model identifier, may be null
     * @param providerId provider identifier, may be null
     * @param requestedEffort one of {@code off}, {@code low}, {@code medium}, {@code high},
     *        {@code max}, {@code auto}; may be null
     * @param preserve whether reasoning content is carried into subsequent requests
     */
    public ReasoningSpec(String baseUrl, String modelId, String providerId,
                         String requestedEffort, boolean preserve) {
        this.baseUrl = lower(baseUrl);
        this.modelId = lower(modelId);
        this.providerId = lower(providerId);
        this.requestedEffort = requestedEffort == null ? "medium" : requestedEffort;
        this.preserve = preserve;
    }

    /** Returns a spec that disables reasoning entirely. */
    public static ReasoningSpec off() {
        return new ReasoningSpec("", "", "", "off", false);
    }

    /** Returns {@code false} when the compatible effort is {@code off}, {@code true} otherwise. */
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

    /**
     * Maps the effective effort to an explicit token budget for providers that require one.
     *
     * @return budget in tokens: 1024 for low, 8192 for high, 16000 for max, 4096 otherwise
     */
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

    /**
     * Returns {@code true} when the endpoint, provider or model identifies as a GLM (Zhipu)
     * deployment; GLM accepts only the low/high/max effort levels.
     */
    public boolean isGlm() {
        return baseUrl.contains("bigmodel") || baseUrl.contains("zhipu")
                || providerId.contains("zhipu") || providerId.contains("glm")
                || modelId.contains("glm");
    }

    /** Lowercases for case-insensitive provider matching; null becomes the empty string. */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
