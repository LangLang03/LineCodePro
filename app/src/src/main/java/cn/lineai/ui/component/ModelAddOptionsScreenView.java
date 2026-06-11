package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.model.ModelProviderPresets;
import cn.lineai.ui.theme.LineTheme;

public final class ModelAddOptionsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onCustom();

        void onLocal();

        void onProvider(String id);
    }

    public ModelAddOptionsScreenView(Context context, Listener listener) {
        super(context, "添加模型", listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        addLargeCard(content, IconButtonView.SLIDERS_HORIZONTAL, "自定义模型",
                "进入原添加页，手动选择协议并填写 Base URL、模型 ID 和密钥", listener::onCustom);
        addLargeCard(content, IconButtonView.FILE_UP, "加载本地模型",
                "通过系统文件选择器导入 GGUF 文件，并在支持的 Android 设备上启用 NPU", listener::onLocal);

        LinearLayout sectionHeader = new LinearLayout(context);
        sectionHeader.setOrientation(HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        IconButtonView boxes = new IconButtonView(context, IconButtonView.BOXES);
        boxes.setIconColor(LineTheme.TEXT_SECONDARY);
        boxes.setIconSizeDp(16, 16);
        boxes.setClickable(false);
        sectionHeader.addView(boxes, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        TextView sectionTitle = LineTheme.text(context, "预置提供商", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = LineTheme.dp(context, LineTheme.XS);
        sectionHeader.addView(sectionTitle, titleParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        headerParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(sectionHeader, headerParams);

        for (ModelProviderPreset preset : ModelProviderPresets.all()) {
            addProvider(content, preset, listener);
        }
    }

    private void addLargeCard(LinearLayout content, int iconType, String title, String desc, Runnable onClick) {
        Context context = content.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(LineTheme.dp(context, 92));
        card.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER_LIGHT));
        card.setClickable(true);
        card.setOnClickListener(v -> onClick.run());
        LineTheme.padding(card, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        FrameLayout largeIcon = new FrameLayout(context);
        largeIcon.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 8));
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(44, 22);
        icon.setClickable(false);
        largeIcon.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 44), LineTheme.dp(context, 44), Gravity.CENTER));
        card.addView(largeIcon, new LinearLayout.LayoutParams(LineTheme.dp(context, 44), LineTheme.dp(context, 44)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(text, textParams);
        text.addView(LineTheme.text(context, title, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView descView = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        descView.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        text.addView(descView, descParams);

        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(19, 17);
        chevron.setClickable(false);
        card.addView(chevron, new LinearLayout.LayoutParams(LineTheme.dp(context, 19), LineTheme.dp(context, 19)));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(card, cardParams);
    }

    private void addProvider(LinearLayout content, ModelProviderPreset preset, Listener listener) {
        Context context = content.getContext();
        String name = preset.getLabel();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER_LIGHT));
        row.setClickable(true);
        row.setOnClickListener(v -> listener.onProvider(preset.getId()));
        LineTheme.padding(row, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);

        TextView initial = LineTheme.text(context, name.substring(0, 1), LineTheme.FONT_MD, LineTheme.ACCENT, Typeface.BOLD);
        initial.setGravity(Gravity.CENTER);
        initial.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 8));
        row.addView(initial, new LinearLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(text, textParams);
        TextView title = LineTheme.text(context, name, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        text.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView sub = LineTheme.text(context, preset.getDesc() + " · " + protocolLabel(preset), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = LineTheme.dp(context, 3);
        text.addView(sub, subParams);

        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(18, 16);
        chevron.setClickable(false);
        row.addView(chevron, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(row, params);
    }

    private String protocolLabel(ModelProviderPreset preset) {
        switch (preset.getProtocolType()) {
            case CODEX_RESPONSES:
                return "Codex";
            case ANTHROPIC_MESSAGES:
                return "Anthropic";
            case LOCAL_GGUF:
                return "本地 GGUF";
            case OPENAI_COMPATIBLE:
            default:
                return "OpenAI 兼容";
        }
    }
}
