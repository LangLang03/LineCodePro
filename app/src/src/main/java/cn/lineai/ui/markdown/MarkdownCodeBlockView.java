package cn.lineai.ui.markdown;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

public final class MarkdownCodeBlockView extends LinearLayout {
    public MarkdownCodeBlockView(Context context, String code, String language) {
        this(context, code, language, false);
    }

    public MarkdownCodeBlockView(Context context, String code, String language, boolean wrap) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(this, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        String safeCode = code == null ? "" : code;
        String lang = language == null ? "" : language.trim();
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = LineTheme.text(context, lang, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        label.setSingleLine(true);
        header.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        IconButtonView copyButton = new IconButtonView(context, IconButtonView.COPY);
        copyButton.setContentDescription("复制代码");
        copyButton.setIconColor(LineTheme.TEXT_TERTIARY);
        copyButton.setIconPaddingDp(5, 4, 5, 4);
        copyButton.setOnClickListener(v -> copyCode(safeCode));
        header.addView(copyButton, new LinearLayout.LayoutParams(LineTheme.dp(context, 28), LineTheme.dp(context, 24)));

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(header, headerParams);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        TextView text = LineTheme.text(context, safeCode, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(LineTheme.dp(context, 3), 1.0f);
        text.setSingleLine(false);
        text.setHorizontallyScrolling(!wrap);
        if (wrap) {
            addView(text, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        scroll.addView(text, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void copyCode(String code) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("LineCode code", code == null ? "" : code));
            Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
        }
    }
}
