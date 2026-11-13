package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Editable prompt templates with per-template reset and save. */
public final class PromptTemplatesPage extends SettingsPage {

    /** Persistence callbacks for template edits. */
    public interface Listener {
        /**
         * Persists the edited template text.
         *
         * @param id template identifier
         * @param value validated template text
         */
        void onSave(String id, String value);

        /**
         * Restores a template to its factory default.
         *
         * @param id template identifier
         */
        void onReset(String id);
    }

    private final List<Editor> editors = new ArrayList<>();

    /**
     * Builds the prompt templates page with one editor per template.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param templates catalog entries to edit; null renders an empty page
     * @param listener persistence callbacks
     */
    public PromptTemplatesPage(Context context, Tokens tokens, Runnable back,
                               List<PromptTemplateEntry> templates, Listener listener) {
        super(context, R.string.screen_prompt_templates_title, tokens, back);
        List<PromptTemplateEntry> values = templates == null
                ? Collections.<PromptTemplateEntry>emptyList() : templates;
        SettingsSection intro = section(R.string.screen_prompt_templates_section);
        TextView introText = Ui.text(context, introText(context, values),
                Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        introText.setLineSpacing(Ui.dp(context, 4), 1f);
        Ui.padding(introText, 16, 16, 16, 16);
        intro.row(introText, false, 0);
        for (PromptTemplateEntry item : values) {
            SettingsSection template = section(item.title);
            Editor editor = new Editor(context, item, listener, tokens);
            editors.add(editor);
            template.row(editor, false, 0);
        }
    }

    /** Re-tints all editors when the app theme changes. */
    @Override public void applyTokens(Tokens tokens) {
        theme.apply(tokens);
        for (Editor editor : editors) editor.applyTokens(tokens);
    }

    /**
     * Builds the intro paragraph listing every template, its description, and variables.
     *
     * @param context context used for localized strings
     * @param templates catalog entries to summarize
     * @return multi-line intro text
     */
    private static String introText(Context context, List<PromptTemplateEntry> templates) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.screen_prompt_templates_variables)).append("\n");
        for (PromptTemplateEntry item : templates) {
            builder.append("\n- ")
                    .append(context.getString(item.title))
                    .append(context.getString(R.string.screen_prompt_templates_item_separator))
                    .append(context.getString(item.description));
            String variables = variablesText(item);
            if (!variables.isEmpty()) {
                builder.append(context.getString(R.string.screen_prompt_templates_item_variables, variables));
            }
        }
        return builder.toString().trim();
    }

    /**
     * Renders a template's variable names as {@code {{NAME}}} placeholders.
     *
     * @param item entry whose variables to render
     * @return comma-separated placeholder list; empty when the template has none
     */
    static String variablesText(PromptTemplateEntry item) {
        String[] variables = item.variables;
        if (variables.length == 0) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < variables.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append("{{").append(variables[i]).append("}}");
        }
        return builder.toString();
    }

    /** One editable template card with placeholder validation, reset, and save. */
    private static final class Editor extends LinearLayout {
        private static final java.util.regex.Pattern PLACEHOLDER =
                java.util.regex.Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

        private final PromptTemplateEntry item;
        private final TextView description;
        private final TextView meta;
        private final TextView statusView;
        private final EditText input;
        private final ActionButton resetButton;
        private final ActionButton saveButton;

        /**
         * Builds the editor card for one template entry.
         *
         * @param context Android context used to inflate views
         * @param item template entry edited by this card
         * @param listener persistence callbacks
         * @param tokens design tokens for the current theme
         */
        private Editor(Context context, PromptTemplateEntry item, Listener listener, Tokens tokens) {
            super(context);
            this.item = item;
            setOrientation(VERTICAL);
            Ui.padding(this, 16, 16, 16, 16);
            description = Ui.text(context, context.getString(item.description),
                    Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
            description.setLineSpacing(Ui.dp(context, 4), 1f);
            addView(description, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            meta = Ui.text(context, context.getString(R.string.screen_prompt_templates_source,
                            item.sourceLabel, variablesText(item)),
                    Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
            meta.setLineSpacing(Ui.dp(context, 3), 1f);
            LayoutParams metaParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            metaParams.topMargin = Ui.dp(context, 8);
            addView(meta, metaParams);

            input = new EditText(context);
            input.setText(item.currentText);
            input.setTextColor(tokens.text);
            input.setHintTextColor(tokens.textTertiary);
            input.setTextSize(Tokens.FONT_SM);
            input.setTypeface(Typeface.MONOSPACE);
            input.setGravity(Gravity.START | Gravity.TOP);
            input.setSingleLine(false);
            input.setMinHeight(Ui.dp(context, 220));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setBackground(Ui.roundedStroke(context, tokens.codeBg, 8, tokens.codeBorder));
            Ui.padding(input, 12, 12, 12, 12);
            LayoutParams inputParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            inputParams.topMargin = Ui.dp(context, 12);
            addView(input, inputParams);

            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams actionsParams = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 34));
            actionsParams.topMargin = Ui.dp(context, 12);
            addView(actions, actionsParams);
            statusView = Ui.text(context, "", Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
            statusView.setGravity(Gravity.CENTER_VERTICAL);
            actions.addView(statusView, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            resetButton = new ActionButton(context, Icons.ROTATE_CCW,
                    context.getString(R.string.common_reset), tokens);
            resetButton.setOnClickListener(v -> {
                input.setText(item.defaultText);
                listener.onReset(item.id);
                updateStatus(false);
                Toast.makeText(getContext(), R.string.screen_prompt_templates_toast_reset,
                        Toast.LENGTH_SHORT).show();
            });
            actions.addView(resetButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            saveButton = new ActionButton(context, Icons.SAVE,
                    context.getString(R.string.common_save), tokens);
            saveButton.setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString();
                if (!validPlaceholders(value)) {
                    Toast.makeText(getContext(), R.string.screen_prompt_templates_invalid,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                listener.onSave(item.id, value);
                updateStatus(!item.defaultText.equals(value));
                Toast.makeText(getContext(), R.string.screen_prompt_templates_toast_saved,
                        Toast.LENGTH_SHORT).show();
            });
            LayoutParams saveParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            saveParams.leftMargin = Ui.dp(context, 8);
            actions.addView(saveButton, saveParams);
            updateStatus(item.customized());
        }

        /**
         * Validates that every {@code {{PLACEHOLDER}}} in the text is a declared variable and no
         * undeclared placeholder syntax remains.
         *
         * @param value template text to validate
         * @return true when the text only references declared variables
         */
        private boolean validPlaceholders(String value) {
            java.util.Set<String> allowed =
                    new java.util.HashSet<>(java.util.Arrays.asList(item.variables));
            java.util.regex.Matcher matcher = PLACEHOLDER.matcher(value);
            StringBuffer stripped = new StringBuffer();
            while (matcher.find()) {
                if (!allowed.contains(matcher.group(1))) return false;
                matcher.appendReplacement(stripped, "");
            }
            matcher.appendTail(stripped);
            return stripped.indexOf("{{") < 0;
        }

        /** Updates the built-in/customized status label of the editor. */
        private void updateStatus(boolean customized) {
            statusView.setText(customized
                    ? R.string.screen_prompt_templates_status_custom
                    : R.string.screen_prompt_templates_status_built_in);
        }

        /** Re-tints the editor views for the given tokens. */
        private void applyTokens(Tokens tokens) {
            description.setTextColor(tokens.textSecondary);
            meta.setTextColor(tokens.textTertiary);
            input.setTextColor(tokens.text);
            input.setHintTextColor(tokens.textTertiary);
            input.setBackground(Ui.roundedStroke(getContext(), tokens.codeBg, 8, tokens.codeBorder));
            statusView.setTextColor(item.customized() ? tokens.accent : tokens.textTertiary);
            resetButton.applyTokens(tokens);
            saveButton.applyTokens(tokens);
        }
    }

    /** Small icon-and-label button used for editor actions. */
    private static final class ActionButton extends LinearLayout {
        private final IconButton icon;
        private final TextView text;

        /**
         * Builds the action button and applies the initial tokens.
         *
         * @param context Android context used to inflate views
         * @param iconType icon resource to display
         * @param label text label of the action
         * @param tokens design tokens for the current theme
         */
        private ActionButton(Context context, int iconType, String label, Tokens tokens) {
            super(context);
            setGravity(Gravity.CENTER);
            setMinimumWidth(Ui.dp(context, 72));
            Ui.padding(this, 8, 0, 8, 0);
            icon = new IconButton(context, iconType);
            icon.setIconSizeDp(16, 16);
            icon.setClickable(false);
            addView(icon, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
            text = Ui.text(context, label, Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
            LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            textParams.leftMargin = Ui.dp(context, 5);
            addView(text, textParams);
            applyTokens(tokens);
        }

        /** Re-tints the button background, icon, and label for the given tokens. */
        private void applyTokens(Tokens tokens) {
            setBackground(Ui.roundedStroke(getContext(), tokens.surfaceLight, 8, tokens.borderLight));
            icon.setIconColor(tokens.textSecondary);
            text.setTextColor(tokens.textSecondary);
        }
    }
}
