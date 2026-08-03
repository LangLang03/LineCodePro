package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.ai.ModelStreamCallback;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

final class CodexOutputMerger {

    static final class ToolCallBuilder extends AbstractToolCallBuilder {
        ToolCallBuilder(String callId) {
            this.id = callId == null ? "" : callId;
        }
    }

    void mergeOutputArray(
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

    void mergeOutputItem(
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

    List<ToolCall> buildToolCalls(LinkedHashMap<String, ToolCallBuilder> toolCallBuilders) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (ToolCallBuilder builder : toolCallBuilders.values()) {
            if (builder == null || !builder.hasName()) {
                continue;
            }
            calls.add(builder.buildWithId());
        }
        return calls;
    }

    void upsertToolCall(
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

    void appendFinalIfMissing(
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

    static String argumentsString(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return "{}";
        }
        if (value instanceof String) {
            String text = (String) value;
            return text.length() == 0 ? "{}" : text;
        }
        return String.valueOf(value);
    }

    static String customInput(Map<String, StringBuilder> customToolInputs, String callId, String itemId) {
        StringBuilder byCallId = customToolInputs.get(callId);
        if (byCallId != null) {
            return byCallId.toString();
        }
        StringBuilder byItemId = customToolInputs.get(itemId);
        return byItemId == null ? "" : byItemId.toString();
    }
}
