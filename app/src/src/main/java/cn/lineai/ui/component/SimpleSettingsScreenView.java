package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class SimpleSettingsScreenView extends LinearLayout {
    public interface Listener {
        void onBack();
    }

    public SimpleSettingsScreenView(Context context, String title, String subtitle, String[] rows, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        addView(new ScreenHeaderView(context, title, listener::onBack, null), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, LineTheme.LG, LineTheme.XL, LineTheme.LG, 100);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = LineTheme.text(context, subtitle, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            sub.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            subParams.bottomMargin = LineTheme.dp(context, LineTheme.LG);
            content.addView(sub, subParams);
        }

        LinearLayout group = new LinearLayout(context);
        group.setOrientation(VERTICAL);
        group.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        content.addView(group, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < rows.length; i++) {
            group.addView(row(context, rows[i], i < rows.length - 1), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private View row(Context context, String text, boolean divider) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(VERTICAL);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(row, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        TextView label = LineTheme.textMedium(context, text, LineTheme.FONT_MD, LineTheme.TEXT);
        row.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        View dot = new View(context);
        dot.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 4));
        row.addView(dot, new LinearLayout.LayoutParams(LineTheme.dp(context, 8), LineTheme.dp(context, 8)));
        wrapper.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (divider) {
            View line = new View(context);
            line.setBackgroundColor(LineTheme.BORDER_LIGHT);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
            lineParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
            wrapper.addView(line, lineParams);
        }
        return wrapper;
    }
}
