package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;

public final class DefaultReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return true;
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        if (context.isEnabled()) {
            String effort = AiBehaviorSettings.concreteReasoningEffort(context.getEffort());
            if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
                effort = "xhigh";
            }
            body.put("reasoning", new JSONObject().put("effort", effort));
        }
    }
}
