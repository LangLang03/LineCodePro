package cn.lineai.ui.component;
import android.widget.LinearLayout;
public final class CardViewHelper {
    public interface OnCardClickListener { void onCardClick(String id); }
    private CardViewHelper() { }
    public static void addCard(LinearLayout content, String id, String title, String desc, String badge, int icon, OnCardClickListener listener) {
        ActionRowView row = new ActionRowView(content.getContext(), icon, title, desc, false, true, () -> listener.onCardClick(id));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = cn.lineai.ui.theme.LineTheme.dp(content.getContext(), 12);
        content.addView(row, params);
    }
}
