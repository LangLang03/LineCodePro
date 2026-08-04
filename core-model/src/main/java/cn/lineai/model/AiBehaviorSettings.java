package cn.lineai.model;

public final class AiBehaviorSettings {
    public static final String TONE_CODING = "coding";
    public static final String TONE_CHAT = "chat";

    public static final String REASONING_OFF = "off";
    public static final String REASONING_AUTO = "auto";
    public static final String REASONING_LOW = "low";
    public static final String REASONING_MEDIUM = "medium";
    public static final String REASONING_HIGH = "high";
    public static final String REASONING_MAX = "max";

    private final String toneMode;
    private final boolean thinkingScrollEnabled;
    private final boolean thinkingAutoExpandEnabled;
    private final String reasoningEffort;
    private final boolean preserveReasoningEnabled;
    private final boolean learningModeEnabled;
    private final boolean softCompactionEnabled;

    public AiBehaviorSettings(
            String toneMode,
            boolean thinkingScrollEnabled,
            boolean thinkingAutoExpandEnabled,
            String reasoningEffort,
            boolean preserveReasoningEnabled,
            boolean learningModeEnabled
    ) {
        this(toneMode, thinkingScrollEnabled, thinkingAutoExpandEnabled, reasoningEffort,
                preserveReasoningEnabled, learningModeEnabled, true);
    }

    public AiBehaviorSettings(
            String toneMode,
            boolean thinkingScrollEnabled,
            boolean thinkingAutoExpandEnabled,
            String reasoningEffort,
            boolean preserveReasoningEnabled,
            boolean learningModeEnabled,
            boolean softCompactionEnabled
    ) {
        this.toneMode = normalizeTone(toneMode);
        this.thinkingScrollEnabled = thinkingScrollEnabled;
        this.thinkingAutoExpandEnabled = thinkingAutoExpandEnabled;
        this.reasoningEffort = normalizeReasoningEffort(reasoningEffort);
        this.preserveReasoningEnabled = preserveReasoningEnabled;
        this.learningModeEnabled = learningModeEnabled;
        this.softCompactionEnabled = softCompactionEnabled;
    }

    public String getToneMode() {
        return toneMode;
    }

    public boolean isThinkingScrollEnabled() {
        return thinkingScrollEnabled;
    }

    public boolean isThinkingAutoExpandEnabled() {
        return thinkingAutoExpandEnabled;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public boolean isPreserveReasoningEnabled() {
        return preserveReasoningEnabled;
    }

    public boolean isLearningModeEnabled() {
        return learningModeEnabled;
    }

    /**
     * 软压缩开关（50% 增量压缩）。关闭后只保留 80% 硬触发，压缩行为对齐 codex：
     * 仅在上下文占用达到硬阈值时全量压缩并保留最近用户消息。
     */
    public boolean isSoftCompactionEnabled() {
        return softCompactionEnabled;
    }

    public static String normalizeTone(String tone) {
        if (TONE_CHAT.equals(tone)) {
            return TONE_CHAT;
        }
        return TONE_CODING;
    }

    public static String normalizeReasoningEffort(String effort) {
        if (REASONING_OFF.equals(effort)
                || REASONING_AUTO.equals(effort)
                || REASONING_LOW.equals(effort)
                || REASONING_HIGH.equals(effort)
                || REASONING_MAX.equals(effort)) {
            return effort;
        }
        return REASONING_MEDIUM;
    }

    public static String concreteReasoningEffort(String effort) {
        String normalized = normalizeReasoningEffort(effort);
        if (REASONING_AUTO.equals(normalized)) {
            return REASONING_MEDIUM;
        }
        return normalized;
    }

    public static boolean isReasoningEnabled(String effort) {
        return !REASONING_OFF.equals(normalizeReasoningEffort(effort));
    }
}
