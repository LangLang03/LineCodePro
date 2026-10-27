package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.model.ModelPreset;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Entry screen for adding a model: a custom endpoint, or one of the provider presets. */
public final class ModelAddOptionsPage extends SettingsPage {

    public interface Listener {
        void onCustom();

        void onProvider(String presetId);

        void onLocal();

        default boolean localModelsAvailable() {
            return false;
        }
    }

    public ModelAddOptionsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_model_add_options_title, tokens, back);
        Ui.padding(content, 16, 16, 16, 16);
        addLargeCard(Icons.SLIDERS_HORIZONTAL, R.string.screen_model_add_options_custom,
                R.string.screen_model_add_options_custom_desc, listener::onCustom);
        if (listener.localModelsAvailable()) {
            addLargeCard(Icons.CPU, R.string.screen_model_add_options_local,
                    R.string.screen_model_add_options_local_desc, listener::onLocal);
        }

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconButton boxes = new IconButton(context, Icons.BOXES);
        boxes.setIconColor(tokens.textSecondary);
        boxes.setIconSizeDp(16, 16);
        boxes.setClickable(false);
        header.addView(boxes, new LinearLayout.LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        TextView title = Ui.bold(context, context.getString(R.string.screen_model_add_options_section_presets),
                Tokens.FONT_SM, tokens.textSecondary);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = Ui.dp(context, 4);
        header.addView(title, titleParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = Ui.dp(context, 20);
        headerParams.bottomMargin = Ui.dp(context, 8);
        content.addView(header, headerParams);

        for (ModelPreset preset : ModelPreset.all()) addProvider(preset, listener);
    }

    private void addLargeCard(int iconType, int label, int description, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(Ui.dp(context, 92));
        card.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.borderLight));
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
        Ui.padding(card, 16, 16, 16, 16);
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 8));
        IconButton icon = new IconButton(context, iconType);
        icon.setIconColor(tokens.accent);
        icon.setIconSizeDp(44, 22);
        icon.setClickable(false);
        iconBox.addView(icon, new FrameLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44), Gravity.CENTER));
        card.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = textParams.rightMargin = Ui.dp(context, 12);
        card.addView(text, textParams);
        text.addView(Ui.bold(context, context.getString(label), Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView desc = Ui.text(context, context.getString(description), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = Ui.dp(context, 4);
        text.addView(desc, descParams);
        IconButton chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(19, 17);
        chevron.setClickable(false);
        card.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(context, 19), Ui.dp(context, 19)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(context, 8);
        content.addView(card, params);
        theme.bind(t -> {
            card.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.borderLight));
            iconBox.setBackground(Ui.rounded(context, t.accentMuted, 8));
            icon.setIconColor(t.accent);
        });
    }

    private void addProvider(ModelPreset preset, Listener listener) {
        Context context = getContext();
        Tokens tokens = theme.current();
        String name = PresetText.label(context, preset.id);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.borderLight));
        row.setClickable(true);
        row.setOnClickListener(v -> listener.onProvider(preset.id));
        Ui.padding(row, 12, 12, 12, 12);
        TextView initial = Ui.bold(context, name.substring(0, 1), Tokens.FONT_MD, tokens.accent);
        initial.setGravity(Gravity.CENTER);
        initial.setBackground(Ui.rounded(context, tokens.accentMuted, 8));
        row.addView(initial, new LinearLayout.LayoutParams(Ui.dp(context, 38), Ui.dp(context, 38)));
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = textParams.rightMargin = Ui.dp(context, 12);
        row.addView(text, textParams);
        TextView title = Ui.bold(context, name, Tokens.FONT_MD, tokens.text);
        title.setSingleLine(true);
        text.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView sub = Ui.text(context, PresetText.description(context, preset.id) + " · "
                        + PresetText.protocol(context, preset.protocol),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(context, 3);
        text.addView(sub, subParams);
        IconButton chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(18, 16);
        chevron.setClickable(false);
        row.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(context, 8);
        content.addView(row, params);
        theme.bind(t -> {
            row.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.borderLight));
            initial.setBackground(Ui.rounded(context, t.accentMuted, 8));
            initial.setTextColor(t.accent);
        });
    }
}
