package cn.lineai.ui.kit;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.tokens.ThemeBinder;
import java.util.Locale;

public final class SettingsSection extends LinearLayout {
    private final LinearLayout group;
    private final ThemeBinder theme;

    public SettingsSection(Context context, int title, ThemeBinder theme) {
        this(context, context.getString(title), theme);
    }

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
