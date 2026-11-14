package cn.lineai.ui.kit;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.tokens.ThemeBinder;
import java.util.Locale;

/**
 * Titled group of rows used on settings-style pages.
 *
 * <p>The section renders an uppercase heading followed by a vertical group with an elevated rounded
 * background. Rows are appended with {@link #row}; the section never rebuilds its children, and
 * restyling on theme changes goes through {@link ThemeBinder} bindings that mutate the existing
 * views in place.</p>
 */
public final class SettingsSection extends LinearLayout {
    private final LinearLayout group;
    private final ThemeBinder theme;

    /**
     * Creates a section titled from a string resource.
     *
     * @param context hosting context
     * @param title resource id of the section heading; rendered uppercase
     * @param theme binder used to register restyle callbacks; never null
     */
    public SettingsSection(Context context, int title, ThemeBinder theme) {
        this(context, context.getString(title), theme);
    }

    /**
     * Creates a section titled from raw text.
     *
     * @param context hosting context
     * @param title heading text; a null value is rendered as an empty heading
     * @param theme binder used to register restyle callbacks; never null
     */
    public SettingsSection(Context context, CharSequence title, ThemeBinder theme) {
        super(context);
        this.theme = theme;
        setOrientation(VERTICAL);
        TextView label = Ui.medium(context, title == null ? "" : title.toString().toUpperCase(Locale.ROOT),
                11, theme.current().textTertiary);
        label.setLetterSpacing(0.05f);
        Ui.padding(label, 16, 0, 16, 0);
        LayoutParams heading = new LayoutParams(-1, -2);
        heading.topMargin = Ui.dp(context, 20);
        heading.bottomMargin = Ui.dp(context, 12);
        addView(label, heading);
        group = new LinearLayout(context);
        group.setOrientation(VERTICAL);
        LayoutParams body = new LayoutParams(-1, -2);
        body.leftMargin = body.rightMargin = Ui.dp(context, 16);
        addView(group, body);
        theme.bind(t -> {
            label.setTextColor(t.textTertiary);
            group.setBackground(Ui.rounded(context, t.surfaceElevated, 12));
        });
    }

    /**
     * Appends a row to the section, optionally followed by a hairline divider.
     *
     * @param row view to append; width matches the section, height wraps content
     * @param divider true to draw a 1px separator under the row
     * @param inset left inset of the divider in dp, letting it start after a leading icon
     */
    public void row(View row, boolean divider, int inset) {
        group.addView(row, new LayoutParams(-1, -2));
        if (!divider) return;
        View line = new View(getContext());
        LayoutParams params = new LayoutParams(-1, 1);
        params.leftMargin = Ui.dp(getContext(), inset);
        group.addView(line, params);
        theme.bind(t -> line.setBackgroundColor(t.borderLight));
    }
}
