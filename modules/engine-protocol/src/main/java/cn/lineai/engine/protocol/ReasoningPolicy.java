package cn.lineai.engine.protocol;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vendor-specific reasoning request fields, in the same first-match order the product has always
 * used: dashscope, minimax, deepseek, kimi, moonshot/glm, openai, then a generic fallback.
 */
public final class ReasoningPolicy {

    private ReasoningPolicy() {
    }

    public static void apply(JSONObject body, ReasoningSpec spec) throws JSONException {
        if (spec == null) {
            return;
        }
        if (spec.baseUrl.contains("integrate.api.nvidia.com") || spec.baseUrl.contains("api.nvidia.com")
                || spec.providerId.contains("nvidia")) {
            return;
        }
        String effort = spec.compatibleEffort();
        boolean enabled = spec.enabled();
        if (dashscope(spec)) {
            body.put("enable_thinking", enabled);
            if (enabled) {
                body.put("thinking_budget", spec.thinkingBudget());
            }
            if (spec.preserve) {
                body.put("preserve_thinking", true);
            }
            return;
        }
        if (minimax(spec)) {
            body.put("thinking", new JSONObject().put("type", enabled ? "adaptive" : "disabled"));
            body.put("reasoning_split", enabled);
            return;
        }
        if (deepseek(spec)) {
            if ("auto".equals(effort)) {
                return;
            }
            body.put("reasoning_effort", deepseekEffort(effort));
            return;
        }
        if (kimi(spec)) {
            JSONObject thinking = new JSONObject().put("type", enabled ? "enabled" : "disabled");
            if (spec.preserve) {
                thinking.put("keep", "all");
            }
            body.put("thinking", thinking);
            body.put("temperature", Math.max(1.0, body.optDouble("temperature", 0.2)));
            return;
        }
        if (moonshot(spec)) {
            body.put("thinking", new JSONObject().put("type", enabled ? "enabled" : "disabled"));
            if (spec.isGlm()) {
                body.put("reasoning_effort", concreteEffort(spec));
                if (spec.preserve) {
                    body.put("clear_thinking", false);
                }
            }
            return;
        }
        if (openAiReasoningModel(spec)) {
            if (enabled) {
                body.put("reasoning_effort", xhigh(concreteEffort(spec)));
            }
            return;
        }
        if (enabled) {
            body.put("reasoning", new JSONObject().put("effort", xhigh(concreteEffort(spec))));
        }
    }

    private static String deepseekEffort(String effort) {
        if ("off".equals(effort)) {
            return "none";
        }
        if ("low".equals(effort)) {
            return "low";
        }
        if ("max".equals(effort)) {
            return "max";
        }
        return "high";
    }

    private static String concreteEffort(ReasoningSpec spec) {
        return spec.effectiveEffort();
    }

    private static String xhigh(String effort) {
        return "max".equals(effort) ? "xhigh" : effort;
    }

    private static boolean dashscope(ReasoningSpec spec) {
        return spec.baseUrl.contains("dashscope") || spec.baseUrl.contains("aliyuncs")
                || spec.modelId.contains("qwen");
    }

    private static boolean minimax(ReasoningSpec spec) {
        return spec.baseUrl.contains("minimax") || spec.modelId.contains("minimax")
                || spec.modelId.contains("abab") || spec.modelId.contains("m2");
    }

    private static boolean deepseek(ReasoningSpec spec) {
        return spec.baseUrl.contains("deepseek") || spec.modelId.contains("deepseek");
    }

    private static boolean kimi(ReasoningSpec spec) {
        return spec.baseUrl.contains("moonshot") || spec.baseUrl.contains("kimi")
                || spec.modelId.contains("kimi") || spec.modelId.contains("moonshot");
    }

    private static boolean moonshot(ReasoningSpec spec) {
        return spec.baseUrl.contains("bigmodel") || spec.baseUrl.contains("zhipu")
                || spec.modelId.contains("glm") || spec.baseUrl.contains("mimo")
                || spec.baseUrl.contains("xiaomi") || spec.modelId.contains("mimo");
    }

    private static boolean openAiReasoningModel(ReasoningSpec spec) {
        return spec.modelId.startsWith("gpt-5") || spec.modelId.startsWith("o1")
                || spec.modelId.startsWith("o3") || spec.modelId.startsWith("o4");
    }
}
