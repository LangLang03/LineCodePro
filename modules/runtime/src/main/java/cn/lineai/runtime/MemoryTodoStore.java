package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.TodoItem;
import cn.lineai.tool.api.TodoStore;

/**
 * Session-scoped TODO state backed only by memory; the list is intentionally lost when the
 * process dies because tool TODOs are transient scratch state, not user data.
 */
public final class MemoryTodoStore implements TodoStore {

    private final List<TodoItem> items = new ArrayList<>();

    /** Replaces the whole list; {@code null} clears it. */
    @Override public synchronized void replace(List<TodoItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
    }

    /** Returns a defensive copy so callers cannot mutate the backing list. */
    @Override public synchronized List<TodoItem> items() {
        return new ArrayList<>(items);
    }
}
