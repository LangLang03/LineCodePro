package cn.lineai.ui.component;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;

public final class SettingsSectionView extends LinearLayout {
    private final SectionHeaderView header;
    private final LinearLayout group;

    public SettingsSectionView(Context context, String title) {
        super(context);
        setOrientation(VERTICAL);

        header = new SectionHeaderView(context, title);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        headerParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        addView(header, headerParams);

        group = new LinearLayout(context);
        group.setOrientation(VERTICAL);
        group.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        groupParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        groupParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        addView(group, groupParams);
    }

    public void addRow(View row, boolean divider) {
        addRow(row, divider, 0);
    }

    public void addRow(View row, boolean divider, int dividerInsetDp) {
        if (!divider) {
            group.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }

        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(VERTICAL);
        wrapper.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View line = new View(getContext());
        line.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        lineParams.leftMargin = LineTheme.dp(getContext(), dividerInsetDp);
        wrapper.addView(line, lineParams);
        group.addView(wrapper, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public LinearLayout getGroup() {
        return group;
    }

    public void setTitle(String title) {
        header.setText((title == null ? "" : title).toUpperCase(java.util.Locale.ROOT));
    }

    public void removeAllRows() {
        group.removeAllViews();
    }
}
