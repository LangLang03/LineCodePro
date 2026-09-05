package cn.lineai.tool.ui;

import java.util.Map;

/** Disclosure belongs to the conversation, so recycling and streamed results cannot reopen it. */
public interface ToolCallExpansion {
    void setExpansionState(Map<String, Boolean> state, String key);
}
