package cn.lineai.model;

public final class ModelContextInfo {
    private final String apiModelId;
    private final int contextTokens;
    private final String contextLabel;

    public ModelContextInfo(String apiModelId, int contextTokens, String contextLabel) {
        this.apiModelId = apiModelId == null ? "" : apiModelId;
        this.contextTokens = contextTokens;
        this.contextLabel = contextLabel == null ? "" : contextLabel;
    }

    public String getApiModelId() {
        return apiModelId;
    }

    public int getContextTokens() {
        return contextTokens;
    }

    public String getContextLabel() {
        return contextLabel;
    }
}
