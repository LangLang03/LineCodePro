package cn.lineai.tool.ui;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 嵌套工具调用的解析器：从Agent/Pipeline的progress JSON数组中解析出ToolCall-ToolResult对。
 * 将解析逻辑从UI层下沉到tool层。
 */
public final class NestedToolCallParser {

    public static class NestedCall {
        public final ToolCall call;
        public final ToolResult result; // 可能为null

        public NestedCall(ToolCall call, ToolResult result) {
            this.call = call;
            this.result = result;
        }
    }

    public static List<NestedCall> parse(JSONArray items) {
        List<NestedCall> calls = new ArrayList<>();
        if (items == null) return calls;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            ToolCall call = new ToolCall(
                    item.optString("id"),
                    item.optString("name"),
                    item.optString("arguments", "{}")
            );
            ToolResult result = null;
            JSONObject resultObject = item.optJSONObject("result");
            if (resultObject != null) {
                result = ToolResult.withReview(
                        call.getId(),
                        call.getName(),
                        resultObject.optString("content"),
                        resultObject.optBoolean("is_error"),
                        resultObject.optString("diff_id"),
                        resultObject.optString("review_state"),
                        resultObject.optString("review_message")
                );
            }
            calls.add(new NestedCall(call, result));
        }
        return calls;
    }

    private NestedToolCallParser() {
    }
}
