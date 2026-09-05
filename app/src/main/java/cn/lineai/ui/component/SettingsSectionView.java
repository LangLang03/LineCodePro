package cn.lineai.ui.component;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;
public final class SettingsSectionView extends LinearLayout {
    private final SectionHeaderView header;
    private final LinearLayout group;
    public SettingsSectionView(Context context, String title) {
        super(context); setOrientation(VERTICAL);
        header = new SectionHeaderView(context, title);
        LayoutParams hp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hp.topMargin = LineTheme.dp(context, 28); hp.bottomMargin = LineTheme.dp(context, 8);
        addView(header, hp);
        group = new LinearLayout(context); group.setOrientation(VERTICAL);
        LayoutParams gp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gp.leftMargin = gp.rightMargin = LineTheme.dp(context, 16);
        addView(group, gp);
    }
    public void addRow(View row, boolean divider) { addRow(row, divider, 0); }
    public void addRow(View row, boolean divider, int inset) {
        if (row == null) return;
        if (row.getParent() instanceof ViewGroup) ((ViewGroup)row.getParent()).removeView(row);
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = group.getChildCount() == 0 ? 0 : LineTheme.dp(getContext(), 8);
        group.addView(row, rowParams);
    }
    public LinearLayout getGroup() { return group; }
    public void setTitle(String title) { header.setText(title == null ? "" : title); }
    public void removeAllRows() { group.removeAllViews(); }
}
