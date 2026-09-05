package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.model.ModelProviderPresets;
import cn.lineai.ui.util.ModelProviderPresetStrings;

public final class ModelAddOptionsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onCustom();

        void onLocal();

        void onProvider(String id);
    }

    public ModelAddOptionsScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_model_add_options_title), listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, 16, 8, 16, 48);

        addLargeCard(content, IconButtonView.SLIDERS_HORIZONTAL, context.getString(R.string.screen_model_add_options_custom),
                context.getString(R.string.screen_model_add_options_custom_desc), listener::onCustom);
        addLargeCard(content, IconButtonView.FILE_UP, context.getString(R.string.screen_model_add_options_local),
                context.getString(R.string.screen_model_add_options_local_desc), listener::onLocal);

        LinearLayout sectionHeader = new LinearLayout(context);
        sectionHeader.setOrientation(HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        IconButtonView boxes = new IconButtonView(context, IconButtonView.BOXES);
        boxes.setIconColor(LineTheme.TEXT_SECONDARY);
        boxes.setIconSizeDp(16, 16);
        boxes.setClickable(false);
        sectionHeader.addView(boxes, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        TextView sectionTitle = LineTheme.text(context, context.getString(R.string.screen_model_add_options_section_presets), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.BOLD);
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
        content.addView(new ActionRowView(getContext(), iconType, title, desc, false, true, onClick), cardParams());
    }
    private void addProvider(LinearLayout content, ModelProviderPreset preset, Listener listener) {
        content.addView(new ActionRowView(getContext(), IconButtonView.BOX,
                ModelProviderPresetStrings.getLabel(getContext(), preset.getId()),
                ModelProviderPresetStrings.getDesc(getContext(), preset.getId()), false, true,
                () -> listener.onProvider(preset.getId())), cardParams());
    }

    private LayoutParams cardParams() {
        LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = LineTheme.dp(getContext(), 8);
        return params;
    }

    private String protocolLabel(ModelProviderPreset preset) {
        switch (preset.getProtocolType()) {
            case CODEX_RESPONSES:
                return "Codex";
            case ANTHROPIC_MESSAGES:
                return "Anthropic";
            case LOCAL_GGUF:
                return getContext().getString(R.string.model_provider_local_gguf);
            case OPENAI_COMPATIBLE:
            default:
                return getContext().getString(R.string.model_provider_openai_compatible);
        }
    }
}
