package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.ai.stream.ThinkTagParser;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OpenAiCompatibleProtocol extends AbstractHttpModelProtocol {
    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("messages", messagesJson(messages));
            body.put("temperature", 0.2);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            String raw = postJson(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers);
            JSONObject response = new JSONObject(raw);
            String text = response
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content");
            return new ModelCompletionResponse(text);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("OpenAI 兼容协议解析失败: " + e.getMessage(), e);
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
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("messages", messagesJson(messages, requestOptions.isPreserveReasoning()));
            body.put("temperature", 0.2);
            body.put("stream", true);
            if (!requestOptions.getTools().isEmpty()) {
                body.put("tools", ToolRegistry.toJsonArray(requestOptions.getTools()));
                body.put("tool_choice", "auto");
            }
            applyReasoningRequest(config, body, requestOptions);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            ThinkTagParser thinkTagParser = new ThinkTagParser();
            HashMap<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();

            postJsonSse(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("OpenAI 流式错误: " + event.opt("error"));
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
                    throw new ModelCompletionException("OpenAI 流式错误: 输出被内容安全策略拦截");
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    return;
                }

                JSONArray toolCalls = delta.optJSONArray("tool_calls");
                if (toolCalls != null) {
                    appendToolCallDeltas(toolCallBuilders, toolCalls);
                }

                String reasoningDelta = extractReasoningDelta(delta);
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

            appendParsedDelta(text, reasoning, thinkTagParser.flush(), callback);
            return new ModelCompletionResponse(text.toString(), reasoning.toString(), buildToolCalls(toolCallBuilders));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("OpenAI 兼容协议流式解析失败: " + e.getMessage(), e);
        }
    }

    private JSONArray messagesJson(List<ModelMessage> messages) throws Exception {
        return messagesJson(messages, false);
    }

    private JSONArray messagesJson(List<ModelMessage> messages, boolean preserveReasoning) throws Exception {
        JSONArray array = new JSONArray();
        for (ModelMessage message : messages) {
            JSONObject object = new JSONObject();
            object.put("role", message.getRole());
            if ("tool".equals(message.getRole())) {
                object.put("tool_call_id", message.getToolCallId());
                object.put("content", message.getContent());
                array.put(object);
                continue;
            }
            if ("assistant".equals(message.getRole()) && !message.getToolCalls().isEmpty()) {
                object.put("content", message.getContent().length() == 0 ? JSONObject.NULL : message.getContent());
                JSONArray toolCalls = new JSONArray();
                for (ToolCall call : message.getToolCalls()) {
                    JSONObject function = new JSONObject()
                            .put("name", call.getName())
                            .put("arguments", call.getArguments());
                    toolCalls.put(new JSONObject()
                            .put("id", call.getId())
                            .put("type", "function")
                            .put("function", function));
                }
                object.put("tool_calls", toolCalls);
            } else if ("user".equals(message.getRole()) && ImageInputPayload.fromRawInputJson(message.getRawInputJson()) != null) {
                object.put("content", imageContent(message));
            } else {
                object.put("content", message.getContent());
            }
            if (preserveReasoning
                    && "assistant".equals(message.getRole())
                    && message.getReasoningContent().length() > 0) {
                object.put("reasoning_content", message.getReasoningContent());
            }
            array.put(object);
        }
        return array;
    }

    JSONArray messagesJsonForTest(List<ModelMessage> messages) throws Exception {
        return messagesJson(messages);
    }

    private JSONArray imageContent(ModelMessage message) throws Exception {
        ImageInputPayload.Payload payload = ImageInputPayload.fromRawInputJson(message.getRawInputJson());
        if (payload == null) {
            return new JSONArray().put(new JSONObject().put("type", "text").put("text", message.getContent()));
        }
        String prompt = payload.getPrompt().length() == 0 ? message.getContent() : payload.getPrompt();
        return new JSONArray()
                .put(new JSONObject()
                        .put("type", "text")
                        .put("text", prompt == null ? "" : prompt))
                .put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject()
                                .put("url", payload.dataUrl())));
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
            if (builder == null || builder.name.length() == 0) {
                continue;
            }
            String id = builder.id.length() == 0 ? "call_" + index + "_" + System.currentTimeMillis() : builder.id;
            String args = builder.arguments.length() == 0 ? "{}" : builder.arguments.toString();
            calls.add(new ToolCall(id, builder.name, args));
        }
        return calls;
    }

    private static final class ToolCallBuilder {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
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

    private String extractReasoningDelta(JSONObject delta) {
        if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
            return delta.optString("reasoning_content");
        }
        Object reasoning = delta.opt("reasoning");
        if (reasoning instanceof String) {
            return (String) reasoning;
        }
        if (reasoning instanceof JSONObject) {
            JSONObject object = (JSONObject) reasoning;
            if (object.has("content")) {
                return object.optString("content");
            }
            if (object.has("text")) {
                return object.optString("text");
            }
        }
        Object details = delta.opt("reasoning_details");
        if (details instanceof JSONObject) {
            JSONObject object = (JSONObject) details;
            if (object.has("content")) {
                return object.optString("content");
            }
            if (object.has("text")) {
                return object.optString("text");
            }
            if (object.has("reasoning_content")) {
                return object.optString("reasoning_content");
            }
        }
        if (details instanceof JSONArray) {
            JSONArray array = (JSONArray) details;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof String) {
                    builder.append((String) item);
                } else if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    if (object.has("content")) {
                        builder.append(object.optString("content"));
                    } else if (object.has("text")) {
                        builder.append(object.optString("text"));
                    } else if (object.has("reasoning_content")) {
                        builder.append(object.optString("reasoning_content"));
                    }
                }
            }
            return builder.toString();
        }
        return "";
    }

    private void applyReasoningRequest(ModelConfig config, JSONObject body, ModelRequestOptions options) throws Exception {
        if (!OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config)) {
            return;
        }
        String base = config.getBaseUrl().toLowerCase(java.util.Locale.ROOT);
        String model = ModelContextParser.apiModelId(config.getModelId()).toLowerCase(java.util.Locale.ROOT);
        String effort = options.getReasoningEffort();
        boolean enabled = !AiBehaviorSettings.REASONING_OFF.equals(effort);
        if (base.contains("dashscope") || base.contains("aliyuncs") || model.contains("qwen")) {
            body.put("enable_thinking", enabled);
            if (enabled) {
                body.put("thinking_budget", thinkingBudget(effort));
            }
            if (options.isPreserveReasoning()) {
                body.put("preserve_thinking", true);
            }
            return;
        }
        if (base.contains("minimax") || model.contains("minimax") || model.contains("abab") || model.contains("m2")) {
            body.put("reasoning_split", enabled);
            return;
        }
        if (base.contains("deepseek") || model.contains("deepseek")) {
            body.put("thinking", new JSONObject().put("type", enabled ? "enabled" : "disabled"));
            if (enabled) {
                body.put("reasoning_effort", AiBehaviorSettings.REASONING_MAX.equals(effort) ? "max" : "high");
            }
            return;
        }
        if (base.contains("moonshot") || base.contains("kimi") || model.contains("kimi")
                || base.contains("bigmodel") || base.contains("zhipu") || model.contains("glm")
                || base.contains("mimo") || base.contains("xiaomi") || model.contains("mimo")) {
            JSONObject thinking = new JSONObject().put("type", enabled ? "enabled" : "disabled");
            if (options.isPreserveReasoning() && (base.contains("moonshot") || base.contains("kimi") || model.contains("kimi"))) {
                thinking.put("keep", "all");
            }
            body.put("thinking", thinking);
            if (options.isPreserveReasoning() && (base.contains("bigmodel") || base.contains("zhipu") || model.contains("glm"))) {
                body.put("clear_thinking", false);
            }
            return;
        }
        if (enabled) {
            body.put("reasoning", new JSONObject().put("effort", effort));
        }
    }

    private int thinkingBudget(String effort) {
        if (AiBehaviorSettings.REASONING_LOW.equals(effort)) {
            return 1024;
        }
        if (AiBehaviorSettings.REASONING_HIGH.equals(effort)) {
            return 8192;
        }
        if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
            return 16000;
        }
        return 4096;
    }

    JSONObject reasoningRequestBodyForTest(ModelConfig config, ModelRequestOptions options) throws Exception {
        JSONObject body = new JSONObject();
        applyReasoningRequest(config, body, options == null ? ModelRequestOptions.defaults() : options);
        return body;
    }
}
