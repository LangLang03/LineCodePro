package cn.lineai.feature.extensions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit one Agent extension. */
public final class AgentExtensionEditPage extends SettingsPage {

    public interface Listener {
        void onSave(AgentExtension extension);

        void onDelete(String id);
    }

    private final Listener listener;
    private final AgentExtension editing;
    private final FormField name;
    private final FormField slug;
    private final FormField trigger;
    private final FormField prompt;
    private final Set<String> selectedTools = new HashSet<>();

    public AgentExtensionEditPage(Context context, Tokens tokens, Runnable back,
                                  AgentExtension editing, List<Tool> tools, Listener listener) {
        super(context, editing == null || editing.id.isEmpty()
                        ? R.string.screen_agent_add_title : R.string.screen_agent_edit_title,
                tokens, back);
        this.listener = listener;
        this.editing = editing == null ? AgentExtension.builder().build() : editing;
        Ui.padding(content, 16, 16, 16, 100);

        name = field(R.string.screen_agent_field_name, this.editing.name,
                R.string.screen_agent_hint_name, false);
        slug = field(R.string.screen_agent_field_identifier, this.editing.slug,
                R.string.screen_agent_hint_slug, false);
        trigger = field(R.string.screen_agent_field_trigger, this.editing.trigger,
                R.string.screen_agent_hint_trigger, false);
        prompt = field(R.string.screen_agent_field_prompt, this.editing.prompt,
                R.string.screen_agent_hint_prompt, true);

        LinearLayout basic = card(R.string.screen_agent_form_basic);
        basic.addView(name, formParams(0));
        basic.addView(slug, formParams(12));
        TextView helper = Ui.text(getContext(),
                getContext().getString(R.string.screen_agent_helper_slug),
                Tokens.FONT_XS, theme.current().textTertiary, Typeface.NORMAL);
        helper.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        basic.addView(helper, formParams(6));
        addCard(basic);

        LinearLayout behavior = card(R.string.screen_agent_form_behavior);
        behavior.addView(trigger, formParams(0));
        behavior.addView(prompt, formParams(12));
        addCard(behavior);

        selectedTools.addAll(this.editing.toolNames);
        addTools(tools);
        content.addView(saveButton(), saveParams());

        if (!this.editing.id.isEmpty()) {
            LinearLayout delete = deleteButton();
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            deleteParams.topMargin = Ui.dp(context, 8);
            content.addView(delete, deleteParams);
        }
    }

    private LinearLayout saveButton() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 12));
        TextView label = Ui.bold(context, context.getString(R.string.screen_agent_save),
                Tokens.FONT_MD, tokens.textOnColor);
        button.addView(label, new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> save());
        return button;
    }

    private LinearLayout.LayoutParams saveParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 46));
        params.bottomMargin = Ui.dp(getContext(), 12);
        return params;
    }

    private void save() {
        String nameValue = name.value();
        String slugValue = slug.value();
        String promptValue = prompt.value();
        if (nameValue.isEmpty() || slugValue.isEmpty() || promptValue.isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_agent_save_require, Toast.LENGTH_SHORT).show();
            return;
        }
        AgentExtension saved = editing.toBuilder()
                .name(nameValue)
                .slug(slugValue)
                .trigger(trigger.value())
                .prompt(promptValue)
                .toolNames(new ArrayList<>(selectedTools))
                .build();
        listener.onSave(saved);
        Toast.makeText(getContext(), R.string.screen_agent_saved, Toast.LENGTH_SHORT).show();
    }

    private void addTools(List<Tool> tools) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout section = card(R.string.screen_agent_section_tools);
        if (tools == null || tools.isEmpty()) {
            section.addView(Ui.text(context, context.getString(R.string.screen_agent_tools_empty),
                    Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL),
                    new LinearLayout.LayoutParams(-1, -2));
            addCard(section);
            return;
        }
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        for (Tool tool : tools) {
            CheckBox box = new CheckBox(context);
            box.setText(tool.name());
            box.setTextSize(Tokens.FONT_SM);
            box.setTextColor(tokens.text);
            box.setButtonTintList(new ColorStateList(states, new int[] {tokens.accent, tokens.border}));
            box.setChecked(selectedTools.contains(tool.name()));
            box.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    selectedTools.add(tool.name());
                } else {
                    selectedTools.remove(tool.name());
                }
            });
            section.addView(box, new LinearLayout.LayoutParams(-1, -2));
            theme.bind(t -> {
                box.setTextColor(t.text);
                box.setButtonTintList(new ColorStateList(states,
                        new int[] {t.accent, t.border}));
            });
        }
        addCard(section);
    }

    private LinearLayout deleteButton() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, tokens.dangerMuted, 12, tokens.danger));
        TextView label = Ui.bold(context, context.getString(R.string.common_delete),
                Tokens.FONT_SM, tokens.danger);
        button.addView(label, new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> Dialogs.confirm(context, theme.current(),
                context.getString(R.string.screen_agent_delete_title),
                context.getString(R.string.screen_agent_delete_message,
                        editing.name.isEmpty() ? editing.slug : editing.name),
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_delete),
                confirmed -> {
                    if (confirmed) listener.onDelete(editing.id);
                }));
        return button;
    }

    private FormField field(int label, String value, int hint, boolean multiline) {
        FormField field = new FormField(getContext(), theme, label, value,
                getContext().getString(hint), false, multiline);
        theme.bind(t -> field.setVisibility(VISIBLE));
        return field;
    }

    private LinearLayout card(int title) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(title), Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 12);
        content.addView(card, params);
    }

    private LinearLayout.LayoutParams formParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), topMargin);
        return params;
    }
}
