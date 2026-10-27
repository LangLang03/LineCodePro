package cn.lineai.feature.settings;

import java.util.Arrays;
import java.util.List;

/** The tool groups shown on the MCP screen, with their default state and target support. */
public final class ToolGroups {

    public static final class Group {
        public final String id;
        public final int name;
        public final int description;
        public final int icon;
        public final boolean enabledByDefault;
        public final boolean local;
        public final boolean remote;

        Group(String id, int name, int description, int icon, boolean enabledByDefault,
              boolean local, boolean remote) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.enabledByDefault = enabledByDefault;
            this.local = local;
            this.remote = remote;
        }

        public boolean visibleIn(String mode) {
            return McpPreferences.MODE_LOCAL.equals(mode) ? local : remote;
        }
    }

    private ToolGroups() {
    }

    public static List<Group> all() {
        return Arrays.asList(
                new Group("file_ops", cn.lineai.ui.i18n.R.string.tool_group_file_ops_name,
                        cn.lineai.ui.i18n.R.string.tool_group_file_ops_desc,
                        cn.lineai.ui.kit.Icons.FILE, true, true, false),
                new Group("agent", cn.lineai.ui.i18n.R.string.tool_group_agent_name,
                        cn.lineai.ui.i18n.R.string.tool_group_agent_desc,
                        cn.lineai.ui.kit.Icons.BRAIN, true, true, true),
                new Group("phone_control", cn.lineai.ui.i18n.R.string.tool_group_phone_control_name,
                        cn.lineai.ui.i18n.R.string.tool_group_phone_control_desc,
                        cn.lineai.ui.kit.Icons.SMARTPHONE, true, true, true),
                new Group("todo", cn.lineai.ui.i18n.R.string.tool_group_todo_name,
                        cn.lineai.ui.i18n.R.string.tool_group_todo_desc,
                        cn.lineai.ui.kit.Icons.SCROLL_TEXT, true, true, true),
                new Group("image_understanding",
                        cn.lineai.ui.i18n.R.string.tool_group_image_understanding_name,
                        cn.lineai.ui.i18n.R.string.tool_group_image_understanding_desc,
                        cn.lineai.ui.kit.Icons.PAINTBRUSH, false, true, true),
                new Group("image_generation",
                        cn.lineai.ui.i18n.R.string.tool_group_image_generation_name,
                        cn.lineai.ui.i18n.R.string.tool_group_image_generation_desc,
                        cn.lineai.ui.kit.Icons.SPARKLES, false, true, true),
                new Group("shell", cn.lineai.ui.i18n.R.string.tool_group_shell_name,
                        cn.lineai.ui.i18n.R.string.tool_group_shell_desc,
                        cn.lineai.ui.kit.Icons.TERMINAL, true, false, true),
                new Group("web_search", cn.lineai.ui.i18n.R.string.tool_group_web_search_name,
                        cn.lineai.ui.i18n.R.string.tool_group_web_search_desc,
                        cn.lineai.ui.kit.Icons.SEARCH, true, true, true),
                new Group("memory", cn.lineai.ui.i18n.R.string.tool_group_memory_name,
                        cn.lineai.ui.i18n.R.string.tool_group_memory_desc,
                        cn.lineai.ui.kit.Icons.BOOK_OPEN, true, true, true));
    }
}
