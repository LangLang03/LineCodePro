package cn.lineai.ui.component;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.theme.IconButtonView;

/** Retains live editors and their values while a section is closed. */
public final class DisclosureSectionView extends LinearLayout {
    private final LinearLayout body;
    private final IconButtonView chevron;
    private boolean expanded;
    public DisclosureSectionView(Context context, String title, boolean open) {
        super(context); setOrientation(VERTICAL);
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(context, 52));
        header.setBackground(LineTheme.pressable(context));
        TextView label = LineTheme.textMedium(context, title, 14, LineTheme.TEXT_SECONDARY);
        header.addView(label, new LayoutParams(0, -2, 1));
        chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_SECONDARY); chevron.setIconSizeDp(24, 16); chevron.setClickable(false);
        header.addView(chevron, new LayoutParams(LineTheme.dp(context,24),LineTheme.dp(context,24)));
        addView(header, new LayoutParams(-1,-2));
        body = new LinearLayout(context); body.setOrientation(VERTICAL); addView(body,new LayoutParams(-1,-2));
        header.setOnClickListener(v -> setExpanded(!expanded)); setExpanded(open);
    }
    public LinearLayout getBody() { return body; }
    public void setExpanded(boolean open) {
        expanded = open; body.setVisibility(open ? VISIBLE : GONE); chevron.setRotation(open ? 90 : 0);
    }
    public static DisclosureSectionView foldTail(LinearLayout parent, int from, String title, boolean open) {
        DisclosureSectionView section = new DisclosureSectionView(parent.getContext(), title, open);
        while (parent.getChildCount() > from) {
            View child = parent.getChildAt(from); parent.removeViewAt(from); section.body.addView(child);
        }
        parent.addView(section,new LayoutParams(-1,-2)); return section;
    }
}
