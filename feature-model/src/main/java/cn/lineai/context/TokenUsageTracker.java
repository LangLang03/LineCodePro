package cn.lineai.context;

import cn.lineai.ai.ModelCompletionResponse;

/**
 * 会话级 token 用量追踪（codex 式触发统计）。
 * <p>
 * 记录最近一次模型响应中服务器观测到的 usage（input/output tokens）。
 * 触发压缩时用 {@link #lastInputTokens()} 代表当前活跃上下文大小：服务器返回的
 * input tokens 已包含 system prompt、工具定义与全部历史消息，比本地 chars/4 估算
 * 更接近真实占用。协议未返回 usage 时保持 0，触发逻辑回退到本地估算。
 */
public final class TokenUsageTracker {
    private int lastInputTokens;
    private int lastOutputTokens;

    public synchronized void record(ModelCompletionResponse response) {
        if (response == null) {
            return;
        }
        if (response.getInputTokens() > 0) {
            this.lastInputTokens = response.getInputTokens();
        }
        if (response.getOutputTokens() > 0) {
            this.lastOutputTokens = response.getOutputTokens();
        }
    }

    public synchronized int lastInputTokens() {
        return lastInputTokens;
    }

    public synchronized int lastOutputTokens() {
        return lastOutputTokens;
    }

    public synchronized void reset() {
        this.lastInputTokens = 0;
        this.lastOutputTokens = 0;
    }
}
