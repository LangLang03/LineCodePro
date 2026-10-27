package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/** Bundled simple/pro tutorials with a chapter selector and jump chips. */
public final class TutorialPage extends SettingsPage {

    private enum Mode { SIMPLE, PRO }

    private static final String ASSET_SIMPLE = "tutorials/simple.md";
    private static final String ASSET_PRO = "tutorials/pro.md";

    private final String simpleMarkdown;
    private final String proMarkdown;
    private final List<String> simpleSections;
    private final List<String> proSections;
    private final LinearLayout simpleRow;
    private final LinearLayout proRow;
    private final LinearLayout chips;
    private final MarkdownLite markdown;
    private Mode mode = Mode.SIMPLE;

    public TutorialPage(Context context, Tokens tokens, Runnable back) {
        super(context, R.string.screen_tutorial_title, tokens, back);
        Ui.padding(content, 16, 12, 16, 100);
        simpleMarkdown = read(context, ASSET_SIMPLE);
        proMarkdown = read(context, ASSET_PRO);
        simpleSections = TutorialSections.parse(simpleMarkdown);
        proSections = TutorialSections.parse(proMarkdown);

        LinearLayout selector = new LinearLayout(context);
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 16, tokens.borderLight));
        content.addView(selector, new LayoutParams(-1, -2));
        simpleRow = variant(selector, R.string.screen_tutorial_simple_label,
                R.string.screen_tutorial_simple_desc, () -> switchTo(Mode.SIMPLE));
        proRow = variant(selector, R.string.screen_tutorial_pro_label,
                R.string.screen_tutorial_pro_desc, () -> switchTo(Mode.PRO));

        TextView subtitle = Ui.text(context, context.getString(R.string.screen_tutorial_subtitle_brief),
                Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        subtitle.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams subtitleParams = new LayoutParams(-1, -2);
        subtitleParams.topMargin = Ui.dp(context, 16);
        subtitleParams.bottomMargin = Ui.dp(context, 12);
        content.addView(subtitle, subtitleParams);

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chipScroll.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        LinearLayout.LayoutParams chipParams = new LayoutParams(-1, -2);
        chipParams.bottomMargin = Ui.dp(context, 12);
        content.addView(chipScroll, chipParams);

        markdown = new MarkdownLite(context, tokens);
        markdown.setCodeWrapEnabled(true);
        content.addView(markdown, new LayoutParams(-1, -2));
        theme.bind(t -> {
            selector.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 16, t.borderLight));
            markdown.render(currentMarkdown(), t);
        });
        render();
    }

    private void switchTo(Mode next) {
        if (next == mode) return;
        mode = next;
        render();
    }

    private void render() {
        markdown.render(currentMarkdown(), theme.current());
        refreshSelector();
        rebuildChips();
    }

    private String currentMarkdown() {
        return mode == Mode.PRO ? proMarkdown : simpleMarkdown;
    }

    private void refreshSelector() {
        style(simpleRow, mode == Mode.SIMPLE);
        style(proRow, mode == Mode.PRO);
    }

    private void style(LinearLayout row, boolean active) {
        row.setBackgroundColor(active ? theme.current().accentMuted : android.graphics.Color.TRANSPARENT);
        View check = row.getChildAt(row.getChildCount() - 1);
        check.setVisibility(active ? View.VISIBLE : View.GONE);
        TextView title = (TextView) ((LinearLayout) row.getChildAt(0)).getChildAt(0);
        title.setTextColor(active ? theme.current().accent : theme.current().text);
    }

    private LinearLayout variant(LinearLayout selector, int title, int description, Runnable onClick) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 62));
        row.setClickable(true);
        row.setOnClickListener(v -> onClick.run());
        Ui.padding(row, 16, 12, 16, 12);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LayoutParams(0, -2, 1f));
        labels.addView(Ui.bold(context, context.getString(title), Tokens.FONT_MD, tokens.text),
                new LayoutParams(-1, -2));
        TextView desc = Ui.text(context, context.getString(description), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(desc, descParams);
        IconButton check = new IconButton(context, Icons.CHECK);
        check.setIconColor(tokens.accent);
        check.setIconSizeDp(18, 16);
        check.setClickable(false);
        row.addView(check, new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));
        selector.addView(row, new LayoutParams(-1, -2));
        return row;
    }

    private void rebuildChips() {
        Context context = getContext();
        Tokens tokens = theme.current();
        chips.removeAllViews();
        List<String> sections = mode == Mode.PRO ? proSections : simpleSections;
        for (int i = 0; i < sections.size(); i++) {
            String title = sections.get(i);
            TextView chip = Ui.bold(context, (i + 1) + " " + TutorialSections.shortTitle(title),
                    Tokens.FONT_XS, tokens.textSecondary);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 14, tokens.borderLight));
            chip.setPadding(Ui.dp(context, 12), Ui.dp(context, 5), Ui.dp(context, 12), Ui.dp(context, 5));
            chip.setOnClickListener(v -> scrollTo(title));
            LinearLayout.LayoutParams params = new LayoutParams(-2, -2);
            if (i > 0) params.leftMargin = Ui.dp(context, 8);
            chips.addView(chip, params);
        }
    }

    private void scrollTo(String title) {
        List<View> candidates = new ArrayList<>();
        collect(markdown, candidates);
        for (View view : candidates) {
            if (!(view instanceof TextView)) continue;
            String text = ((TextView) view).getText().toString().trim();
            if (text.equals(title) || text.startsWith(title)) {
                int y = topWithinScroll(view);
                scroll.smoothScrollTo(0, Math.max(0, y - Ui.dp(getContext(), 8)));
                return;
            }
        }
    }

    private void collect(View parent, List<View> out) {
        if (!(parent instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            out.add(child);
            collect(child, out);
        }
    }

    private int topWithinScroll(View view) {
        int y = 0;
        View current = view;
        while (current != null && current != scroll) {
            y += current.getTop();
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return y;
    }

    private static String read(Context context, String asset) {
        try (InputStream input = context.getAssets().open(asset)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            Toast.makeText(context, R.string.screen_tutorial_loading_error, Toast.LENGTH_SHORT).show();
            return "";
        }
    }
}
