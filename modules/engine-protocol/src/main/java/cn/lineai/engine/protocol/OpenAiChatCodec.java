package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

/**
 * OpenAI-compatible wire format: request body builder and streaming delta parser.
 *
 * <p>This is the only place that knows the provider's JSON field names. Everything above it deals
 * in {@link ChatRequest} and {@link StreamEvent}.</p>
 */
public final class OpenAiChatCodec {

    private OpenAiChatCodec() {
    }

    /**
     * Builds the JSON body of an OpenAI-compatible chat completion request.
     *
     * <p>The system prompt becomes a leading {@code system} message; assistant tool calls use the
     * {@code tool_calls} shape and tool results the {@code tool} role; image blocks become
     * {@code image_url} content parts. Vendor reasoning fields are applied last via
     * {@link ReasoningPolicy}.</p>
     *
     * @param request normalized completion request
     * @return the request body as JSON text
     * @throws IllegalStateException when the body cannot be serialized
     */
    public static String requestBody(ChatRequest request) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", request.modelId);
            body.put("stream", request.stream);
            body.put("temperature", (double) request.temperature);
            body.put("max_tokens", request.maxTokens);
            JSONArray messages = new JSONArray();
            if (!request.systemPrompt.isEmpty()) {
                messages.put(message("system", request.systemPrompt));
            }
            for (Message source : request.messages) {
                JSONObject item = wireMessage(source);
                if (item != null) messages.put(item);
            }
            body.put("messages", messages);
            if (!request.tools.isEmpty()) {
                JSONArray tools = new JSONArray();
                for (ToolSchema tool : request.tools) {
                    tools.put(new JSONObject()
                            .put("type", "function")
                            .put("function", new JSONObject()
                                    .put("name", tool.name)
                                    .put("description", tool.description)
                                    .put("parameters", new JSONObject(tool.parametersJson))));
                }
                body.put("tools", tools);
                body.put("tool_choice", "auto");
            }
            ReasoningPolicy.apply(body, request.reasoning);
            return body.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot build request body", e);
        }
    }

    /** Encodes one message, choosing plain, multipart, tool-result or tool-call wire shape. */
    private static JSONObject wireMessage(Message source) throws JSONException {
        if (source.role == Role.TOOL) {
            return new JSONObject().put("role", "tool")
                    .put("tool_call_id", source.toolCallId)
                    .put("content", source.plainText());
        }
        String role = roleName(source.role);
        if (role == null) return null;
        String content = source.wireText();
        if (source.toolCalls.isEmpty()) {
            java.util.List<String> images = ImageData.paths(source);
            if (images.isEmpty()) {
                return content.isEmpty() ? null : message(role, content);
            }
            JSONArray parts = new JSONArray();
            if (!content.isEmpty()) {
                parts.put(new JSONObject().put("type", "text").put("text", content));
            }
            for (String image : images) {
                String url = ImageData.dataUrl(image);
                if (url.isEmpty()) continue;
                parts.put(new JSONObject().put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", url)));
            }
            return new JSONObject().put("role", role).put("content", parts);
        }
        JSONArray calls = new JSONArray();
        for (cn.lineai.core.model.ToolCallRecord call : source.toolCalls) {
            calls.put(new JSONObject()
                    .put("id", call.id)
                    .put("type", "function")
                    .put("function", new JSONObject()
                            .put("name", call.name)
                            .put("arguments", call.arguments)));
        }
        return new JSONObject().put("role", role).put("content", content).put("tool_calls", calls);
    }

    /** Creates a simple role/content message object. */
    private static JSONObject message(String role, String content) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    /** Maps a role to the OpenAI wire name; TOOL and unknown roles yield null. */
    private static String roleName(Role role) {
        switch (role) {
            case USER:
                return "user";
            case ASSISTANT:
                return "assistant";
            case SYSTEM:
                return "system";
            default:
                return null;
        }
    }

    /** Returns a stateless decoder over {@link #parse(String)}. */
    public static StreamDecoder decoder() {
        return OpenAiChatCodec::parse;
    }

    /**
     * Parses one SSE payload.
     *
     * @param payload data field of one SSE event, or the terminal {@code [DONE]} marker
     * @return decoded event, or {@code null} for keep-alives, the terminal marker and payloads
     *         that are not valid JSON
     */
    public static StreamEvent parse(String payload) {
        if (payload == null || payload.isEmpty() || "[DONE]".equals(payload.trim())) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(payload);
            String text = "";
            String reasoning = "";
            String finish = "";
            List<ToolCallDraft> calls = null;
            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                finish = choice.optString("finish_reason", "");
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    delta = choice.optJSONObject("message");
                }
                if (delta != null) {
                    text = delta.optString("content", "");
                    reasoning = delta.optString("reasoning_content",
                            delta.optString("reasoning", ""));
                    calls = parseToolCalls(delta.optJSONArray("tool_calls"));
                }
            }
            int prompt = 0;
            int completion = 0;
            JSONObject usage = root.optJSONObject("usage");
            if (usage != null) {
                prompt = usage.optInt("prompt_tokens", 0);
                completion = usage.optInt("completion_tokens", 0);
            }
            if (calls != null && !calls.isEmpty()) {
                return StreamEvent.of(text, reasoning, finish, prompt, completion, calls);
            }
            return StreamEvent.of(text, reasoning, finish, prompt, completion);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Decodes the tool_calls array into drafts; returns null when absent or empty. */
    private static List<ToolCallDraft> parseToolCalls(JSONArray items) {
        if (items == null || items.length() == 0) return null;
        List<ToolCallDraft> calls = new java.util.ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            JSONObject function = item.optJSONObject("function");
            calls.add(new ToolCallDraft(item.optInt("index", i), item.optString("id", ""),
                    function == null ? "" : function.optString("name", ""),
                    function == null ? "" : function.optString("arguments", "")));
        }
        return calls;
    }

    /**
     * Appends the chat path to a base URL unless it already points at an endpoint.
     *
     * @param baseUrl provider base URL, with or without trailing slash
     * @return URL whose path ends with {@code /chat/completions} or {@code /completions}
     */
    public static String endpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions") || base.endsWith("/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    /**
     * Appends {@code suffix} unless the trimmed base already ends with it.
     *
     * @param baseUrl provider base URL, with or without trailing slash
     * @param suffix path suffix such as {@code /models}
     * @return URL whose path ends with {@code suffix}
     */
    public static String endpoint(String baseUrl, String suffix) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(suffix)) return base;
        return base + suffix;
    }

    /**
     * Wraps the given messages in a fixed-size list.
     *
     * @param messages messages to wrap
     * @return list view over the arguments, for single-turn callers
     */
    public static List<Message> asList(Message... messages) {
        return java.util.Arrays.asList(messages);
    }
}
