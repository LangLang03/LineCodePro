package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;

import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/** Rows from the C++ extension screens whose labels are data (names, URLs, tool titles). */
final class ExtensionRows {

    private ExtensionRows() {
    }

    /** C++ InstalledRow: 68dp, 20dp secondary glyph, 16/11 labels and a trailing switch. */
    static final class SwitchRow extends LinearLayout {

        private final ThemeBinder theme;
        private final IconButton icon;
        private final TextView title;
        private final TextView description;
        private final Switch toggle;
        private Consumer<Boolean> listener;
        private boolean binding;

        SwitchRow(Context context, ThemeBinder theme, int glyph, CharSequence name,
                  CharSequence desc) {
            super(context);
            this.theme = theme;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(context, 68));
            Ui.padding(this, 16, 16, 16, 16);
            icon = new IconButton(context, glyph);
            icon.setIconSizeDp(20, 20);
            icon.setClickable(false);
            addView(icon, new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(VERTICAL);
            LayoutParams labelParams = new LayoutParams(0, -2, 1f);
            labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 12);
            addView(labels, labelParams);
            title = Ui.medium(context, name, Tokens.FONT_MD, theme.current().text);
            labels.addView(title, new LayoutParams(-1, -2));
            description = Ui.text(context, desc, Tokens.FONT_XS,
                    theme.current().textTertiary, Typeface.NORMAL);
            LayoutParams descParams = new LayoutParams(-1, -2);
            descParams.topMargin = Ui.dp(context, 2);
            labels.addView(description, descParams);
            toggle = new Switch(context);
            toggle.setContentDescription(name);
            addView(toggle, new LayoutParams(-2, -2));
            theme.bind(t -> {
                icon.setIconColor(t.textSecondary);
                title.setTextColor(t.text);
                description.setTextColor(t.textTertiary);
                description.setVisibility(desc == null || desc.length() == 0 ? GONE : VISIBLE);
                ExtensionKit.switchTint(toggle, t);
            });
        }

        SwitchRow checked(boolean value) {
            binding = true;
            toggle.setChecked(value);
            binding = false;
            return this;
        }

        SwitchRow onToggle(Consumer<Boolean> value) {
            listener = value;
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (!binding && listener != null) listener.accept(checked);
            });
            return this;
        }

        SwitchRow onLongClick(Runnable action) {
            setOnLongClickListener(v -> {
                action.run();
                return true;
            });
            return this;
        }
    }

    /** C++ AgentOptionRow: 56dp selectable row with an accent-muted selected state. */
    static final class OptionRow extends LinearLayout {

        private final ThemeBinder theme;
        private final IconButton icon;
        private final TextView title;
        private final TextView description;
        private final int glyph;
        private boolean active;

        OptionRow(Context context, ThemeBinder theme, int glyph, CharSequence name,
                  CharSequence desc, boolean selected, Runnable click) {
            super(context);
            this.theme = theme;
            this.glyph = glyph;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(context, 56));
            Ui.padding(this, 16, 12, 16, 12);
            this.active = selected;
            icon = new IconButton(context, glyph);
            icon.setIconSizeDp(20, 20);
            icon.setClickable(false);
            addView(icon, new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(VERTICAL);
            LayoutParams labelParams = new LayoutParams(0, -2, 1f);
            labelParams.leftMargin = Ui.dp(context, 12);
            addView(labels, labelParams);
            title = Ui.text(context, name, Tokens.FONT_MD, theme.current().text, Typeface.NORMAL);
            labels.addView(title, new LayoutParams(-1, -2));
            description = Ui.text(context, desc, Tokens.FONT_XS,
                    theme.current().textTertiary, Typeface.NORMAL);
            LayoutParams descParams = new LayoutParams(-1, -2);
            descParams.topMargin = Ui.dp(context, 2);
            labels.addView(description, descParams);
            theme.bind(t -> restyle(t));
            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> {
                active = !active;
                restyle(theme.current());
                if (click != null) click.run();
            });
        }

        private void restyle(Tokens tokens) {
            setBackgroundColor(active ? tokens.accentMuted : Color.TRANSPARENT);
            setSelected(active);
            icon.setIconColor(active ? tokens.accent : tokens.textSecondary);
            title.setTextColor(active ? tokens.accent : tokens.text);
            title.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif",
                    Typeface.NORMAL));
            description.setTextColor(tokens.textTertiary);
        }
    }
}
