package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

/** Anthropic Messages API: streaming request body and SSE event parsing. */
public final class AnthropicCodec {

    private AnthropicCodec() {
    }

    public static String requestBody(ChatRequest request) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", request.modelId);
            int budget = request.reasoning == null ? 0 : request.reasoning.thinkingBudget();
            boolean thinking = request.reasoning != null && request.reasoning.enabled();
            body.put("max_tokens", thinking ? Math.max(4096, budget + 1024) : 4096);
            body.put("stream", true);
            if (!request.systemPrompt.isEmpty()) body.put("system", request.systemPrompt);
            body.put("messages", messages(request));
            if (!request.tools.isEmpty()) {
                JSONArray tools = new JSONArray();
                for (ToolSchema tool : request.tools) {
                    tools.put(new JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("input_schema", new JSONObject(tool.parametersJson)));
                }
                body.put("tools", tools);
            }
            if (thinking) {
                body.put("thinking", new JSONObject()
                        .put("type", "enabled")
                        .put("budget_tokens", budget));
            }
            return body.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot build Anthropic request", error);
        }
    }

    private static JSONArray messages(ChatRequest request) throws JSONException {
        JSONArray messages = new JSONArray();
        int index = 0;
        while (index < request.messages.size()) {
            Message message = request.messages.get(index);
            if (message.role == Role.TOOL) {
                JSONArray content = new JSONArray();
                while (index < request.messages.size()
                        && request.messages.get(index).role == Role.TOOL) {
                    Message tool = request.messages.get(index);
                    content.put(new JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", tool.toolCallId)
                            .put("content", tool.plainText()));
                    index++;
                }
                messages.put(new JSONObject().put("role", "user").put("content", content));
                continue;
            }
            index++;
            String role = role(message.role);
            if (role == null) continue;
            String text = message.wireText();
            java.util.List<String> images = ImageData.paths(message);
            if (message.toolCalls.isEmpty()) {
                if (images.isEmpty()) {
                    if (text.isEmpty()) continue;
                    messages.put(new JSONObject().put("role", role).put("content", text));
                } else {
                    JSONArray content = new JSONArray();
                    if (!text.isEmpty()) {
                        content.put(new JSONObject().put("type", "text").put("text", text));
                    }
                    for (String image : images) {
                        String data = ImageData.base64(image);
                        if (data.isEmpty()) continue;
                        content.put(new JSONObject().put("type", "image")
                                .put("source", new JSONObject()
                                        .put("type", "base64")
                                        .put("media_type", ImageData.mediaType(image))
                                        .put("data", data)));
                    }
                    messages.put(new JSONObject().put("role", role).put("content", content));
                }
            } else {
                JSONArray content = new JSONArray();
                if (!text.isEmpty()) content.put(new JSONObject().put("type", "text").put("text", text));
                for (cn.lineai.core.model.ToolCallRecord call : message.toolCalls) {
                    content.put(new JSONObject()
                            .put("type", "tool_use")
                            .put("id", call.id)
                            .put("name", call.name)
                            .put("input", new JSONObject(call.arguments)));
                }
                messages.put(new JSONObject().put("role", role).put("content", content));
            }
        }
        return messages;
    }

    private static String role(Role role) {
        if (role == Role.USER) return "user";
        if (role == Role.ASSISTANT) return "assistant";
        return null;
    }

    public static StreamDecoder decoder() {
        return AnthropicCodec::parse;
    }

    /** Returns the delta carried by one SSE payload; throws on provider errors. */
    public static StreamEvent parse(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String type = event.optString("type");
            if ("error".equals(type)) {
                throw new IllegalStateException("Anthropic error: " + event.opt("error"));
            }
            if ("message_start".equals(type)) {
                JSONObject usage = event.optJSONObject("message");
                JSONObject tokens = usage == null ? null : usage.optJSONObject("usage");
                return StreamEvent.of("", "", "",
                        tokens == null ? 0 : tokens.optInt("input_tokens", 0), 0);
            }
            if ("message_delta".equals(type)) {
                JSONObject delta = event.optJSONObject("delta");
                JSONObject usage = event.optJSONObject("usage");
                return StreamEvent.of("", "", delta == null ? "" : delta.optString("stop_reason", ""),
                        0, usage == null ? 0 : usage.optInt("output_tokens", 0));
            }
            if ("content_block_start".equals(type)) {
                JSONObject block = event.optJSONObject("content_block");
                if (block != null && "redacted_thinking".equals(block.optString("type"))) {
                    return StreamEvent.reasoning("[redacted thinking]");
                }
                if (block != null && "tool_use".equals(block.optString("type"))) {
                    return StreamEvent.calls(java.util.Collections.singletonList(
                            new ToolCallDraft(event.optInt("index", 0), block.optString("id", ""),
                                    block.optString("name", ""), "")));
                }
                return StreamEvent.of("", "", "", 0, 0);
            }
            if ("content_block_delta".equals(type)) {
                JSONObject delta = event.optJSONObject("delta");
                if (delta == null) return StreamEvent.of("", "", "", 0, 0);
                String deltaType = delta.optString("type");
                if ("thinking_delta".equals(deltaType)) {
                    return StreamEvent.reasoning(delta.optString("thinking", ""));
                }
                if ("text_delta".equals(deltaType)) {
                    return StreamEvent.text(delta.optString("text", ""));
                }
                if ("input_json_delta".equals(deltaType)) {
                    return StreamEvent.calls(java.util.Collections.singletonList(
                            new ToolCallDraft(event.optInt("index", 0), "", "",
                                    delta.optString("partial_json", ""))));
                }
            }
            return StreamEvent.of("", "", "", 0, 0);
        } catch (JSONException error) {
            throw new IllegalStateException("Anthropic stream parse failed", error);
        }
    }

    public static String messagesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/messages")) return base;
        if (base.endsWith("/v1")) return base + "/messages";
        return base + "/v1/messages";
    }

    public static List<Message> asList(Message message) {
        return java.util.Collections.singletonList(message);
    }
}
