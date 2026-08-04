package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.model.TodoItem;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TodoUpdateTool extends BaseTool {
    public static final String NAME = "todo_update";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Maintain the current session's TODO list. Each call replaces the old list with the full list; "
                + "the state is injected as {{TODO_STATE}} into the next system prompt, "
                + "helping the model proceed in order and update progress promptly. "
                + "Status values: pending (not started) / in_progress (in progress) / completed (done). "
                + "At most 1 in_progress at a time; new tasks should be placed at the bottom of the list; "
                + "when a task is complete, remove it from the list or set it to completed immediately, do not keep intermediate states.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.TODO;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("items", new JSONObject()
                                .put("type", "array")
                                .put("description", "Complete TODO list; replaces the old list.")
                                .put("items", new JSONObject()
                                        .put("type", "object")
                                        .put("properties", new JSONObject()
                                                .put("content", new JSONObject()
                                                        .put("type", "string")
                                                        .put("description", "Task content, concise and verifiable"))
                                                .put("status", new JSONObject()
                                                        .put("type", "string")
                                                        .put("enum", new JSONArray()
                                                                .put(TodoItem.STATUS_PENDING)
                                                                .put(TodoItem.STATUS_IN_PROGRESS)
                                                                .put(TodoItem.STATUS_COMPLETED))
                                                        .put("description", "Task status: pending / in_progress / completed"))))))
                .put("required", new JSONArray().put("items"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (input == null) {
            return error(context.getString(R.string.tool_todo_params_empty));
        }
        JSONArray rawArray = input.optJSONArray("items");
        if (rawArray == null) {
            return error(context.getString(R.string.tool_todo_items_missing));
        }
        List<TodoItem> parsed = new ArrayList<>();
        for (int i = 0; i < rawArray.length(); i++) {
            JSONObject obj = rawArray.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            TodoItem item = TodoItem.fromJson(obj);
            if (item != null) {
                parsed.add(item);
            }
        }
        TodoStateStore store = context == null ? null : context.getTodoStateStore();
        if (store == null) {
            return error(context.getString(R.string.tool_todo_store_not_init));
        }
        store.replace(parsed);
        int total = store.totalCount();
        int done = store.completedCount();
        String summary;
        if (total == 0) {
            summary = context.getString(R.string.tool_todo_cleared);
        } else {
            summary = context.getString(R.string.tool_todo_updated, total, done);
        }
        return ok(summary);
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallTodoView.class;
    }
}
