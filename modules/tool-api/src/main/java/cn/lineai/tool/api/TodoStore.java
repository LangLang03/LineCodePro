package cn.lineai.tool.api;

import java.util.List;

import cn.lineai.core.model.TodoItem;

/**
 * Session TODO state shared between the todo tool and the prompt.
 *
 * <p>The store is the single source of truth: the tool writes plans through it and the prompt
 * builder reads the same list, so both always agree on the current plan.</p>
 */
public interface TodoStore {

    /**
     * Replaces the whole plan with the given items.
     *
     * @param items the new plan; an empty list clears the plan
     */
    void replace(List<TodoItem> items);

    /**
     * Returns the current plan in display order.
     *
     * @return the live item list; never {@code null}
     */
    List<TodoItem> items();
}
