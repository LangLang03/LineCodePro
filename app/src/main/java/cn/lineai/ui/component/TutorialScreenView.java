package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.ui.markdown.MarkdownView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 教程屏。运行时从 assets/tutorials/{simple,pro}.md 读取 Markdown 并交给
 * {@link MarkdownView} 渲染；顶部提供简单 / 专业模式切换。
 *
 * <p>仅依赖项目内的 {@link MarkdownView}（commonmark + GFM tables），不引入新依赖；
 * 不写外链（链接策略由 {@code MarkdownLinkHandler} 注入），纯内部文档。</p>
 */
public final class TutorialScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    private static final String ASSET_SIMPLE = "tutorials/simple.md";
    private static final String ASSET_PRO = "tutorials/pro.md";

    private enum TutorialMode { SIMPLE, PRO }

    private TutorialMode currentMode;
    private final String simpleMarkdown;
    private final String proMarkdown;
    private final String fallbackMarkdown;
    private View simpleRow;
    private View proRow;
    private LinearLayout contentHost;

    public TutorialScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_tutorial_title), listener::onBack, null);
        this.currentMode = TutorialMode.SIMPLE;
        this.fallbackMarkdown = context.getString(R.string.screen_tutorial_fallback);
        this.simpleMarkdown = readAsset(context, ASSET_SIMPLE, fallbackMarkdown);
        this.proMarkdown = readAsset(context, ASSET_PRO, fallbackMarkdown);
        buildLayout();
    }

    private void buildLayout() {
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.MD, LineTheme.LG, 100);

        LinearLayout selector = new LinearLayout(getContext());
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_ELEVATED, 16, LineTheme.BORDER_LIGHT));
        content.addView(selector, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        simpleRow = addVariant(selector,
                getContext().getString(R.string.screen_tutorial_simple_label),
                getContext().getString(R.string.screen_tutorial_simple_desc),
                currentMode == TutorialMode.SIMPLE,
                new Runnable() {
                    @Override
                    public void run() {
                        switchTo(TutorialMode.SIMPLE);
                    }
                });
        proRow = addVariant(selector,
                getContext().getString(R.string.screen_tutorial_pro_label),
                getContext().getString(R.string.screen_tutorial_pro_desc),
                currentMode == TutorialMode.PRO,
                new Runnable() {
                    @Override
                    public void run() {
                        switchTo(TutorialMode.PRO);
                    }
                });

        TextView subtitle = LineTheme.text(getContext(),
                getContext().getString(R.string.screen_tutorial_subtitle_brief),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        subtitle.setLineSpacing(LineTheme.dp(getContext(), 3), 1f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        subtitleParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.MD);
        content.addView(subtitle, subtitleParams);

        contentHost = new LinearLayout(getContext());
        contentHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(contentHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        renderInto(contentHost, currentMode);
        refreshSelector();
    }

    private void switchTo(TutorialMode mode) {
        if (mode == currentMode) {
            return;
        }
        currentMode = mode;
        renderInto(contentHost, mode);
        refreshSelector();
    }

    private void refreshSelector() {
        applyVariantStyle(simpleRow, currentMode == TutorialMode.SIMPLE);
        applyVariantStyle(proRow, currentMode == TutorialMode.PRO);
    }

    private void renderInto(View host, TutorialMode mode) {
        if (!(host instanceof LinearLayout)) {
            return;
        }
        LinearLayout container = (LinearLayout) host;
        container.removeAllViews();
        String markdown = mode == TutorialMode.PRO ? proMarkdown : simpleMarkdown;
        MarkdownView markdownView = new MarkdownView(getContext());
        markdownView.setCodeWrapEnabled(true);
        markdownView.setMarkdown(markdown);
        container.addView(markdownView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private View addVariant(LinearLayout selector, String title, String desc, boolean active, final Runnable onClick) {
        final Context context = selector.getContext();
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(context, 62));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        });
        LineTheme.padding(row, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_MD,
                active ? LineTheme.ACCENT : LineTheme.TEXT, Typeface.BOLD);
        text.addView(titleView);
        TextView sub = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = LineTheme.dp(context, 2);
        text.addView(sub, subParams);

        IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
        check.setIconColor(LineTheme.ACCENT);
        check.setIconSizeDp(18, 16);
        check.setClickable(false);
        row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        check.setVisibility(active ? View.VISIBLE : View.GONE);

        selector.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void applyVariantStyle(View row, boolean active) {
        if (!(row instanceof LinearLayout)) {
            return;
        }
        LinearLayout linearRow = (LinearLayout) row;
        linearRow.setBackgroundColor(active ? LineTheme.ACCENT_MUTED : 0);
        int childCount = linearRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = linearRow.getChildAt(i);
            if (child instanceof IconButtonView) {
                child.setVisibility(active ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static String readAsset(Context context, String path, String fallback) {
        if (context == null || path == null) {
            return fallback;
        }
        try {
            InputStream input = context.getAssets().open(path);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            } finally {
                try {
                    input.close();
                } catch (Exception ignored) {
                    // ignore close failure
                }
            }
        } catch (Exception e) {
            Log.e("TutorialScreenView", "Failed to read tutorial asset: " + path, e);
            if (context != null) {
                Toast.makeText(context, context.getString(R.string.screen_tutorial_loading_error), Toast.LENGTH_SHORT).show();
            }
            return fallback;
        }
    }
}
