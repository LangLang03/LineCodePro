package cn.lineai.feature.extensions;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit one Agent extension. */
public final class AgentExtensionEditPage extends ExtensionPage {

    public interface Listener {
        void onSave(AgentExtension extension);

        void onDelete(String id);

        /** Blocking draft generation; the page calls it on the IO scheduler. */
        AgentExtension generateDraft(String description) throws Exception;
    }

    private final Listener listener;
    private final Scheduler scheduler;
    private final AgentExtension editing;
    private final List<Tool> tools;
    private final Set<String> selectedTools = new HashSet<>();
    private final FormField name;
    private final FormField slug;
    private final FormField prompt;
    private final FormField trigger;
    private final LinearLayout toolsHost;

    public AgentExtensionEditPage(Context context, Tokens tokens, Runnable back,
                                  Scheduler scheduler, AgentExtension editing, List<Tool> tools,
                                  Listener listener) {
        super(context, tokens, back, R.string.screen_agent_add_title);
        this.listener = listener;
        this.scheduler = scheduler;
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
                this::showAiWriter), false, 0);

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

    private void showAiWriter() {
        Context context = getContext();
        Tokens current = theme.current();
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(context, current.bg, 20));
        Ui.padding(panel, 20, 20, 20, 20);

        TextView title = Ui.bold(context,
                context.getString(R.string.screen_agent_let_ai_dialog_title),
                Tokens.FONT_LG, current.text);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = Ui.text(context,
                context.getString(R.string.screen_agent_let_ai_dialog_desc), Tokens.FONT_XS,
                current.textTertiary, android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        panel.addView(description, descParams);

        EditText input = new EditText(context);
        input.setHint(R.string.screen_agent_let_ai_hint);
        input.setTextSize(Tokens.FONT_MD);
        input.setTextColor(current.text);
        input.setHintTextColor(current.textTertiary);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinHeight(Ui.dp(context, 96));
        input.setBackground(Ui.roundedStroke(context, current.inputBg, 12, current.border));
        Ui.padding(input, 12, 12, 12, 12);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = Ui.dp(context, 12);
        panel.addView(input, inputParams);

        TextView generate = Ui.medium(context,
                context.getString(R.string.screen_agent_let_ai_button), Tokens.FONT_MD,
                current.textOnColor);
        generate.setGravity(Gravity.CENTER);
        generate.setClickable(true);
        generate.setFocusable(true);
        generate.setBackground(Ui.rounded(context, current.accent, 12));
        Ui.padding(generate, 12, 12, 12, 12);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = Ui.dp(context, 12);
        panel.addView(generate, buttonParams);

        Dialog dialog = Sheets.showBottom(context, current, panel);
        generate.setOnClickListener(v -> generateDraft(dialog, input, generate));
    }

    private void generateDraft(Dialog dialog, EditText input, TextView generate) {
        String description = input.getText() == null ? "" : input.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_agent_require_description,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        generate.setEnabled(false);
        generate.setText(R.string.message_assistant_generating);
        scheduler.io(() -> {
            try {
                AgentExtension draft = listener.generateDraft(description);
                scheduler.main(() -> {
                    if (draft == null) {
                        resetGenerate(generate);
                        Toast.makeText(getContext(),
                                R.string.toast_ai_generate_failed_no_result,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    applyDraft(draft);
                    dialog.dismiss();
                    Toast.makeText(getContext(), R.string.screen_agent_ai_filled,
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? getContext().getString(R.string.toast_ai_generate_failed)
                        : error.getMessage();
                scheduler.main(() -> {
                    resetGenerate(generate);
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void resetGenerate(TextView generate) {
        generate.setEnabled(true);
        generate.setText(R.string.screen_agent_let_ai_button);
    }

    private void applyDraft(AgentExtension draft) {
        name.input().setText(draft.name);
        slug.input().setText(draft.slug);
        prompt.input().setText(draft.prompt);
        trigger.input().setText(draft.trigger);
        selectedTools.clear();
        selectedTools.addAll(draft.toolNames);
        renderTools();
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
