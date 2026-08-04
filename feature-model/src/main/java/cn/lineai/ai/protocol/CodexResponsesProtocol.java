package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class CodexResponsesProtocol extends AbstractHttpModelProtocol {

    private final CodexRequestBuilder requestBuilder = new CodexRequestBuilder();
    private final CodexOutputMerger outputMerger = new CodexOutputMerger();

    @Override
    public boolean supportsNativeTools(ModelConfig model) {
        return true;
    }

    @Override
    public boolean supportsDedicatedCompression() {
        return true;
    }

    @Override
    public boolean supportsImageGeneration() {
        return true;
    }

    static final String CODEX_PROTOCOL_VERSION = CodexRequestBuilder.CODEX_PROTOCOL_VERSION;
    static final String CODEX_ORIGINATOR = CodexRequestBuilder.CODEX_ORIGINATOR;

    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        String raw = "";
        try {
            JSONObject body = requestBuilder.buildCompleteBody(config, messages);
            HashMap<String, String> headers = requestBuilder.codexHeaders(config.getApiKey());
            raw = postJson(requestBuilder.responsesEndpoint(config.getBaseUrl()), body, headers);
            JSONObject response = new JSONObject(raw);
            StringBuilder text = new StringBuilder(response.optString("output_text"));
            StringBuilder reasoning = new StringBuilder();
            LinkedHashMap<String, CodexOutputMerger.ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
            outputMerger.mergeOutputArray(response.optJSONArray("output"), text, reasoning, toolCallBuilders, new HashMap<>(), null);
            return new ModelCompletionResponse(text.toString(), reasoning.toString(), outputMerger.buildToolCalls(toolCallBuilders));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            logParseError("parse_codex_complete", raw, e);
            throw new ModelCompletionException("Codex Responses protocol parse failed: " + e.getMessage(), e);
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
            JSONObject body = requestBuilder.buildRequestBody(config, messages, requestOptions);
            HashMap<String, String> headers = requestBuilder.codexHeaders(config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            LinkedHashMap<String, CodexOutputMerger.ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
            HashMap<String, StringBuilder> customToolInputs = new HashMap<>();
            final int[] usageInputTokens = new int[1];
            final int[] usageOutputTokens = new int[1];

            postJsonSse(requestBuilder.responsesEndpoint(config.getBaseUrl()), body, headers, cancellationToken, (eventType, data) -> {
                handleSseEvent(eventType, data, callback, text, reasoning, toolCallBuilders, customToolInputs, usageInputTokens, usageOutputTokens);
            });

            return new ModelCompletionResponse(
                    text.toString(),
                    reasoning.toString(),
                    outputMerger.buildToolCalls(toolCallBuilders),
                    usageInputTokens[0],
                    usageOutputTokens[0]
            );
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex Responses protocol stream parse failed: " + e.getMessage(), e);
        }
    }

    private void handleSseEvent(
            String eventType,
            String data,
            ModelStreamCallback callback,
            StringBuilder text,
            StringBuilder reasoning,
            LinkedHashMap<String, CodexOutputMerger.ToolCallBuilder> toolCallBuilders,
            HashMap<String, StringBuilder> customToolInputs,
            int[] usageInputTokens,
            int[] usageOutputTokens
    ) throws Exception {
        if ("[DONE]".equals(data.trim())) {
            return;
        }
        JSONObject event = new JSONObject(data);
        if (event.has("error")) {
            throw new ModelCompletionException("Codex stream error: " + event.opt("error"));
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
            outputMerger.appendFinalIfMissing(text, event.optString("text", event.optString("delta")), false, callback);
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

        handleCompleted(type, event, callback, text, reasoning, toolCallBuilders, customToolInputs, usageInputTokens, usageOutputTokens);
    }

    private void handleCompleted(
            String type,
            JSONObject event,
            ModelStreamCallback callback,
            StringBuilder text,
            StringBuilder reasoning,
            LinkedHashMap<String, CodexOutputMerger.ToolCallBuilder> toolCallBuilders,
            HashMap<String, StringBuilder> customToolInputs,
            int[] usageInputTokens,
            int[] usageOutputTokens
    ) throws Exception {
        if ("response.completed".equals(type)) {
            JSONObject response = event.optJSONObject("response");
            if (response != null) {
                outputMerger.mergeOutputArray(response.optJSONArray("output"), text, reasoning, toolCallBuilders, customToolInputs, callback);
                JSONObject usage = response.optJSONObject("usage");
                if (usage != null) {
                    usageInputTokens[0] = Math.max(usageInputTokens[0], usage.optInt("input_tokens", 0));
                    usageOutputTokens[0] = Math.max(usageOutputTokens[0], usage.optInt("output_tokens", 0));
                }
            }
            throw new SseStreamCompleteException();
        } else if ("response.output_item.done".equals(type) && event.has("response")) {
            JSONObject response = event.optJSONObject("response");
            if (response != null) {
                outputMerger.mergeOutputArray(response.optJSONArray("output"), text, reasoning, toolCallBuilders, customToolInputs, callback);
            }
        } else if (("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) && event.has("item")) {
            outputMerger.mergeOutputItem(event.optJSONObject("item"), text, reasoning, toolCallBuilders, customToolInputs, callback);
        } else if ("response.failed".equals(type)) {
            throw new ModelCompletionException("Codex response.failed: " + event.toString());
        } else if ("response.incomplete".equals(type)) {
            JSONObject response = event.optJSONObject("response");
            JSONObject details = response == null ? null : response.optJSONObject("incomplete_details");
            String reason = details == null ? "unknown" : details.optString("reason", "unknown");
            throw new ModelCompletionException("Codex response.incomplete: " + reason);
        }
    }

    static String codexUserAgent() {
        return CodexRequestBuilder.codexUserAgent();
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
            LinkedHashMap<String, CodexOutputMerger.ToolCallBuilder> toolCallBuilders,
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
        CodexOutputMerger.ToolCallBuilder builder = toolCallBuilders.get(callId);
        if (builder == null) {
            builder = new CodexOutputMerger.ToolCallBuilder(callId);
            toolCallBuilders.put(callId, builder);
        }
        builder.arguments.append(delta);
    }
}
