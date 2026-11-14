package cn.lineai.ui.kit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.ui.tokens.ThemeBinder;
import java.util.function.Consumer;

/**
 * Single settings row rendered as icon, title, optional description and one trailing affordance
 * determined by {@link Style}.
 *
 * <p>The row builds its children once at construction; every later appearance change (selection,
 * theme, checked state) restyles the existing views in place through the shared binder, never by
 * removing or re-adding children. Switch changes made programmatically through {@link #checked}
 * are suppressed from the toggle listener so callers can reflect state without re-entering their
 * own handler.</p>
 */
public final class PreferenceRow extends LinearLayout {
    /**
     * Trailing affordance and sizing variant of the row.
     *
     * <p>{@code NAVIGATION} and {@code ACTION} use the large icon box and a trailing chevron;
     * {@code SWITCH} embeds a toggle; {@code OPTION} is a compact selectable chip-style row without
     * chevron.</p>
     */
    public enum Style { NAVIGATION, ACTION, SWITCH, OPTION }
    private final ThemeBinder theme;
    private final Style style;
    private final IconButton icon;
    private final TextView title;
    private final TextView description;
    private final Switch toggle;
    private IconButton chevron;
    private boolean selected;
    private boolean binding;

    /**
     * Builds the row for the given style. All strings are resolved eagerly so later theme passes
     * never need the resources again.
     *
     * @param context hosting context
     * @param theme binder shared with the owning page; restyle callbacks are registered on it
     * @param style row variant selecting trailing affordance and metrics
     * @param glyph icon id from {@link Icons} shown in the leading box
     * @param label resource id of the row title
     * @param description resource id of the secondary line
     */
    public PreferenceRow(Context context, ThemeBinder theme, Style style,
                         int glyph, int label, int description) {
        super(context);
        this.theme = theme;
        this.style = style;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(this, 16, style == Style.SWITCH ? 16 : 12, 16, style == Style.SWITCH ? 16 : 12);
        if (style == Style.ACTION) setMinimumHeight(Ui.dp(context, 68));
        if (style == Style.OPTION) setMinimumHeight(Ui.dp(context, 56));
        boolean large = style == Style.ACTION || style == Style.NAVIGATION;
        int size = large ? 36 : 20;
        icon = new IconButton(context, glyph);
        icon.setIconSizeDp(size, 20);
        icon.setClickable(false);
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.addView(icon, new FrameLayout.LayoutParams(Ui.dp(context, size), Ui.dp(context, size), Gravity.CENTER));
        addView(iconBox, new LayoutParams(Ui.dp(context, size), Ui.dp(context, size)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LayoutParams labelParams = new LayoutParams(0, -2, 1);
        labelParams.leftMargin = Ui.dp(context, 12);
        labelParams.rightMargin = style == Style.OPTION ? 0 : Ui.dp(context, 12);
        addView(labels, labelParams);
        title = style == Style.OPTION
                ? Ui.text(context, context.getString(label), 16, theme.current().text, Typeface.NORMAL)
                : Ui.medium(context, context.getString(label), 16, theme.current().text);
        labels.addView(title, new LayoutParams(-1, -2));
        this.description = Ui.text(context, context.getString(description), 11,
                theme.current().textTertiary, Typeface.NORMAL);
        if (style == Style.ACTION) this.description.setLineSpacing(Ui.dp(context, 3), 1);
        LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(this.description, descParams);
        toggle = style == Style.SWITCH ? new Switch(context) : null;
        if (toggle != null) {
            toggle.setContentDescription(context.getString(label));
            addView(toggle, new LayoutParams(-2, -2));
        }
        if (large) addChevron(context);
        setFocusable(true);
        theme.bind(t -> {
            this.description.setTextColor(t.textTertiary);
            if (large) iconBox.setBackground(Ui.rounded(context, t.accentMuted, style == Style.ACTION ? 8 : 18));
            restyle();
        });
    }

    /** Adds the trailing chevron; called at build time for large styles only. */
    private void addChevron(Context context) {
        chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconSizeDp(20, style == Style.ACTION ? 17 : 16);
        chevron.setClickable(false);
        addView(chevron, new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        theme.bind(t -> chevron.setIconColor(t.textTertiary));
    }

    /** Replaces the title text, for rows whose label depends on runtime state. */
    public void titleText(CharSequence value) {
        title.setText(value);
    }

    /**
     * Replaces the secondary line and collapses the row when the text is empty.
     *
     * @param value new description; null or empty hides the line entirely
     */
    public void descriptionText(CharSequence value) {
        description.setText(value);
        description.setVisibility(value == null || value.length() == 0 ? GONE : VISIBLE);
    }

    /** Hides the trailing chevron for non-navigating rows. */
    public void chevron(boolean visible) {
        if (chevron != null) chevron.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * Wires a switch row to a change listener. Clicking anywhere in the row flips the switch, and
     * the listener runs only for user-initiated flips, never for programmatic {@link #checked}
     * updates.
     *
     * @param change consumer invoked with the new checked state on user interaction
     */
    public void onToggle(Consumer<Boolean> change) {
        toggle.setOnCheckedChangeListener((button, value) -> { if (!binding) change.accept(value); });
        setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
    }

    /**
     * Reflects the switch state without firing the listener registered via {@link #onToggle}.
     *
     * @param value new checked state
     */
    public void checked(boolean value) {
        binding = true;
        toggle.setChecked(value);
        binding = false;
    }

    /**
     * Enables or disables the row together with its embedded switch, keeping the two controls
     * consistent for both touch and screen-reader use.
     */
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (toggle != null) toggle.setEnabled(enabled);
    }

    /**
     * Marks the row as the currently active choice; option rows highlight their background and
     * medium typeface, other styles tint icon and title with the accent color.
     *
     * @param value true to render the selected state
     */
    public void active(boolean value) {
        selected = value;
        setSelected(value);
        restyle();
    }

    /** Reapplies all palette-dependent styling to the existing children, in place. */
    private void restyle() {
        cn.lineai.ui.tokens.Tokens t = theme.current();
        boolean large = style == Style.ACTION || style == Style.NAVIGATION;
        icon.setIconColor(large || selected ? t.accent : t.textSecondary);
        title.setTextColor(selected ? t.accent : t.text);
        if (style == Style.OPTION) {
            title.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            setBackgroundColor(selected ? t.accentMuted : Color.TRANSPARENT);
        }
        if (toggle != null) {
            int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
            toggle.setThumbTintList(new ColorStateList(states, new int[]{t.accent, t.textTertiary}));
            toggle.setTrackTintList(new ColorStateList(states, new int[]{t.accentDim, t.surfaceLight}));
        }
    }
}
