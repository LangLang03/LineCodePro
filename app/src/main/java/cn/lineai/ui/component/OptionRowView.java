package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class OptionRowView extends LinearLayout {
    private final IconButtonView icon;
    private final TextView labelView;
    private boolean active;

    public OptionRowView(Context context, int iconType, String label, String desc, boolean active, Runnable onClick) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(LineTheme.dp(context, 56));
        LineTheme.padding(this, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);

        icon = new IconButtonView(context, iconType);
        icon.setIconSizeDp(20, 20);
        icon.setClickable(false);
        addView(icon, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        contentParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        addView(content, contentParams);

        labelView = LineTheme.text(context, label, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        content.addView(labelView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (desc != null && desc.length() > 0) {
            TextView descView = LineTheme.text(context, desc, 14, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 6);
            content.addView(descView, descParams);
        }

        setActive(active);
        if (onClick != null) {
            setClickable(true);
            setOnClickListener(v -> onClick.run());
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        setBackground(active ? LineTheme.roundedStroke(getContext(), LineTheme.INPUT_BG, 12, LineTheme.ACCENT) : LineTheme.pressable(getContext()));
        setSelected(active);
        icon.setIconColor(LineTheme.TEXT_SECONDARY);
        labelView.setTextColor(LineTheme.TEXT);
        labelView.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    }

    public boolean isActive() {
        return active;
    }
}
