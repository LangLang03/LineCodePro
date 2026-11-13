package cn.lineai.feature.settings;

import java.util.Arrays;
import java.util.List;

/**
 * The tool groups shown on the MCP screen, with their default state and target support.
 *
 * <p>Groups are the unit of user control over MCP tools: enabling or disabling a group toggles a
 * capability family rather than an individual tool, so callers must never branch on tool names.</p>
 */
public final class ToolGroups {

    /** Descriptor of a single tool group rendered on the MCP settings screen. */
    public static final class Group {
        public final String id;
        public final int name;
        public final int description;
        public final int icon;
        public final boolean enabledByDefault;
        public final boolean local;
        public final boolean remote;

        /** Creates a group descriptor; values are fixed at catalog definition time. */
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

        /**
         * Returns whether the group applies to the given MCP execution target.
         *
         * @param mode execution target identifier from {@link McpPreferences}
         * @return true when the group is available under the target
         */
        public boolean visibleIn(String mode) {
            return McpPreferences.MODE_LOCAL.equals(mode) ? local : remote;
        }
    }

    /** Utility class; not instantiable. */
    private ToolGroups() {
    }

    /**
     * Returns the catalog of tool groups in display order.
     *
     * @return unmodifiable-ordered list of group descriptors backed by a fixed array
     */
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
                new Group("local_shell", cn.lineai.ui.i18n.R.string.tool_group_local_shell_name,
                        cn.lineai.ui.i18n.R.string.tool_group_local_shell_desc,
                        cn.lineai.ui.kit.Icons.TERMINAL, true, true, false),
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
