package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CodexResponsesProtocol extends AbstractHttpModelProtocol {
    static final String CODEX_PROTOCOL_VERSION = "0.120.0";
    static final String CODEX_ORIGINATOR = "codex_cli_rs";
    private static final String CODEX_INSTALLATION_ID = UUID.nameUUIDFromBytes(
            "cn.lineai.linecode.codex".getBytes(StandardCharsets.UTF_8)
    ).toString();
    private static final String CODEX_WINDOW_ID = CODEX_INSTALLATION_ID + ":0";

    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("input", ResponsesInputBuilder.inputJson(messages));
            body.put("tools", new JSONArray());
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", true);
            body.put("store", isAzureResponsesEndpoint(config.getBaseUrl()));
            body.put("include", new JSONArray());
            putInstructions(body, messages);
            putCodexClientFields(body, config);

            HashMap<String, String> headers = codexHeaders(config.getApiKey());
            String raw = postJson(responsesEndpoint(config.getBaseUrl()), body, headers);
            JSONObject response = new JSONObject(raw);
            StringBuilder text = new StringBuilder(response.optString("output_text"));
            StringBuilder reasoning = new StringBuilder();
            LinkedHashMap<String, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
            mergeOutputArray(response.optJSONArray("output"), text, reasoning, toolCallBuilders, new HashMap<>(), null);
            return new ModelCompletionResponse(text.toString(), reasoning.toString(), buildToolCalls(toolCallBuilders));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex Responses 协议解析失败: " + e.getMessage(), e);
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
            body.put("input", ResponsesInputBuilder.inputJson(messages));
            body.put("stream", true);
            body.put("parallel_tool_calls", true);
            body.put("store", isAzureResponsesEndpoint(config.getBaseUrl()));
            JSONArray include = new JSONArray();
            putInstructions(body, messages);
            JSONArray tools = toolsJson(requestOptions.getTools());
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            if (!AiBehaviorSettings.REASONING_OFF.equals(requestOptions.getReasoningEffort())) {
                body.put("reasoning", new JSONObject()
                        .put("effort", AiBehaviorSettings.REASONING_MAX.equals(requestOptions.getReasoningEffort()) ? "high" : requestOptions.getReasoningEffort())
                        .put("summary", "auto"));
                include.put("reasoning.encrypted_content");
            }
            body.put("include", include);
            putCodexClientFields(body, config);

            HashMap<String, String> headers = codexHeaders(config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            LinkedHashMap<String, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
            HashMap<String, StringBuilder> customToolInputs = new HashMap<>();

            postJsonSse(responsesEndpoint(config.getBaseUrl()), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("Codex 流式错误: " + event.opt("error"));
                }
                String type = event.optString("type");
                if (type.length() == 0) {
                    type = eventType == null ? "" : eventType;
                }

                if ("response.custom_tool_call_input.delta".equals(type) && event.has("delta")) {
                    appendCustomToolInput(customToolInputs, event.optString("item_id"), event.optString("delta"));
                    appendCustomToolInput(customToolInputs, event.optString("call_id"), event.optString("delta"));
                    return;
                }

                if ("response.function_call_arguments.delta".equals(type) && event.has("delta")) {
                    appendFunctionArgumentsDelta(toolCallBuilders, event, event.optString("delta"));
                    return;
                }

                if ("response.output_text.delta".equals(type) && event.has("delta")) {
                    String delta = event.optString("delta");
                    text.append(delta);
                    if (callback != null) {
                        callback.onTextDelta(delta);
                    }
                    return;
                }

                if ("response.output_text.done".equals(type)) {
                    appendFinalIfMissing(text, event.optString("text", event.optString("delta")), false, callback);
                    return;
                }

                if (("response.reasoning_summary_text.delta".equals(type) || "response.reasoning_text.delta".equals(type))
                        && event.has("delta")) {
                    String delta = event.optString("delta");
                    reasoning.append(delta);
                    if (callback != null) {
                        callback.onReasoningDelta(delta);
                    }
                    return;
                }

                if ("response.reasoning_summary_part.added".equals(type) && reasoning.length() > 0) {
                    reasoning.append('\n');
                    if (callback != null) {
                        callback.onReasoningDelta("\n");
                    }
                    return;
                }

                if ("response.completed".equals(type)) {
                    JSONObject response = event.optJSONObject("response");
                    if (response != null) {
                        mergeOutputArray(response.optJSONArray("output"), text, reasoning, toolCallBuilders, customToolInputs, callback);
                    }
                    throw new SseStreamCompleteException();
                } else if ("response.output_item.done".equals(type) && event.has("response")) {
                    JSONObject response = event.optJSONObject("response");
                    if (response != null) {
                        mergeOutputArray(response.optJSONArray("output"), text, reasoning, toolCallBuilders, customToolInputs, callback);
                    }
                } else if (("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) && event.has("item")) {
                    mergeOutputItem(event.optJSONObject("item"), text, reasoning, toolCallBuilders, customToolInputs, callback);
                } else if ("response.failed".equals(type)) {
                    throw new ModelCompletionException("Codex response.failed: " + event.toString());
                } else if ("response.incomplete".equals(type)) {
                    JSONObject response = event.optJSONObject("response");
                    JSONObject details = response == null ? null : response.optJSONObject("incomplete_details");
                    String reason = details == null ? "unknown" : details.optString("reason", "unknown");
                    throw new ModelCompletionException("Codex response.incomplete: " + reason);
                }
            });

            return new ModelCompletionResponse(text.toString(), reasoning.toString(), buildToolCalls(toolCallBuilders));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex Responses 协议流式解析失败: " + e.getMessage(), e);
        }
    }

    private HashMap<String, String> codexHeaders(String apiKey) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("version", CODEX_PROTOCOL_VERSION);
        headers.put("originator", CODEX_ORIGINATOR);
        headers.put("User-Agent", codexUserAgent());
        return headers;
    }

    static String codexUserAgent() {
        return CODEX_ORIGINATOR + "/" + CODEX_PROTOCOL_VERSION + " (Android; LineCode)";
    }

    private void putCodexClientFields(JSONObject body, ModelConfig config) throws Exception {
        body.put("prompt_cache_key", promptCacheKey(config));
        body.put("client_metadata", new JSONObject()
                .put("x-codex-installation-id", CODEX_INSTALLATION_ID)
                .put("x-codex-window-id", CODEX_WINDOW_ID));
    }

    private String promptCacheKey(ModelConfig config) {
        String model = config == null ? "" : ModelContextParser.apiModelId(config.getModelId());
        return "linecode-codex-" + Integer.toHexString(model.hashCode());
    }

    private boolean isAzureResponsesEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        String normalized = baseUrl.toLowerCase(java.util.Locale.US);
        return normalized.contains("openai.azure.")
                || normalized.contains("cognitiveservices.azure.")
                || normalized.contains("aoai.azure.")
                || normalized.contains("azure-api.")
                || normalized.contains("azurefd.")
                || normalized.contains("windows.net/openai");
    }

    private String responsesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/responses";
        }
        return endpoint(base, "/responses");
    }

    private void putInstructions(JSONObject body, List<ModelMessage> messages) throws Exception {
        String instructions = ResponsesInputBuilder.instructions(messages);
        if (instructions.length() > 0) {
            body.put("instructions", instructions);
        }
    }

    private JSONArray toolsJson(List<BaseTool> tools) throws Exception {
        JSONArray array = new JSONArray();
        JSONArray openAiTools = ToolRegistry.toJsonArray(tools);
        for (int i = 0; i < openAiTools.length(); i++) {
            JSONObject tool = openAiTools.optJSONObject(i);
            if (tool == null) {
                continue;
            }
            JSONObject function = tool.optJSONObject("function");
            if ("function".equals(tool.optString("type")) && function != null) {
                array.put(new JSONObject()
                        .put("type", "function")
                        .put("name", function.optString("name"))
                        .put("description", function.optString("description"))
                        .put("strict", false)
                        .put("parameters", function.optJSONObject("parameters") == null
                                ? new JSONObject().put("type", "object").put("properties", new JSONObject())
                                : function.optJSONObject("parameters")));
            } else {
                array.put(tool);
            }
        }
        return array;
    }

    private void mergeOutputArray(
            JSONArray output,
            StringBuilder text,
            StringBuilder reasoning,
            LinkedHashMap<String, ToolCallBuilder> toolCallBuilders,
            Map<String, StringBuilder> customToolInputs,
            ModelStreamCallback callback
    ) {
        if (output == null) {
            return;
        }
        for (int i = 0; i < output.length(); i++) {
            mergeOutputItem(output.optJSONObject(i), text, reasoning, toolCallBuilders, customToolInputs, callback);
        }
    }

    private void mergeOutputItem(
            JSONObject item,
            StringBuilder text,
            StringBuilder reasoning,
            LinkedHashMap<String, ToolCallBuilder> toolCallBuilders,
            Map<String, StringBuilder> customToolInputs,
            ModelStreamCallback callback
    ) {
        if (item == null) {
            return;
        }
        String itemType = item.optString("type");
        if ("function_call".equals(itemType)) {
            upsertToolCall(toolCallBuilders,
                    item.optString("call_id", item.optString("id")),
                    item.optString("name"),
                    argumentsString(item.opt("arguments")),
                    true);
            return;
        }
        if ("custom_tool_call".equals(itemType)) {
            String callId = item.optString("call_id", item.optString("id"));
            String input = item.has("input")
                    ? argumentsString(item.opt("input"))
                    : customInput(customToolInputs, callId, item.optString("id"));
            upsertToolCall(toolCallBuilders, callId, item.optString("name"), input, true);
            return;
        }
        if ("reasoning".equals(itemType)) {
            String next = extractReasoningText(item);
            appendFinalIfMissing(reasoning, next, true, callback);
            return;
        }
        JSONArray content = item.optJSONArray("content");
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = block.optString("type");
            if ("output_text".equals(type) || "text".equals(type)) {
                appendFinalIfMissing(text, block.optString("text"), false, callback);
            } else if ("reasoning_text".equals(type) || "summary_text".equals(type)) {
                appendFinalIfMissing(reasoning, block.optString("text"), true, callback);
            }
        }
    }

    private String extractReasoningText(JSONObject item) {
        StringBuilder builder = new StringBuilder();
        JSONArray summary = item.optJSONArray("summary");
        if (summary != null) {
            for (int i = 0; i < summary.length(); i++) {
                JSONObject part = summary.optJSONObject(i);
                if (part != null) {
                    builder.append(part.optString("text"));
                }
            }
        }
        if (builder.length() == 0) {
            builder.append(item.optString("content"));
        }
        return builder.toString();
    }

    private void appendCustomToolInput(Map<String, StringBuilder> customToolInputs, String id, String delta) {
        if (id == null || id.length() == 0 || delta == null || delta.length() == 0) {
            return;
        }
        StringBuilder builder = customToolInputs.get(id);
        if (builder == null) {
            builder = new StringBuilder();
            customToolInputs.put(id, builder);
        }
        builder.append(delta);
    }

    private void appendFunctionArgumentsDelta(
            LinkedHashMap<String, ToolCallBuilder> toolCallBuilders,
            JSONObject event,
            String delta
    ) {
        if (delta == null || delta.length() == 0) {
            return;
        }
        String callId = event.optString("call_id");
        if (callId.length() == 0) {
            callId = event.optString("item_id", event.optString("output_index"));
        }
        if (callId.length() == 0) {
            callId = "call_" + toolCallBuilders.size();
        }
        ToolCallBuilder builder = toolCallBuilders.get(callId);
        if (builder == null) {
            builder = new ToolCallBuilder(callId);
            toolCallBuilders.put(callId, builder);
        }
        builder.arguments.append(delta);
    }

    private void upsertToolCall(
            LinkedHashMap<String, ToolCallBuilder> toolCallBuilders,
            String callId,
            String name,
            String arguments,
            boolean replaceArguments
    ) {
        if (callId == null || callId.length() == 0) {
            callId = "call_" + toolCallBuilders.size() + "_" + System.currentTimeMillis();
        }
        ToolCallBuilder builder = toolCallBuilders.get(callId);
        if (builder == null) {
            builder = new ToolCallBuilder(callId);
            toolCallBuilders.put(callId, builder);
        }
        if (name != null && name.length() > 0) {
            builder.name = name;
        }
        if (arguments != null) {
            if (replaceArguments) {
                builder.arguments.setLength(0);
            }
            builder.arguments.append(arguments);
        }
    }

    private String argumentsString(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return "{}";
        }
        if (value instanceof String) {
            String text = (String) value;
            return text.length() == 0 ? "{}" : text;
        }
        return String.valueOf(value);
    }

    private String customInput(Map<String, StringBuilder> customToolInputs, String callId, String itemId) {
        StringBuilder byCallId = customToolInputs.get(callId);
        if (byCallId != null) {
            return byCallId.toString();
        }
        StringBuilder byItemId = customToolInputs.get(itemId);
        return byItemId == null ? "" : byItemId.toString();
    }

    private List<ToolCall> buildToolCalls(LinkedHashMap<String, ToolCallBuilder> toolCallBuilders) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (ToolCallBuilder builder : toolCallBuilders.values()) {
            if (builder == null || builder.name.length() == 0) {
                continue;
            }
            calls.add(new ToolCall(
                    builder.callId,
                    builder.name,
                    builder.arguments.length() == 0 ? "{}" : builder.arguments.toString()
            ));
        }
        return calls;
    }

    private void appendFinalIfMissing(
            StringBuilder target,
            String next,
            boolean thinking,
            ModelStreamCallback callback
    ) {
        if (next == null || next.length() == 0) {
            return;
        }
        String delta = next;
        String current = target.toString();
        if (current.contains(next)) {
            return;
        }
        if (current.length() > 0 && next.startsWith(current)) {
            delta = next.substring(current.length());
        }
        if (delta.length() == 0) {
            return;
        }
        target.append(delta);
        if (callback == null) {
            return;
        }
        if (thinking) {
            callback.onReasoningDelta(delta);
        } else {
            callback.onTextDelta(delta);
        }
    }

    private static final class ToolCallBuilder {
        private final String callId;
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();

        ToolCallBuilder(String callId) {
            this.callId = callId == null ? "" : callId;
        }
    }
}
