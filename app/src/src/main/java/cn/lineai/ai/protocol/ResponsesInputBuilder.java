package cn.lineai.ai.protocol;

import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.tool.ToolCall;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class ResponsesInputBuilder {
    private ResponsesInputBuilder() {
    }

    static JSONArray inputJson(List<ModelMessage> messages) throws Exception {
        JSONArray array = new JSONArray();
        for (ModelMessage message : messages) {
            if (appendRawInputItem(array, message.getRawInputJson())) {
                continue;
            }
            String role = message.getRole();
            if ("system".equals(role)) {
                continue;
            }
            if ("user".equals(role)) {
                appendMessage(array, "user", "input_text", message.getContent());
            } else if ("assistant".equals(role)) {
                if (message.getContent().trim().length() > 0) {
                    appendMessage(array, "assistant", "output_text", message.getContent());
                }
                for (ToolCall call : message.getToolCalls()) {
                    array.put(new JSONObject()
                            .put("type", "function_call")
                            .put("name", call.getName())
                            .put("arguments", call.getArguments().length() == 0 ? "{}" : call.getArguments())
                            .put("call_id", call.getId()));
                }
            } else if ("tool".equals(role)) {
                array.put(toolOutputItem(message));
            }
        }
        return array;
    }

    static String instructions(List<ModelMessage> messages) {
        StringBuilder builder = new StringBuilder();
        if (messages == null) {
            return "";
        }
        for (ModelMessage message : messages) {
            if (message == null || !"system".equals(message.getRole())) {
                continue;
            }
            String content = message.getContent().trim();
            if (content.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(content);
        }
        return builder.toString();
    }

    private static void appendMessage(JSONArray target, String role, String contentType, String text) throws Exception {
        target.put(new JSONObject()
                .put("type", "message")
                .put("role", role)
                .put("content", new JSONArray()
                        .put(new JSONObject()
                                .put("type", contentType)
                                .put("text", text == null ? "" : text))));
    }

    private static boolean appendRawInputItem(JSONArray target, String rawInputJson) throws Exception {
        if (rawInputJson == null || rawInputJson.trim().length() == 0) {
            return false;
        }
        String raw = rawInputJson.trim();
        ImageInputPayload.Payload imagePayload = ImageInputPayload.fromRawInputJson(raw);
        if (imagePayload != null) {
            appendImageMessage(target, imagePayload);
            return true;
        }
        if (raw.startsWith("[")) {
            JSONArray items = new JSONArray(raw);
            for (int i = 0; i < items.length(); i++) {
                Object item = items.opt(i);
                if (item != null) {
                    target.put(item);
                }
            }
            return true;
        }
        target.put(new JSONObject(raw));
        return true;
    }

    private static void appendImageMessage(JSONArray target, ImageInputPayload.Payload payload) throws Exception {
        target.put(new JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "input_text")
                                .put("text", payload.getPrompt()))
                        .put(new JSONObject()
                                .put("type", "input_image")
                                .put("image_url", payload.dataUrl()))));
    }

    private static JSONObject toolOutputItem(ModelMessage message) throws Exception {
        String content = message.getContent();
        if (message.isToolError()) {
            String label = message.getToolName().length() > 0 ? message.getToolName() : message.getToolCallId();
            content = "Tool " + label + " failed:\n" + content;
        }
        return new JSONObject()
                .put("type", "function_call_output")
                .put("call_id", message.getToolCallId())
                .put("output", content == null ? "" : content);
    }
}
