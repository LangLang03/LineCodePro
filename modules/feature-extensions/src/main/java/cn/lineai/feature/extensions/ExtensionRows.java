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

/**
 * Rows from the C++ extension screens whose labels are data (names, URLs, tool titles).
 *
 * <p>These rows accept CharSequence content so the extension pages can render user data verbatim;
 * callers must never hard-code tool names into row titles.</p>
 */
final class ExtensionRows {

    /** Utility class; not instantiable. */
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

        /**
         * Builds the switch row.
         *
         * @param context Android context used to inflate views
         * @param theme theme binder shared with the page
         * @param glyph icon resource shown before the labels
         * @param name row title text
         * @param desc row description text; may be null or empty to hide the label
         */
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
            setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
            theme.bind(t -> {
                icon.setIconColor(t.textSecondary);
                title.setTextColor(t.text);
                description.setTextColor(t.textTertiary);
                description.setVisibility(desc == null || desc.length() == 0 ? GONE : VISIBLE);
                ExtensionKit.switchTint(toggle, t);
            });
        }

        /**
         * Sets the switch state without firing the toggle listener.
         *
         * @param value checked state to display
         * @return this row for chaining
         */
        SwitchRow checked(boolean value) {
            binding = true;
            toggle.setChecked(value);
            binding = false;
            return this;
        }

        /**
         * Registers the listener invoked for user-initiated toggle changes.
         *
         * @param value consumer receiving the new checked state
         * @return this row for chaining
         */
        SwitchRow onToggle(Consumer<Boolean> value) {
            listener = value;
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (!binding && listener != null) listener.accept(checked);
            });
            return this;
        }

        /**
         * Registers a long-press action on the row.
         *
         * @param action action to run on long press
         * @return this row for chaining
         */
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

        /**
         * Builds the option row; clicks toggle the selected state and re-style the row.
         *
         * @param context Android context used to inflate views
         * @param theme theme binder shared with the page
         * @param glyph icon resource shown before the labels
         * @param name row title text
         * @param desc row description text; may be null or empty
         * @param selected initial selected state
         * @param click action invoked after every click, or null to style only
         */
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

        /** Applies the selected or unselected visual treatment from the given tokens. */
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
