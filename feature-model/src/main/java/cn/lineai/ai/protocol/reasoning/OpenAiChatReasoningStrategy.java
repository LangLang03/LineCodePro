package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;

/** OpenAI Chat Completions exposes reasoning effort as a top-level request field. */
public final class OpenAiChatReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return modelId.startsWith("gpt-5")
                || modelId.startsWith("o1")
                || modelId.startsWith("o3")
                || modelId.startsWith("o4");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        if (!context.isEnabled()) {
            return;
        }
        String effort = AiBehaviorSettings.concreteReasoningEffort(context.getEffort());
        if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
            effort = "xhigh";
        }
        body.put("reasoning_effort", effort);
    }
}
