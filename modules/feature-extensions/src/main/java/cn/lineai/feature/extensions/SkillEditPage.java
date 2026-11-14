package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Toast;

import cn.lineai.core.model.Skill;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit a local SKILL.md extension. */
public final class SkillEditPage extends ExtensionPage {

    /** Persistence callbacks for the editor. */
    public interface Listener {
        /**
         * Persists the edited skill.
         *
         * @param skill assembled skill with name, description, and content
         */
        void onSave(Skill skill);

        /**
         * Deletes the skill being edited.
         *
         * @param id identifier of the skill to delete
         */
        void onDelete(String id);
    }

    private final Listener listener;
    private final Skill editing;
    private final FormField name;
    private final FormField description;
    private final FormField content;

    /**
     * Builds the editor, seeding the form from the skill being edited when present.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param editing skill to edit, or null to create a new one
     * @param listener persistence callbacks
     */
    public SkillEditPage(Context context, Tokens tokens, Runnable back, Skill editing,
                         Listener listener) {
        super(context, tokens, back, editing == null || editing.id.isEmpty()
                ? R.string.screen_skill_add_title : R.string.screen_skill_edit_title);
        this.listener = listener;
        this.editing = editing == null ? Skill.builder().build() : editing;
        int title = this.editing.id.isEmpty()
                ? R.string.screen_skill_add_title : R.string.screen_skill_edit_title;
        header(title, ExtensionKit.saveAction(context, theme, this::save));

        name = ExtensionKit.field(context, theme, R.string.screen_skill_field_name,
                this.editing.name, R.string.screen_skill_hint_name, false);
        description = ExtensionKit.field(context, theme, R.string.screen_skill_field_description,
                this.editing.description, R.string.screen_skill_hint_description, false);
        content = ExtensionKit.field(context, theme, R.string.screen_skill_field_content,
                this.editing.content, R.string.screen_skill_hint_content, true);
        content.input().setMinHeight(Ui.dp(context, 220));

        SettingsSection form = section(R.string.screen_skill_form_title);
        form.row(form(name, 16, 12), false, 0);
        form.row(form(description, 0, 12), false, 0);
        form.row(form(content, 0, 16), false, 0);
        if (!this.editing.id.isEmpty()) renderDelete();
    }

    /** Wraps a form field with vertical spacing. */
    private LinearLayout form(FormField field, int topDp, int bottomDp) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(getContext(), 16);
        wrapper.setPadding(side, Ui.dp(getContext(), topDp), side, Ui.dp(getContext(), bottomDp));
        wrapper.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    /** Adds the delete section with confirmation, shown only when editing an existing skill. */
    private void renderDelete() {
        SettingsSection section = section(R.string.screen_extension_detail_delete_title);
        section.row(ExtensionKit.dangerAction(getContext(), theme, R.string.common_delete,
                () -> ExtensionKit.confirm(getContext(), theme,
                        R.string.screen_skill_delete_title,
                        getContext().getString(R.string.screen_skill_delete_message, editing.name),
                        () -> listener.onDelete(editing.id))), false, 0);
    }

    /** Validates name and content, then hands the assembled skill to the host. */
    private void save() {
        if (name.value().isEmpty() || content.value().isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_skill_save_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Skill saved = editing.toBuilder()
                .name(name.value())
                .description(description.value())
                .content(content.value())
                .build();
        listener.onSave(saved);
        Toast.makeText(getContext(), R.string.screen_skill_saved, Toast.LENGTH_SHORT).show();
    }
}
