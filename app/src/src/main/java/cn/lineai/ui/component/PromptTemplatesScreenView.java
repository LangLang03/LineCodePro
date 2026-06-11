package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.ui.theme.LineTheme;
import java.util.List;

public final class PromptTemplatesScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onPromptTemplateSaved(String id, String value);

        void onPromptTemplateReset(String id);
    }

    public PromptTemplatesScreenView(Context context, List<PromptTemplateItem> templates, Listener listener) {
        super(context, "自定义提示词", listener::onBack, null);
        LinearLayout content = getContent();
        List<PromptTemplateItem> values = templates == null ? java.util.Collections.emptyList() : templates;

        SettingsSectionView intro = new SettingsSectionView(context, "模板说明");
        TextView introText = LineTheme.text(context, introText(values), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
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

    private static String introText(List<PromptTemplateItem> templates) {
        StringBuilder builder = new StringBuilder();
        builder.append("模板变量使用 {{变量名}} 形式，保存后下一次模型请求立即生效。重置会恢复内置模板。\n");
        for (PromptTemplateItem item : templates) {
            builder.append("\n- ")
                    .append(item.getTitle())
                    .append("：")
                    .append(item.getDescription());
            String variables = variablesText(item);
            if (variables.length() > 0) {
                builder.append("\n  变量：").append(variables);
            }
        }
        return builder.toString().trim();
    }

    private static String variablesText(PromptTemplateItem item) {
        String[] variables = item.getVariables();
        if (variables.length == 0) {
            return "无";
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

    private static final class PromptTemplateEditorView extends LinearLayout {
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
                    item.getSourceLabel() + "\n变量：" + variablesText(item),
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

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34));
            actionsParams.topMargin = LineTheme.dp(context, LineTheme.MD);
            addView(actions, actionsParams);

            statusView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            statusView.setGravity(Gravity.CENTER_VERTICAL);
            updateStatus(item.isCustomized());
            actions.addView(statusView, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

            LinearLayout reset = actionButton(context, IconButtonView.ROTATE_CCW, "重置");
            reset.setOnClickListener(v -> {
                input.setText(item.getDefaultText());
                listener.onPromptTemplateReset(item.getId());
                updateStatus(false);
                Toast.makeText(getContext(), "已重置", Toast.LENGTH_SHORT).show();
            });
            actions.addView(reset, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

            LinearLayout save = actionButton(context, IconButtonView.SAVE, "保存");
            save.setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString();
                listener.onPromptTemplateSaved(item.getId(), value);
                updateStatus(!item.getDefaultText().equals(value));
                Toast.makeText(getContext(), "已保存", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            saveParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            actions.addView(save, saveParams);
        }

        private void updateStatus(boolean customized) {
            statusView.setText(customized ? "已自定义" : "使用内置模板");
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
