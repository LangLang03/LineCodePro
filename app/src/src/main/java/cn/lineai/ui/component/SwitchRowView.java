package cn.lineai.ui.component;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class SwitchRowView extends LinearLayout {
    public SwitchRowView(Context context, int iconType, String label, String desc, boolean value) {
        this(context, iconType, label, desc, value, null);
    }

    public SwitchRowView(Context context, int iconType, String label, String desc, boolean value, CompoundButton.OnCheckedChangeListener listener) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(this, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(LineTheme.TEXT_SECONDARY);
        icon.setIconSizeDp(20, 20);
        icon.setClickable(false);
        addView(icon, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        labelParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        addView(labels, labelParams);

        TextView title = LineTheme.text(context, label, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labels.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (desc != null && desc.length() > 0) {
            TextView description = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            labels.addView(description, descParams);
        }

        Switch toggle = new Switch(context);
        toggle.setChecked(value);
        tintSwitch(toggle, listener);
        addView(toggle, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
    }

    private void tintSwitch(Switch toggle, CompoundButton.OnCheckedChangeListener listener) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT, LineTheme.TEXT_TERTIARY}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT_DIM, LineTheme.SURFACE_LIGHT}));
        if (listener != null) {
            toggle.setOnCheckedChangeListener(listener);
        }
    }
}
