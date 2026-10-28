package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

/** OpenAI Responses API ("Codex") streaming request and SSE event parsing. */
public final class CodexCodec {

    public static final String PROTOCOL_VERSION = "0.120.0";
    public static final String ORIGINATOR = "codex_cli_rs";

    private CodexCodec() {
    }

    public static String requestBody(ChatRequest request, String baseUrl) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", request.modelId);
            body.put("stream", true);
            body.put("instructions", request.systemPrompt);
            body.put("input", input(request));
            JSONArray tools = new JSONArray();
            for (ToolSchema tool : request.tools) {
                tools.put(new JSONObject()
                        .put("type", "function")
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("parameters", new JSONObject(tool.parametersJson)));
            }
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", true);
            body.put("store", isAzure(baseUrl));
            body.put("include", new JSONArray());
            if (request.reasoning != null && request.reasoning.enabled()) {
                String effort = request.reasoning.effectiveEffort();
                body.put("reasoning", new JSONObject()
                        .put("effort", "max".equals(effort) ? "high" : effort)
                        .put("summary", "auto"));
            }
            return body.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot build Codex request", error);
        }
    }

    private static JSONArray input(ChatRequest request) throws JSONException {
        JSONArray input = new JSONArray();
        for (Message message : request.messages) {
            if (message.role == Role.TOOL) {
                input.put(new JSONObject()
                        .put("type", "function_call_output")
                        .put("call_id", message.toolCallId)
                        .put("output", message.plainText()));
                continue;
            }
            String role = message.role == Role.USER ? "user"
                    : message.role == Role.ASSISTANT ? "assistant" : null;
            if (role == null) continue;
            String text = message.wireText();
            java.util.List<String> images = ImageData.paths(message);
            if (!text.isEmpty() || !images.isEmpty()) {
                JSONArray content = new JSONArray();
                if (!text.isEmpty()) {
                    content.put(new JSONObject()
                            .put("type", "user".equals(role) ? "input_text" : "output_text")
                            .put("text", text));
                }
                if ("user".equals(role)) {
                    for (String image : images) {
                        String url = ImageData.dataUrl(image);
                        if (url.isEmpty()) continue;
                        content.put(new JSONObject().put("type", "input_image")
                                .put("image_url", url));
                    }
                }
                if (content.length() > 0) {
                    input.put(new JSONObject()
                            .put("type", "message")
                            .put("role", role)
                            .put("content", content));
                }
            }
            for (cn.lineai.core.model.ToolCallRecord call : message.toolCalls) {
                input.put(new JSONObject()
                        .put("type", "function_call")
                        .put("name", call.name)
                        .put("arguments", call.arguments)
                        .put("call_id", call.id));
            }
        }
        return input;
    }

    public static StreamDecoder decoder() {
        return new StreamDecoder() {
            private final java.util.Map<String, Integer> indexes = new java.util.HashMap<>();
            private int next;

            @Override public StreamEvent decode(String payload) {
                try {
                    JSONObject event = new JSONObject(payload);
                    String type = event.optString("type");
                    JSONObject item = event.optJSONObject("item");
                    if (("response.output_item.added".equals(type)
                            || "response.output_item.done".equals(type)) && item != null
                            && "function_call".equals(item.optString("type"))) {
                        String callId = item.optString("call_id", item.optString("id", ""));
                        int index = index(callId);
                        return StreamEvent.calls(java.util.Collections.singletonList(
                                new ToolCallDraft(index, callId, item.optString("name", ""),
                                        "response.output_item.done".equals(type)
                                                ? item.optString("arguments", "") : "")));
                    }
                    if ("response.function_call_arguments.delta".equals(type)) {
                        String callId = event.optString("call_id", event.optString("item_id", ""));
                        return StreamEvent.calls(java.util.Collections.singletonList(
                                new ToolCallDraft(index(callId), callId, "",
                                        event.optString("delta", ""))));
                    }
                    return CodexCodec.parse(payload);
                } catch (JSONException error) {
                    throw new IllegalStateException("Codex stream parse failed", error);
                }
            }

            private int index(String callId) {
                Integer existing = indexes.get(callId);
                if (existing != null) return existing;
                int value = next++;
                indexes.put(callId, value);
                return value;
            }
        };
    }

    public static StreamEvent parse(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String type = event.optString("type");
            if ("error".equals(type) || "response.failed".equals(type)
                    || "response.incomplete".equals(type)) {
                throw new IllegalStateException("Codex stream error: " + event);
            }
            if ("response.output_text.delta".equals(type)) {
                return StreamEvent.text(event.optString("delta", ""));
            }
            if ("response.reasoning_summary_text.delta".equals(type)) {
                return StreamEvent.reasoning(event.optString("delta", ""));
            }
            if ("response.completed".equals(type)) {
                JSONObject response = event.optJSONObject("response");
                JSONObject usage = response == null ? null : response.optJSONObject("usage");
                return StreamEvent.of("", "", "stop",
                        usage == null ? 0 : usage.optInt("input_tokens", 0),
                        usage == null ? 0 : usage.optInt("output_tokens", 0));
            }
            return StreamEvent.of("", "", "", 0, 0);
        } catch (JSONException error) {
            throw new IllegalStateException("Codex stream parse failed", error);
        }
    }

    public static String responsesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/responses";
        }
        if (base.endsWith("/responses")) return base;
        return base + "/responses";
    }

    public static boolean isAzure(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("openai.azure.") || normalized.contains("cognitiveservices.azure.")
                || normalized.contains("aoai.azure.") || normalized.contains("azure-api.")
                || normalized.contains("azurefd.") || normalized.contains("windows.net/openai");
    }
}
