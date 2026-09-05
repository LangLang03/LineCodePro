package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.PromptTemplateItem;
import java.util.List;

public final class PromptTemplatesScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onPromptTemplateSaved(String id, String value);

        void onPromptTemplateReset(String id);
    }

    public PromptTemplatesScreenView(Context context, List<PromptTemplateItem> templates, Listener listener) {
        super(context, context.getString(R.string.screen_prompt_templates_title), listener::onBack, null);
        LinearLayout content = getContent();
        List<PromptTemplateItem> values = templates == null ? java.util.Collections.emptyList() : templates;

        SettingsSectionView intro = new SettingsSectionView(context, context.getString(R.string.screen_prompt_templates_section));
        TextView introText = LineTheme.text(context, introText(context, values), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        introText.setLineSpacing(LineTheme.dp(context, 4), 1f);
        LineTheme.padding(introText, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        intro.addRow(introText, false);
        content.addView(intro, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        for (PromptTemplateItem item : values) {
            SettingsSectionView section = new SettingsSectionView(context, item.getTitle());
            section.addRow(new PromptTemplateEditorView(context, item, listener), false);
            content.addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private static String introText(Context context, List<PromptTemplateItem> templates) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.screen_prompt_templates_variables)).append("\n");
        for (PromptTemplateItem item : templates) {
            builder.append("\n- ")
                    .append(item.getTitle())
                    .append(context.getString(R.string.screen_prompt_templates_item_separator))
                    .append(item.getDescription());
            String variables = variablesText(item);
            if (variables.length() > 0) {
                builder.append(context.getString(R.string.screen_prompt_templates_item_variables, variables));
            }
        }
        return builder.toString().trim();
    }

    private static String variablesText(PromptTemplateItem item) {
        String[] variables = item.getVariables();
        if (variables.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < variables.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("{{").append(variables[i]).append("}}");
        }
        return builder.toString();
    }

    private static final class PromptTemplateEditorView extends ScreenSurfaceView {
        private final PromptTemplateItem item;
        private final TextView statusView;
        private final EditText input;

        PromptTemplateEditorView(Context context, PromptTemplateItem item, Listener listener) {
            super(context);
            this.item = item;
            setOrientation(VERTICAL);
            LineTheme.padding(this, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

            TextView description = LineTheme.text(context, item.getDescription(), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            description.setLineSpacing(LineTheme.dp(context, 4), 1f);
            addView(description, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            TextView meta = LineTheme.text(context,
                    context.getString(R.string.screen_prompt_templates_source, item.getSourceLabel(), variablesText(item)),
                    LineTheme.FONT_XS,
                    LineTheme.TEXT_TERTIARY,
                    Typeface.NORMAL);
            meta.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            metaParams.topMargin = LineTheme.dp(context, LineTheme.SM);
            addView(meta, metaParams);

            input = new EditText(context);
            input.setText(item.getCurrentText());
            input.setTextColor(LineTheme.TEXT);
            input.setHintTextColor(LineTheme.TEXT_TERTIARY);
            input.setTextSize(LineTheme.FONT_SM);
            input.setTypeface(Typeface.MONOSPACE);
            input.setGravity(Gravity.START | Gravity.TOP);
            input.setSingleLine(false);
            input.setMinHeight(LineTheme.dp(context, 220));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
            input.setPadding(
                    LineTheme.dp(context, LineTheme.MD),
                    LineTheme.dp(context, LineTheme.MD),
                    LineTheme.dp(context, LineTheme.MD),
                    LineTheme.dp(context, LineTheme.MD)
            );
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            inputParams.topMargin = LineTheme.dp(context, LineTheme.MD);
            addView(input, inputParams);

            LinearLayout actions = new AdaptiveActionsView(context);
            actions.setOrientation(HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34));
            actionsParams.topMargin = LineTheme.dp(context, LineTheme.MD);
            addView(actions, actionsParams);

            statusView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            statusView.setGravity(Gravity.CENTER_VERTICAL);
            updateStatus(item.isCustomized());
            actions.addView(statusView, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

            LinearLayout reset = actionButton(context, IconButtonView.ROTATE_CCW, context.getString(R.string.common_reset));
            reset.setOnClickListener(v -> {
                input.setText(item.getDefaultText());
                listener.onPromptTemplateReset(item.getId());
                updateStatus(false);
                Toast.makeText(getContext(), getContext().getString(R.string.screen_prompt_templates_toast_reset), Toast.LENGTH_SHORT).show();
            });
            actions.addView(reset, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

            LinearLayout save = actionButton(context, IconButtonView.SAVE, context.getString(R.string.common_save));
            save.setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString();
                listener.onPromptTemplateSaved(item.getId(), value);
                updateStatus(!item.getDefaultText().equals(value));
                Toast.makeText(getContext(), getContext().getString(R.string.screen_prompt_templates_toast_saved), Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            saveParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            actions.addView(save, saveParams);
        }

        private void updateStatus(boolean customized) {
            statusView.setText(customized ? getContext().getString(R.string.screen_prompt_templates_status_custom) : getContext().getString(R.string.screen_prompt_templates_status_built_in));
            statusView.setTextColor(customized ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY);
        }

        private static LinearLayout actionButton(Context context, int iconType, String label) {
            LinearLayout button = new LinearLayout(context);
            button.setOrientation(HORIZONTAL);
            button.setGravity(Gravity.CENTER);
            button.setMinimumWidth(LineTheme.dp(context, 72));
            button.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 8, LineTheme.BORDER_LIGHT));
            LineTheme.padding(button, LineTheme.SM, 0, LineTheme.SM, 0);

            IconButtonView icon = new IconButtonView(context, iconType);
            icon.setIconColor(LineTheme.TEXT_SECONDARY);
            icon.setIconSizeDp(16, 16);
            icon.setClickable(false);
            button.addView(icon, new LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));

            TextView text = LineTheme.text(context, label, LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            textParams.leftMargin = LineTheme.dp(context, 5);
            button.addView(text, textParams);
            return button;
        }
    }
}
