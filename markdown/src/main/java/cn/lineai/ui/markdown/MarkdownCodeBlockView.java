package cn.lineai.ui.markdown;
import cn.lineai.ui.theme.LineTheme;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.ui.markdown.R;

public final class MarkdownCodeBlockView extends LinearLayout {
    public MarkdownCodeBlockView(Context context, String code, String language) {
        this(context, code, language, false);
    }

    public MarkdownCodeBlockView(Context context, String code, String language, boolean wrap) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.rounded(context, LineTheme.CODE_BG, 12));
        LineTheme.padding(this, 16, 8, 16, 16);

        String safeCode = code == null ? "" : code;
        String lang = language == null ? "" : language.trim();
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = LineTheme.text(context, lang, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        label.setSingleLine(true);
        header.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        ImageButton copyButton = new ImageButton(context);
        copyButton.setImageResource(R.drawable.ic_lucide_copy);
        copyButton.setContentDescription(getContext().getString(R.string.markdown_code_copy_desc));
        copyButton.setColorFilter(LineTheme.TEXT_TERTIARY, PorterDuff.Mode.SRC_IN);
        copyButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
        copyButton.setAdjustViewBounds(false);
        copyButton.setBackgroundColor(Color.TRANSPARENT);
        copyButton.setClickable(true);
        copyButton.setFocusable(true);
        float density = context.getResources().getDisplayMetrics().density;
        copyButton.setPadding(Math.round(15 * density), Math.round(15 * density), Math.round(15 * density), Math.round(15 * density));
        copyButton.setOnClickListener(v -> copyCode(safeCode));
        header.addView(copyButton, new LinearLayout.LayoutParams(LineTheme.dp(context, 48), LineTheme.dp(context, 48)));

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(header, headerParams);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        TextView text = LineTheme.text(context, safeCode, 13, LineTheme.TEXT, Typeface.NORMAL);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(LineTheme.dp(context, 6), 1.0f);
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
            Toast.makeText(getContext(), getContext().getString(R.string.markdown_code_copied), Toast.LENGTH_SHORT).show();
        }
    }
}
