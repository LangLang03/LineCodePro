package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import org.json.JSONObject;

public final class KimiReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("moonshot") || baseUrl.contains("kimi") || modelId.contains("kimi")
                || modelId.contains("moonshot");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        JSONObject thinking = new JSONObject().put("type", context.isEnabled() ? "enabled" : "disabled");
        if (context.isPreserveReasoning()) {
            thinking.put("keep", "all");
        }
        body.put("thinking", thinking);
        // Kimi 官方要求 temperature 必须 >= 1.0，低于该值会报错。
        body.put("temperature", Math.max(1.0, body.optDouble("temperature", 0.2)));
    }
}
