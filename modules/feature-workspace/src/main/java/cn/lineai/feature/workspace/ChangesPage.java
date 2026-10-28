package cn.lineai.feature.workspace;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.lineai.core.model.FileDiff;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** File changes recorded by tools, with one-tap rollback. */
public final class ChangesPage extends SettingsPage implements cn.lineai.ui.kit.Refreshable {

    public interface Listener {
        List<FileDiff> load();

        boolean revert(String id);
    }

    private final Listener listener;

    public ChangesPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.workspace_changes_title, tokens, back);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        reload();
    }

    @Override public void onShown() {
        reload();
    }

    public void reload() {
        content.removeAllViews();
        List<FileDiff> diffs = listener.load();
        if (diffs.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.workspace_changes_empty),
                    Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            Ui.padding(empty, 16, 48, 16, 48);
            content.addView(empty, new LayoutParams(-1, -2));
            return;
        }
        for (FileDiff diff : diffs) {
            content.addView(row(diff), rowParams());
        }
    }

    private LinearLayout row(FileDiff diff) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(card, 14, 12, 14, 12);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView path = Ui.bold(context, diff.path, Tokens.FONT_MD, tokens.text);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        header.addView(path, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView time = Ui.text(context, formatTime(diff.createdAt), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        header.addView(time, new LinearLayout.LayoutParams(-2, -2));
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView stats = Ui.text(context, stats(diff), Tokens.FONT_XS,
                tokens.textSecondary, Typeface.NORMAL);
        stats.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.topMargin = Ui.dp(context, 6);
        card.addView(stats, statsParams);

        if (diff.reverted) {
            TextView reverted = Ui.medium(context,
                    context.getString(R.string.workspace_changes_reverted),
                    Tokens.FONT_XS, tokens.textTertiary);
            LinearLayout.LayoutParams revertedParams = new LinearLayout.LayoutParams(-2, -2);
            revertedParams.topMargin = Ui.dp(context, 8);
            card.addView(reverted, revertedParams);
        } else {
            TextView revert = Ui.bold(context,
                    context.getString(R.string.workspace_changes_revert),
                    Tokens.FONT_SM, tokens.accent);
            revert.setClickable(true);
            Ui.padding(revert, 0, 8, 0, 2);
            revert.setOnClickListener(v -> {
                boolean ok = listener.revert(diff.id);
                Toast.makeText(context, ok ? R.string.workspace_changes_reverted_toast
                        : R.string.workspace_changes_failed, Toast.LENGTH_SHORT).show();
                reload();
            });
            LinearLayout.LayoutParams revertParams = new LinearLayout.LayoutParams(-2, -2);
            revertParams.topMargin = Ui.dp(context, 6);
            card.addView(revert, revertParams);
        }
        theme.bind(t -> card.setBackground(
                Ui.roundedStroke(context, t.surfaceElevated, 12, t.border)));
        return card;
    }

    private static String stats(FileDiff diff) {
        int before = diff.before.isEmpty() ? 0 : diff.before.split("\n", -1).length;
        int after = diff.after.isEmpty() ? 0 : diff.after.split("\n", -1).length;
        int delta = after - before;
        return (delta >= 0 ? "+" + delta : String.valueOf(delta)) + " lines"
                + "  ·  " + before + " → " + after;
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 8);
        return params;
    }
}
