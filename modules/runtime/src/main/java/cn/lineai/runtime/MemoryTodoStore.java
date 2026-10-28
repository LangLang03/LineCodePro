package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.TodoItem;
import cn.lineai.tool.api.TodoStore;

/** Session-scoped TODO state; recreated with the process. */
public final class MemoryTodoStore implements TodoStore {

    private final List<TodoItem> items = new ArrayList<>();

    @Override public synchronized void replace(List<TodoItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
    }

    @Override public synchronized List<TodoItem> items() {
        return new ArrayList<>(items);
    }
}
