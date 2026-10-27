package cn.lineai.tool.workspace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.TodoItem;
import cn.lineai.tool.api.TodoStore;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** Replaces the session TODO list; the state is injected into the next system prompt. */
public final class TodoUpdateTool implements Tool {

    @Override public String name() { return "todo_update"; }

    @Override public String description() {
        return "Replace the session TODO list. Each call sends the complete list. "
                + "Status values: pending, in_progress (at most one), completed.";
    }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .objectArray("items", "Complete TODO list", cn.lineai.tool.api.ToolSchemaBuilder.object()
                        .string("content", "Task content")
                        .enumString("status", "Task status",
                                TodoItem.Status.PENDING.wireName(),
                                TodoItem.Status.IN_PROGRESS.wireName(),
                                TodoItem.Status.COMPLETED.wireName())
                        .required("content").required("status"))
                .required("items").build();
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        int count = arguments == null || arguments.optJSONArray("items") == null
                ? 0 : arguments.optJSONArray("items").length();
        return new ToolPresentation("TODO update", ToolIcon.TODO,
                count + " items", ToolPresentation.reason(arguments));
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        TodoStore store = context == null ? null : context.todos();
        if (store == null) return ToolResult.failure("", "TODO store is not available");
        JSONArray raw = arguments == null ? null : arguments.optJSONArray("items");
        if (raw == null) return ToolResult.failure("", "items is required");
        List<TodoItem> items = new ArrayList<>();
        int inProgress = 0;
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null) continue;
            String content = item.optString("content", "").trim();
            if (content.isEmpty()) continue;
            TodoItem.Status status = TodoItem.Status.fromWire(
                    item.optString("status", TodoItem.Status.PENDING.wireName()));
            if (status == TodoItem.Status.IN_PROGRESS) inProgress++;
            items.add(new TodoItem(content, status));
        }
        if (inProgress > 1) {
            return ToolResult.failure("", "At most one task may be in_progress");
        }
        store.replace(items);
        int done = 0;
        for (TodoItem item : items) {
            if (item.status == TodoItem.Status.COMPLETED) done++;
        }
        if (items.isEmpty()) return ToolResult.success("", "TODO list cleared");
        return ToolResult.success("", "TODO list updated: " + items.size() + " tasks, " + done + " done");
    }
}
