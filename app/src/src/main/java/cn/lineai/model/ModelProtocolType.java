package cn.lineai.model;

public enum ModelProtocolType {
    OPENAI_COMPATIBLE("OpenAI"),
    CODEX_RESPONSES("Codex"),
    ANTHROPIC_MESSAGES("Anthropic"),
    LOCAL_GGUF("本地");

    private final String label;

    ModelProtocolType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ModelProtocolType fromStorage(String value) {
        if (value == null) {
            return OPENAI_COMPATIBLE;
        }
        String normalized = value.trim();
        if ("openai".equalsIgnoreCase(normalized) || "openai_compatible".equalsIgnoreCase(normalized)) {
            return OPENAI_COMPATIBLE;
        }
        if ("codex".equalsIgnoreCase(normalized) || "codex_responses".equalsIgnoreCase(normalized)) {
            return CODEX_RESPONSES;
        }
        if ("anthropic".equalsIgnoreCase(normalized) || "claude".equalsIgnoreCase(normalized)
                || "anthropic_messages".equalsIgnoreCase(normalized)) {
            return ANTHROPIC_MESSAGES;
        }
        if ("local".equalsIgnoreCase(normalized) || "gguf".equalsIgnoreCase(normalized)
                || "local_gguf".equalsIgnoreCase(normalized)) {
            return LOCAL_GGUF;
        }
        for (ModelProtocolType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return OPENAI_COMPATIBLE;
    }
}
