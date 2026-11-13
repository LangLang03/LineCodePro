package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

/**
 * OpenAI Responses API ("Codex") streaming request and SSE event parsing.
 *
 * <p>The Codex CLI wire contract is honoured explicitly: requests always stream and conversation
 * history is sent as a flat {@code input} array in which tool results are
 * {@code function_call_output} items and assistant tool calls are {@code function_call} items.</p>
 */
public final class CodexCodec {

    /** Codex CLI protocol version, sent as the {@code version} header and query parameter. */
    public static final String PROTOCOL_VERSION = "0.120.0";
    /** Originator identifier the Codex backend expects on every request. */
    public static final String ORIGINATOR = "codex_cli_rs";

    private CodexCodec() {
    }

    /**
     * Builds the JSON body of a Responses API streaming request.
     *
     * <p>{@code store} is enabled only for Azure deployments, which require server-side storage.
     * Reasoning is emitted with {@code summary} set to {@code auto}, and {@code max} effort is
     * downgraded to {@code high} because the Responses API defines no {@code max} level.</p>
     *
     * @param request normalized completion request
     * @param baseUrl provider base URL, used only to detect Azure
     * @return the request body as JSON text
     * @throws IllegalStateException when the body cannot be serialized
     */
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

    /** Encodes history as Responses input items; TOOL roles become function_call_output items. */
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

    /**
     * Returns a stateful decoder for Responses SSE events.
     *
     * <p>Responses events identify tool calls by {@code call_id} rather than by numeric index, so
     * the decoder assigns each new call the next free index; fragments of one call then merge
     * correctly in {@link ToolCallMerger}. The instance belongs to a single response stream.</p>
     *
     * @return decoder for one response stream
     */
    public static StreamDecoder decoder() {
        return new StreamDecoder() {
            private final java.util.Map<String, Integer> indexes = new java.util.HashMap<>();
            private int next;

            /** Decodes one Responses event, resolving tool calls through the index map. */
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

            /** Returns the stable index for a call id, allocating the next free one on first use. */
            private int index(String callId) {
                Integer existing = indexes.get(callId);
                if (existing != null) return existing;
                int value = next++;
                indexes.put(callId, value);
                return value;
            }
        };
    }

    /**
     * Parses one Responses SSE payload without tool-call state.
     *
     * @param payload data field of one SSE event
     * @return decoded event; an empty event for unrecognised event types
     * @throws IllegalStateException when the provider reports {@code error},
     *         {@code response.failed} or {@code response.incomplete}, or when the payload is not
     *         valid JSON
     */
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

    /**
     * Normalises a base URL to the Responses endpoint.
     *
     * @param baseUrl provider base URL; may end in {@code /chat/completions}, {@code /responses}
     *        or the bare origin
     * @return URL whose path ends with {@code /responses}
     */
    public static String responsesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/responses";
        }
        if (base.endsWith("/responses")) return base;
        return base + "/responses";
    }

    /**
     * Returns {@code true} when the base URL points at an Azure OpenAI deployment; Azure requires
     * {@code store: true} on Responses requests.
     *
     * @param baseUrl provider base URL, matched case-insensitively
     * @return true for known Azure OpenAI host patterns
     */
    public static boolean isAzure(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("openai.azure.") || normalized.contains("cognitiveservices.azure.")
                || normalized.contains("aoai.azure.") || normalized.contains("azure-api.")
                || normalized.contains("azurefd.") || normalized.contains("windows.net/openai");
    }
}
