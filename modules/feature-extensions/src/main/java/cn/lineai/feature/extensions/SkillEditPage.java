package cn.lineai.feature.extensions;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Toast;

import cn.lineai.core.model.Skill;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit a local SKILL.md extension. */
public final class SkillEditPage extends SettingsPage {

    public interface Listener {
        void onSave(Skill skill);

        void onDelete(String id);
    }

    private final Listener listener;
    private final Skill editing;
    private final FormField name;
    private final FormField description;
    private final FormField instructions;

    public SkillEditPage(Context context, Tokens tokens, Runnable back, Skill editing,
                         Listener listener) {
        super(context, editing == null || editing.id.isEmpty()
                        ? R.string.screen_skill_add_title : R.string.screen_skill_edit_title,
                tokens, back);
        this.listener = listener;
        this.editing = editing == null ? Skill.builder().build() : editing;
        Ui.padding(content, 16, 16, 16, 100);

        name = field(R.string.screen_skill_field_name, this.editing.name,
                R.string.screen_skill_hint_name, false);
        description = field(R.string.screen_skill_field_description, this.editing.description,
                R.string.screen_skill_hint_description, false);
        instructions = field(R.string.screen_skill_field_content, this.editing.content,
                R.string.screen_skill_hint_content, true);

        LinearLayout form = card();
        form.addView(name, formParams(0));
        form.addView(description, formParams(12));
        form.addView(instructions, formParams(12));
        addCard(form);

        instructions.input().setMinHeight(Ui.dp(context, 220));
        instructions.input().setGravity(Gravity.TOP | Gravity.START);

        content.addView(saveButton(), saveParams());
        if (!this.editing.id.isEmpty()) {
            content.addView(deleteButton(),
                    new LinearLayout.LayoutParams(-1, Ui.dp(context, 44)));
        }
    }

    private void save() {
        if (name.value().isEmpty() || instructions.value().isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_skill_save_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Skill saved = editing.toBuilder()
                .name(name.value())
                .description(description.value())
                .content(instructions.value())
                .build();
        listener.onSave(saved);
        Toast.makeText(getContext(), R.string.screen_skill_saved, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout saveButton() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 12));
        button.addView(Ui.bold(context, context.getString(R.string.screen_agent_save),
                Tokens.FONT_MD, tokens.textOnColor), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> save());
        return button;
    }

    private LinearLayout deleteButton() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, tokens.dangerMuted, 12, tokens.danger));
        button.addView(Ui.bold(context, context.getString(R.string.common_delete),
                Tokens.FONT_SM, tokens.danger), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> Dialogs.confirm(context, theme.current(),
                context.getString(R.string.screen_skill_delete_title),
                context.getString(R.string.screen_skill_delete_message, editing.name),
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_delete),
                confirmed -> {
                    if (confirmed) listener.onDelete(editing.id);
                }));
        return button;
    }

    private FormField field(int label, String value, int hint, boolean multiline) {
        return new FormField(getContext(), theme, label, value,
                getContext().getString(hint), false, multiline);
    }

    private LinearLayout card() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
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

    private LinearLayout.LayoutParams saveParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 46));
        params.bottomMargin = Ui.dp(getContext(), 12);
        return params;
    }
}
