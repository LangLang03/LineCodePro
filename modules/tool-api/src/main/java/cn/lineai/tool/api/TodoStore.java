package cn.lineai.tool.api;

import java.util.List;

import cn.lineai.core.model.TodoItem;

/** Session TODO state shared between the todo tool and the prompt. */
public interface TodoStore {

    void replace(List<TodoItem> items);

    List<TodoItem> items();
}
