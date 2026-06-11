package cn.lineai.context;

public final class ContextSnapshot {
    private final int usedTokens;
    private final int maxTokens;
    private final int percent;

    public ContextSnapshot(int usedTokens, int maxTokens) {
        this.usedTokens = Math.max(0, usedTokens);
        this.maxTokens = Math.max(1, maxTokens);
        this.percent = Math.min(100, Math.max(0, (int) Math.round((this.usedTokens * 100d) / this.maxTokens)));
    }

    public int getUsedTokens() {
        return usedTokens;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getPercent() {
        return percent;
    }
}
