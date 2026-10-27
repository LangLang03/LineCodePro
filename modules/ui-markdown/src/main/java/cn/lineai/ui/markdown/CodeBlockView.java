package cn.lineai.ui.markdown;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.md.R;
import cn.lineai.ui.tokens.Tokens;

/** Wrapping changes measurement, never the code text or copy action. */
final class CodeBlockView extends LinearLayout {
    private final String code;
    private final TextView label;
    private final TextView body;
    private final IconButton copyButton;
    private final CodeViewport viewport;
    private final GradientDrawable background;
    private boolean wrap;

    CodeBlockView(Context context, Tokens tokens, String code, String language, boolean wrap) {
        super(context);
        this.code = code;
        this.wrap = wrap;
        setOrientation(VERTICAL);
        Ui.padding(this, 16, 8, 16, 16);
        background = Ui.rounded(context, tokens.codeBg, 12);
        setBackground(background);
        label = Ui.text(context, language, Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        label.setSingleLine(true);
        copyButton = new IconButton(context, Icons.COPY);
        copyButton.setIconSizeDp(48, 18);
        copyButton.setContentDescription(context.getString(R.string.markdown_code_copy_desc));
        copyButton.setOnClickListener(view -> copyCode());
        addHeader();
        body = Ui.text(context, code, 13, tokens.text, Typeface.NORMAL);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setSingleLine(false);
        body.setLineSpacing(Ui.dp(context, 6), 1f);
        body.setHorizontallyScrolling(!wrap);
        viewport = new CodeViewport(context);
        viewport.setHorizontalScrollBarEnabled(false);
        viewport.setFillViewport(wrap);
        viewport.addView(body, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(viewport, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        applyTheme(tokens);
    }

    private void addHeader() {
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        int touchSize = Ui.dp(getContext(), 48);
        header.addView(copyButton, new LayoutParams(touchSize, touchSize));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(getContext(), 8);
        addView(header, params);
    }

    void applyTheme(Tokens tokens) {
        background.setColor(tokens.codeBg);
        label.setTextColor(tokens.textTertiary);
        copyButton.setIconColor(tokens.textTertiary);
        body.setTextColor(tokens.text);
    }

    void setCodeWrapEnabled(boolean enabled) {
        if (wrap == enabled) {
            return;
        }
        wrap = enabled;
        body.setHorizontallyScrolling(!enabled);
        viewport.setFillViewport(enabled);
        if (enabled) {
            viewport.scrollTo(0, 0);
        }
        viewport.requestLayout();
        body.invalidate();
    }

    private void copyCode() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("LineCode code", code));
            Toast.makeText(getContext(), R.string.markdown_code_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private final class CodeViewport extends HorizontalScrollView {
        private CodeViewport(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int limit = Integer.MAX_VALUE;
            if (wrap && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                limit = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                        - getPaddingLeft() - getPaddingRight());
            }
            if (body.getMaxWidth() != limit) {
                body.setMaxWidth(limit);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
