package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class ActionRowView extends LinearLayout {
    public ActionRowView(Context context, int iconType, String label, String desc, boolean destructive, boolean showChevron, Runnable onClick) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(LineTheme.dp(context, 68));
        LineTheme.padding(this, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);

        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, destructive ? LineTheme.DANGER_MUTED : LineTheme.ACCENT_MUTED, 8));
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(destructive ? LineTheme.DANGER : LineTheme.ACCENT);
        icon.setIconSizeDp(36, 20);
        icon.setClickable(false);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36), Gravity.CENTER));
        addView(iconWrap, new LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36)));

        LinearLayout textWrap = new LinearLayout(context);
        textWrap.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        addView(textWrap, textParams);

        TextView title = LineTheme.textMedium(context, label, LineTheme.FONT_MD, destructive ? LineTheme.DANGER : LineTheme.TEXT);
        textWrap.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (desc != null && desc.length() > 0) {
            TextView description = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            description.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            textWrap.addView(description, descParams);
        }

        if (showChevron) {
            IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
            chevron.setIconColor(LineTheme.TEXT_TERTIARY);
            chevron.setIconSizeDp(20, 17);
            chevron.setClickable(false);
            addView(chevron, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));
        }

        if (onClick != null) {
            setClickable(true);
            setOnClickListener(v -> onClick.run());
        }
    }
}
