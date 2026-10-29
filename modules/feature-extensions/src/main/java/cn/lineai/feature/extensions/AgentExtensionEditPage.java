package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit one Agent extension. */
public final class AgentExtensionEditPage extends ExtensionPage {

    public interface Listener {
        void onSave(AgentExtension extension);

        void onDelete(String id);
    }

    private final Listener listener;
    private final AgentExtension editing;
    private final List<Tool> tools;
    private final Set<String> selectedTools = new HashSet<>();
    private final FormField name;
    private final FormField slug;
    private final FormField prompt;
    private final FormField trigger;
    private final LinearLayout toolsHost;

    public AgentExtensionEditPage(Context context, Tokens tokens, Runnable back,
                                  AgentExtension editing, List<Tool> tools, Listener listener) {
        super(context, tokens, back, R.string.screen_agent_add_title);
        this.listener = listener;
        this.editing = editing == null ? AgentExtension.builder().build() : editing;
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
        this.selectedTools.addAll(this.editing.toolNames);
        header(R.string.screen_agent_add_title, ExtensionKit.saveAction(context, theme, this::save));

        name = ExtensionKit.field(context, theme, R.string.screen_agent_field_name,
                this.editing.name, R.string.screen_agent_hint_name, false);
        slug = ExtensionKit.field(context, theme, R.string.screen_agent_field_identifier,
                this.editing.slug, R.string.screen_agent_hint_slug, false);
        prompt = ExtensionKit.field(context, theme, R.string.screen_agent_field_prompt,
                this.editing.prompt, R.string.screen_agent_hint_prompt, true);
        trigger = ExtensionKit.field(context, theme, R.string.screen_agent_field_trigger,
                this.editing.trigger, R.string.screen_agent_hint_trigger, true);
        toolsHost = new LinearLayout(context);
        toolsHost.setOrientation(LinearLayout.VERTICAL);
        build();
        renderTools();
        if (!this.editing.id.isEmpty()) renderDelete();
    }

    private void build() {
        SettingsSection quick = section(R.string.screen_agent_quick_create);
        quick.row(ExtensionKit.action(getContext(), theme, Icons.SPARKLES,
                R.string.screen_agent_let_ai_write, R.string.screen_agent_let_ai_write_desc, 68,
                () -> Toast.makeText(getContext(), R.string.screen_agent_ai_unavailable,
                        Toast.LENGTH_SHORT).show()), false, 0);

        SettingsSection basic = section(R.string.screen_agent_form_basic);
        basic.row(form(name, 0, 16, 12), false, 0);
        basic.row(form(slug, R.string.screen_agent_helper_slug, 0, 16), false, 0);

        SettingsSection behavior = section(R.string.screen_agent_form_behavior);
        behavior.row(form(prompt, 0, 16, 12), false, 0);
        behavior.row(form(trigger, 0, 0, 16), false, 0);

        content.addView(toolsHost, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout form(FormField field, int helper, int topDp, int bottomDp) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(getContext(), 16);
        wrapper.setPadding(side, Ui.dp(getContext(), topDp), side, Ui.dp(getContext(), bottomDp));
        wrapper.addView(field, new LinearLayout.LayoutParams(-1, -2));
        if (helper != 0) ExtensionKit.hint(getContext(), theme, wrapper, helper);
        return wrapper;
    }

    private void renderTools() {
        toolsHost.removeAllViews();
        String title = getContext().getString(R.string.screen_agent_tools_count,
                getContext().getString(R.string.screen_agent_section_tools),
                getContext().getString(R.string.screen_agent_tools_selected),
                selectedTools.size());
        SettingsSection section = ExtensionKit.section(getContext(), theme, title);
        toolsHost.addView(section, new LinearLayout.LayoutParams(-1, -2));
        if (tools.isEmpty()) {
            section.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_agent_tools_empty), false, 0);
            return;
        }
        for (Tool tool : tools) {
            boolean active = selectedTools.contains(tool.name());
            ExtensionRows.OptionRow row = new ExtensionRows.OptionRow(getContext(), theme,
                    Icons.SETTINGS, tool.name(), tool.description(), active, () -> {
                        if (selectedTools.contains(tool.name())) {
                            selectedTools.remove(tool.name());
                        } else {
                            selectedTools.add(tool.name());
                        }
                        renderTools();
                    });
            section.row(row, false, 0);
        }
    }

    private void renderDelete() {
        SettingsSection section = section(R.string.screen_extension_detail_delete_title);
        section.row(ExtensionKit.dangerAction(getContext(), theme, R.string.common_delete,
                () -> ExtensionKit.confirm(getContext(), theme,
                        R.string.screen_agent_delete_title,
                        getContext().getString(R.string.screen_agent_delete_message,
                                editing.name.isEmpty() ? editing.slug : editing.name),
                        () -> listener.onDelete(editing.id))), false, 0);
    }

    private void save() {
        String nameValue = name.value();
        String slugValue = normalizeSlug(slug.value().isEmpty() ? nameValue : slug.value());
        String promptValue = prompt.value();
        if (nameValue.isEmpty() || slugValue.isEmpty() || promptValue.isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_agent_save_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        AgentExtension saved = editing.toBuilder()
                .name(nameValue)
                .slug(slugValue)
                .prompt(promptValue)
                .trigger(trigger.value())
                .toolNames(new ArrayList<>(selectedTools))
                .build();
        listener.onSave(saved);
    }

    /** Lowercase identifier; non-alphanumeric runs collapse to a dash (legacy normalisation). */
    private static String normalizeSlug(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean dash = false;
        for (int index = 0; index < raw.length() && builder.length() < 48; index++) {
            char ch = raw.charAt(index);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
                dash = false;
            } else if (!dash && builder.length() > 0) {
                builder.append('-');
                dash = true;
            }
        }
        int end = builder.length();
        while (end > 0 && (builder.charAt(end - 1) == '-' || builder.charAt(end - 1) == '_')) end--;
        String clean = builder.substring(0, end);
        if (clean.isEmpty()) return "";
        char first = clean.charAt(0);
        return first >= 'a' && first <= 'z' ? clean : "agent-" + clean;
    }
}
