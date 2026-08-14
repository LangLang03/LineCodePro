package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import org.json.JSONObject;

public final class MoonshotReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("bigmodel") || baseUrl.contains("zhipu") || modelId.contains("glm")
                || baseUrl.contains("mimo") || baseUrl.contains("xiaomi") || modelId.contains("mimo");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        JSONObject thinking = new JSONObject().put("type", context.isEnabled() ? "enabled" : "disabled");
        String base = context.getBaseUrl();
        String model = context.getModelId();
        body.put("thinking", thinking);
        if (context.isPreserveReasoning() && (base.contains("bigmodel") || base.contains("zhipu") || model.contains("glm"))) {
            body.put("clear_thinking", false);
        }
    }
}
