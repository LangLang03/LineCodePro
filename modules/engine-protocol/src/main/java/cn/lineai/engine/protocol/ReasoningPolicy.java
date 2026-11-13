package cn.lineai.engine.protocol;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vendor-specific reasoning request fields, in the same first-match order the product has always
 * used: dashscope, minimax, deepseek, kimi, moonshot/glm, openai, then a generic fallback.
 *
 * <p>NVIDIA endpoints are excluded outright because they reject unknown reasoning fields. Exactly
 * one branch fires per request, so the body must not already carry reasoning fields from another
 * policy.</p>
 */
public final class ReasoningPolicy {

    private ReasoningPolicy() {
    }

    /**
     * Writes vendor-specific reasoning fields into a request body, mutating it in place.
     *
     * <p>Providers are detected from the spec's base URL, provider and model identifiers in the
     * fixed first-match order documented on the class; the first match decides which fields are
     * emitted and no other branch runs.</p>
     *
     * @param body request body to mutate
     * @param spec requested reasoning behaviour; ignored when null
     * @throws JSONException when the body rejects an inserted value
     */
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

    /** Maps effort to DeepSeek's levels, where {@code off} is written as {@code none}. */
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

    /** Resolves the concrete effort after {@code auto} collapsing. */
    private static String concreteEffort(ReasoningSpec spec) {
        return spec.effectiveEffort();
    }

    /** Promotes {@code max} to {@code xhigh} for providers that use that spelling. */
    private static String xhigh(String effort) {
        return "max".equals(effort) ? "xhigh" : effort;
    }

    /** Matches Qwen/DashScope deployments by host or model name. */
    private static boolean dashscope(ReasoningSpec spec) {
        return spec.baseUrl.contains("dashscope") || spec.baseUrl.contains("aliyuncs")
                || spec.modelId.contains("qwen");
    }

    /** Matches MiniMax deployments by host or model name, including the M2 series. */
    private static boolean minimax(ReasoningSpec spec) {
        return spec.baseUrl.contains("minimax") || spec.modelId.contains("minimax")
                || spec.modelId.contains("abab") || spec.modelId.contains("m2");
    }

    /** Matches DeepSeek deployments by host or model name. */
    private static boolean deepseek(ReasoningSpec spec) {
        return spec.baseUrl.contains("deepseek") || spec.modelId.contains("deepseek");
    }

    /** Matches Kimi/Moonshot deployments by host or model name. */
    private static boolean kimi(ReasoningSpec spec) {
        return spec.baseUrl.contains("moonshot") || spec.baseUrl.contains("kimi")
                || spec.modelId.contains("kimi") || spec.modelId.contains("moonshot");
    }

    /** Matches GLM, MiMo and Xiaomi deployments, which share the moonshot-style thinking field. */
    private static boolean moonshot(ReasoningSpec spec) {
        return spec.baseUrl.contains("bigmodel") || spec.baseUrl.contains("zhipu")
                || spec.modelId.contains("glm") || spec.baseUrl.contains("mimo")
                || spec.baseUrl.contains("xiaomi") || spec.modelId.contains("mimo");
    }

    /** Matches OpenAI model families that accept a bare reasoning_effort field. */
    private static boolean openAiReasoningModel(ReasoningSpec spec) {
        return spec.modelId.startsWith("gpt-5") || spec.modelId.startsWith("o1")
                || spec.modelId.startsWith("o3") || spec.modelId.startsWith("o4");
    }
}
