package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.ai.stream.ThinkTagParser;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.ai.protocol.reasoning.DashscopeReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.DeepseekReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.DefaultReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.KimiReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MinimaxReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MoonshotReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.OpenAiChatReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.ReasoningDeltaExtractor;
import cn.lineai.tool.ToolInfo;
import cn.lineai.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OpenAiCompatibleProtocol extends AbstractHttpModelProtocol {

    private final OpenAiMessageSerializer messageSerializer = new OpenAiMessageSerializer();
    private final ReasoningStrategyRegistry reasoningStrategyRegistry = createDefaultRegistry();

    private static ReasoningStrategyRegistry createDefaultRegistry() {
        ReasoningStrategyRegistry registry = new ReasoningStrategyRegistry();
        registry.register(new DashscopeReasoningStrategy());
        registry.register(new MinimaxReasoningStrategy());
        registry.register(new DeepseekReasoningStrategy());
        registry.register(new KimiReasoningStrategy());
        registry.register(new MoonshotReasoningStrategy());
        registry.register(new OpenAiChatReasoningStrategy());
        registry.register(new DefaultReasoningStrategy());
        return registry;
    }

    @Override
    public boolean supportsNativeTools(ModelConfig model) {
        return OpenAiCompatibleCapabilities.supportsNativeTools(model);
    }

    @Override
    public boolean supportsDedicatedCompression() {
        return true;
    }

    @Override
    public boolean supportsImageGeneration() {
        return true;
    }

    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        String raw = "";
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config));
            body.put("messages", messageSerializer.messagesJson(messages));
            body.put("temperature", 0.2);
            applyReasoningRequest(config, body, ModelRequestOptions.defaults());

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            raw = postJson(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers);
            JSONObject response = new JSONObject(raw);
            String text = response
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content");
            JSONObject usage = response.optJSONObject("usage");
            int inputTokens = usage == null ? 0 : usage.optInt("prompt_tokens", 0);
            int outputTokens = usage == null ? 0 : usage.optInt("completion_tokens", 0);
            return new ModelCompletionResponse(text, "", java.util.Collections.emptyList(), inputTokens, outputTokens);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            logParseError("parse_openai_complete", raw, e);
            throw new ModelCompletionException("OpenAI compatible protocol parse failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        try {
            ModelRequestOptions requestOptions = options == null ? ModelRequestOptions.defaults() : options;
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config));
            body.put("messages", messageSerializer.messagesJson(messages, requestOptions.isPreserveReasoning()));
            body.put("temperature", 0.2);
            body.put("stream", true);
            if (!requestOptions.getTools().isEmpty()) {
                body.put("tools", ToolInfo.toJsonArray(requestOptions.getTools()));
                body.put("tool_choice", "auto");
            }
            applyReasoningRequest(config, body, requestOptions);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            ReasoningDeltaExtractor reasoningDeltaExtractor = new ReasoningDeltaExtractor();
            ThinkTagParser thinkTagParser = new ThinkTagParser();
            HashMap<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();
            final int[] usageInputTokens = new int[1];
            final int[] usageOutputTokens = new int[1];

            postJsonSse(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("OpenAI stream error: " + describeError(event.opt("error")));
                }
                // 部分兼容端点会在最后一个 chunk（含空 choices）携带 usage，先于 choices 处理。
                JSONObject usage = event.optJSONObject("usage");
                if (usage != null) {
                    usageInputTokens[0] = Math.max(usageInputTokens[0], usage.optInt("prompt_tokens", 0));
                    usageOutputTokens[0] = Math.max(usageOutputTokens[0], usage.optInt("completion_tokens", 0));
                }
                JSONArray choices = event.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    return;
                }
                JSONObject choice = choices.optJSONObject(0);
                if (choice == null) {
                    return;
                }
                if ("content_filter".equals(choice.optString("finish_reason"))) {
                    throw new ModelCompletionException("OpenAI stream error: output blocked by content safety policy");
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    return;
                }

                JSONArray toolCalls = delta.optJSONArray("tool_calls");
                if (toolCalls != null) {
                    appendToolCallDeltas(toolCallBuilders, toolCalls);
                }

                String reasoningDelta = reasoningDeltaExtractor.extract(delta);
                if (reasoningDelta.length() > 0) {
                    reasoning.append(reasoningDelta);
                    if (callback != null) {
                        callback.onReasoningDelta(reasoningDelta);
                    }
                }

                if (delta.has("content") && !delta.isNull("content")) {
                    ThinkTagParser.Result parsed = thinkTagParser.append(delta.optString("content"));
                    appendParsedDelta(text, reasoning, parsed, callback);
                }
            });
            String trailingReasoning = reasoningDeltaExtractor.flush();
            if (trailingReasoning.length() > 0) {
                reasoning.append(trailingReasoning);
                if (callback != null) {
                    callback.onReasoningDelta(trailingReasoning);
                }
            }

            appendParsedDelta(text, reasoning, thinkTagParser.flush(), callback);
            return new ModelCompletionResponse(
                    text.toString(),
                    reasoning.toString(),
                    buildToolCalls(toolCallBuilders),
                    usageInputTokens[0],
                    usageOutputTokens[0]
            );
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("OpenAI compatible protocol stream parse failed: " + e.getMessage(), e);
        }
    }

    private void appendToolCallDeltas(Map<Integer, ToolCallBuilder> builders, JSONArray toolCalls) {
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject item = toolCalls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int index = item.optInt("index", i);
            ToolCallBuilder builder = builders.get(index);
            if (builder == null) {
                builder = new ToolCallBuilder();
                builders.put(index, builder);
            }
            if (item.has("id") && !item.isNull("id")) {
                String id = item.optString("id");
                if (id.length() > 0) {
                    builder.id = id;
                }
            }
            JSONObject function = item.optJSONObject("function");
            if (function == null) {
                continue;
            }
            if (function.has("name") && !function.isNull("name")) {
                String name = function.optString("name");
                if (name.length() > 0) {
                    builder.name = name;
                }
            }
            if (function.has("arguments") && !function.isNull("arguments")) {
                builder.arguments.append(function.optString("arguments"));
            }
        }
    }

    private List<ToolCall> buildToolCalls(Map<Integer, ToolCallBuilder> builders) {
        ArrayList<Integer> indexes = new ArrayList<>(builders.keySet());
        indexes.sort(Integer::compareTo);
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (Integer index : indexes) {
            ToolCallBuilder builder = builders.get(index);
            if (builder == null || !builder.hasName()) {
                continue;
            }
            calls.add(builder.build(index));
        }
        return calls;
    }

    private static final class ToolCallBuilder extends AbstractToolCallBuilder {
    }

    private void appendParsedDelta(
            StringBuilder text,
            StringBuilder reasoning,
            ThinkTagParser.Result parsed,
            ModelStreamCallback callback
    ) {
        if (parsed.getThinking().length() > 0) {
            reasoning.append(parsed.getThinking());
            if (callback != null) {
                callback.onReasoningDelta(parsed.getThinking());
            }
        }
        if (parsed.getText().length() > 0) {
            text.append(parsed.getText());
            if (callback != null) {
                callback.onTextDelta(parsed.getText());
            }
        }
    }

    private void applyReasoningRequest(ModelConfig config, JSONObject body, ModelRequestOptions options) throws Exception {
        if (!OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config)) {
            return;
        }
        String base = config.getBaseUrl().toLowerCase(java.util.Locale.ROOT);
        String model = ModelContextParser.apiModelId(config).toLowerCase(java.util.Locale.ROOT);
        String effort = options.getReasoningEffort();
        boolean enabled = AiBehaviorSettings.isReasoningEnabled(effort);
        String concrete = AiBehaviorSettings.concreteReasoningEffort(effort);
        ReasoningRequestContext context = new ReasoningRequestContext(
                enabled, concrete, options.isPreserveReasoning(), base, model, thinkingBudget(concrete));
        ReasoningRequestStrategy strategy = reasoningStrategyRegistry.find(base, model);
        if (strategy != null) {
            strategy.apply(body, context);
        }
    }

    JSONObject reasoningRequestBodyForTest(ModelConfig config, ModelRequestOptions options) throws Exception {
        JSONObject body = new JSONObject();
        applyReasoningRequest(config, body, options == null ? ModelRequestOptions.defaults() : options);
        return body;
    }

    /**
     * 把 SSE 错误字段可读地转为文本。{@code error} 可能是字符串或 JSON 对象：
     * 对象时直接 {@code JSONObject.toString()} 会把中文转义成 {@code \\uXXXX}，
     * 因此优先读取 {@code message}/{@code type} 字段，最后统一做一次 Unicode 转义解码。
     */
    private static String describeError(Object error) {
        if (error == null) {
            return "";
        }
        if (error instanceof JSONObject) {
            JSONObject obj = (JSONObject) error;
            String message = obj.optString("message");
            if (message != null && message.length() > 0) {
                return StringUtils.decodeUnicodeEscapes(message);
            }
            String type = obj.optString("type");
            if (type != null && type.length() > 0) {
                return StringUtils.decodeUnicodeEscapes(type);
            }
            return StringUtils.decodeUnicodeEscapes(obj.optString("code"));
        }
        return StringUtils.decodeUnicodeEscapes(error.toString());
    }
}
