package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.message.ModelMessage;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class OpenAiMessageSerializer {

    JSONArray messagesJson(List<ModelMessage> messages) throws Exception {
        return messagesJson(messages, false);
    }

    JSONArray messagesJson(List<ModelMessage> messages, boolean preserveReasoning) throws Exception {
        JSONArray array = new JSONArray();
        for (ModelMessage message : messages) {
            JSONObject object = new JSONObject();
            object.put("role", message.getRole());
            if ("tool".equals(message.getRole())) {
                object.put("tool_call_id", message.getToolCallId());
                object.put("content", toolContentForModel(message));
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

    private static String toolContentForModel(ModelMessage message) {
        String content = message.getContent();
        if (!message.isToolError()) {
            return content == null ? "" : content;
        }
        String label = message.getToolName().length() > 0 ? message.getToolName() : message.getToolCallId();
        String body = content == null ? "" : content;
        return "Tool " + label + " failed:\n" + body;
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
}
