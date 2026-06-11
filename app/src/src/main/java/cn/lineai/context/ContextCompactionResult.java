package cn.lineai.context;

public final class ContextCompactionResult {
    private final String summaryContent;
    private final String responseInputItemJson;

    public ContextCompactionResult(String summaryContent, String responseInputItemJson) {
        this.summaryContent = summaryContent == null ? "" : summaryContent;
        this.responseInputItemJson = responseInputItemJson == null ? "" : responseInputItemJson.trim();
    }

    public String getSummaryContent() {
        return summaryContent;
    }

    public String getResponseInputItemJson() {
        return responseInputItemJson;
    }
}
