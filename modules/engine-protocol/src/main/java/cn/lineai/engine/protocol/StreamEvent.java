package cn.lineai.engine.protocol;

import java.util.Collections;
import java.util.List;

/** One parsed streaming chunk. Immutable; empty strings mean "absent". */
public final class StreamEvent {

    public final String text;
    public final String reasoning;
    public final String finishReason;
    public final int promptTokens;
    public final int completionTokens;
    public final List<ToolCallDraft> calls;

    private StreamEvent(String text, String reasoning, String finishReason, int promptTokens,
                        int completionTokens, List<ToolCallDraft> calls) {
        this.text = text;
        this.reasoning = reasoning;
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.calls = calls == null ? Collections.<ToolCallDraft>emptyList() : calls;
    }

    public static StreamEvent text(String value) {
        return new StreamEvent(value, "", "", 0, 0, null);
    }

    public static StreamEvent reasoning(String value) {
        return new StreamEvent("", value, "", 0, 0, null);
    }

    public static StreamEvent of(String text, String reasoning, String finishReason,
                                 int promptTokens, int completionTokens) {
        return new StreamEvent(text, reasoning, finishReason, promptTokens, completionTokens, null);
    }

    public static StreamEvent calls(List<ToolCallDraft> calls) {
        return new StreamEvent("", "", "", 0, 0, calls);
    }

    public static StreamEvent of(String text, String reasoning, String finishReason,
                                 int promptTokens, int completionTokens,
                                 List<ToolCallDraft> calls) {
        return new StreamEvent(text, reasoning, finishReason, promptTokens, completionTokens, calls);
    }

    public boolean isEmpty() {
        return text.isEmpty() && reasoning.isEmpty() && finishReason.isEmpty() && calls.isEmpty();
    }
}
