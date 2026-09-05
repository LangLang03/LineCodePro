package cn.lineai.ai;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import java.util.Locale;

/** Maps the app's shared reasoning levels to the subset accepted by each provider. */
public final class ReasoningCompatibility {
    private ReasoningCompatibility() {
    }

    public static ModelRequestOptions adapt(ModelConfig config, ModelRequestOptions options) {
        ModelRequestOptions source = options == null ? ModelRequestOptions.defaults() : options;
        String effort = compatibleEffort(config, source.getReasoningEffort());
        if (effort.equals(source.getReasoningEffort())) {
            return source;
        }
        return new ModelRequestOptions(effort, source.isPreserveReasoning(), source.getTools());
    }

    public static String compatibleEffort(ModelConfig config, String requestedEffort) {
        String effort = AiBehaviorSettings.normalizeReasoningEffort(requestedEffort);
        if (!isGlm(config)) {
            return effort;
        }
        if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
            return AiBehaviorSettings.REASONING_MAX;
        }
        if (AiBehaviorSettings.REASONING_OFF.equals(effort)
                || AiBehaviorSettings.REASONING_LOW.equals(effort)) {
            return AiBehaviorSettings.REASONING_LOW;
        }
        return AiBehaviorSettings.REASONING_HIGH;
    }

    public static boolean isGlm(ModelConfig config) {
        if (config == null) {
            return false;
        }
        String baseUrl = lower(config.getBaseUrl());
        String provider = lower(config.getProviderLabel());
        String model = lower(ModelContextParser.apiModelId(config));
        return baseUrl.contains("bigmodel")
                || baseUrl.contains("zhipu")
                || provider.contains("zhipu")
                || provider.contains("glm")
                || provider.contains("智谱")
                || model.contains("glm");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
