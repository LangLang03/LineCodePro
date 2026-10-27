package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.lineai.data.api.StorageStats;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.ScreenBar;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/** Storage usage summary with per-category sizes and a refresh action. */
public final class StorageManagementPage extends LinearLayout {

    public interface Listener {
        void onRefresh();
    }

    private final ThemeBinder theme;
    private final Listener listener;
    private final TextView total;
    private final TextView diffSize;
    private final TextView diffCount;
    private final TextView chatSize;
    private final TextView chatCount;
    private final TextView configSize;
    private final TextView configCount;
    private final TextView homeSize;
    private final TextView homeCount;

    public StorageManagementPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context);
        this.theme = new ThemeBinder(tokens);
        this.listener = listener;
        setOrientation(VERTICAL);
        theme.bind(t -> setBackgroundColor(t.bg));

        ScreenBar bar = new ScreenBar(context, theme, R.string.screen_storage_title,
                Icons.CHEVRON_LEFT, R.string.common_back, back,
                Icons.REFRESH_CW, R.string.common_refresh, listener::onRefresh);
        addView(bar, new LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        Ui.padding(content, 16, 16, 16, 100);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1f));

        Tokens current = tokens;
        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(VERTICAL);
        summary.setBackground(Ui.rounded(context, current.surfaceElevated, 12));
        Ui.padding(summary, 16, 16, 16, 16);
        summary.addView(Ui.medium(context, context.getString(R.string.screen_storage_counted),
                Tokens.FONT_XS, current.textTertiary), new LayoutParams(-1, -2));
        total = Ui.bold(context, context.getString(R.string.screen_storage_calculating),
                Tokens.FONT_XXL, current.text);
        LinearLayout.LayoutParams valueParams = new LayoutParams(-1, -2);
        valueParams.topMargin = Ui.dp(context, 4);
        summary.addView(total, valueParams);
        TextView note = Ui.text(context, context.getString(R.string.screen_storage_summary),
                Tokens.FONT_XS, current.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams noteParams = new LayoutParams(-1, -2);
        noteParams.topMargin = Ui.dp(context, 4);
        summary.addView(note, noteParams);
        LinearLayout.LayoutParams summaryParams = new LayoutParams(-1, -2);
        summaryParams.bottomMargin = Ui.dp(context, 12);
        content.addView(summary, summaryParams);
        theme.bind(t -> summary.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));

        diffSize = new TextView(context);
        diffCount = new TextView(context);
        content.addView(row(context, Icons.GIT_COMPARE, R.string.screen_storage_row_diff_cache,
                R.string.screen_storage_desc_diff, diffSize, diffCount), rowParams(context));
        chatSize = new TextView(context);
        chatCount = new TextView(context);
        content.addView(row(context, Icons.MESSAGE_SQUARE, R.string.screen_storage_row_chat,
                R.string.screen_storage_desc_chat, chatSize, chatCount), rowParams(context));
        configSize = new TextView(context);
        configCount = new TextView(context);
        content.addView(row(context, Icons.SETTINGS, R.string.screen_storage_row_config,
                R.string.screen_storage_desc_config, configSize, configCount), rowParams(context));
        homeSize = new TextView(context);
        homeCount = new TextView(context);
        content.addView(row(context, Icons.FOLDER, R.string.screen_storage_row_home,
                R.string.screen_storage_desc_home, homeSize, homeCount), rowParams(context));
        listener.onRefresh();
    }

    private LinearLayout row(Context context, int icon, int title, int description,
                             TextView size, TextView count) {
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(row, 12, 12, 12, 12);
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 19));
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(tokens.accent);
        glyph.setIconSizeDp(38, 19);
        glyph.setClickable(false);
        iconBox.addView(glyph, new FrameLayout.LayoutParams(
                Ui.dp(context, 38), Ui.dp(context, 38), Gravity.CENTER));
        row.addView(iconBox, new LayoutParams(Ui.dp(context, 38), Ui.dp(context, 38)));
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LayoutParams(0, -2, 1f);
        textParams.leftMargin = textParams.rightMargin = Ui.dp(context, 12);
        row.addView(text, textParams);
        text.addView(Ui.bold(context, context.getString(title), Tokens.FONT_MD, tokens.text),
                new LayoutParams(-1, -2));
        TextView desc = Ui.text(context, context.getString(description), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        text.addView(desc, descParams);
        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(VERTICAL);
        meta.setGravity(Gravity.END);
        size.setText("-");
        size.setTextColor(tokens.text);
        size.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        size.setTextSize(Tokens.FONT_MD);
        meta.addView(size, new LayoutParams(-2, -2));
        count.setText("-");
        count.setTextColor(tokens.textTertiary);
        count.setTextSize(Tokens.FONT_XS);
        LinearLayout.LayoutParams countParams = new LayoutParams(-2, -2);
        countParams.topMargin = Ui.dp(context, 2);
        meta.addView(count, countParams);
        row.addView(meta, new LayoutParams(-2, -2));
        theme.bind(t -> {
            row.setBackground(Ui.rounded(context, t.surfaceElevated, 12));
            iconBox.setBackground(Ui.rounded(context, t.accentMuted, 19));
            glyph.setIconColor(t.accent);
        });
        return row;
    }

    private LinearLayout.LayoutParams rowParams(Context context) {
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(context, 8);
        return params;
    }

    public void render(StorageStats stats) {
        if (stats == null) return;
        Context context = getContext();
        total.setText(format(stats.totalBytes()));
        diffSize.setText(format(stats.diffBytes));
        diffCount.setText(context.getString(R.string.screen_storage_count,
                stats.diffCount));
        chatSize.setText(format(stats.chatBytes));
        chatCount.setText(context.getString(R.string.screen_storage_count, stats.chatCount));
        configSize.setText(format(stats.configBytes));
        configCount.setText(context.getString(R.string.screen_storage_count, stats.configCount));
        homeSize.setText(format(stats.homeBytes));
        homeCount.setText(context.getString(R.string.screen_storage_count, stats.homeCount));
    }

    static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024L * 1024 * 1024)) + " GB";
    }
}
