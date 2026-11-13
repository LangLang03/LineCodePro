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

/**
 * Rendered code fence: language label, copy action and a monospace body inside a horizontal
 * viewport that can switch between scrolling and wrapping.
 *
 * <p>Package-private by design; instances are created and owned by {@link AstBlockRenderer}. The
 * class-level invariant is that toggling wrap changes only measurement ({@code maxWidth}, scroll
 * flags and layout requests), never the code text or the copy action, so a theme or preference
 * change cannot destroy selection state. Restyling mutates the existing children in place.</p>
 */
final class CodeBlockView extends LinearLayout {
    private final String code;
    private final TextView label;
    private final TextView body;
    private final IconButton copyButton;
    private final CodeViewport viewport;
    private final GradientDrawable background;
    private boolean wrap;

    /**
     * Builds the block with header, body and themed background. The body is text-selectable and
     * never re-created afterwards; all later updates are in-place mutations.
     *
     * @param context hosting context
     * @param tokens palette snapshot applied at construction
     * @param code literal code text; retained verbatim for the copy action
     * @param language info-string language shown in the header; may be empty
     * @param wrap initial wrap mode; false keeps long lines inside a horizontal viewport
     */
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

    /** Assembles the language-label and copy-button header row at construction time. */
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

    /**
     * Restyles the retained children in place for a theme change.
     *
     * @param tokens new palette snapshot
     */
    void applyTheme(Tokens tokens) {
        background.setColor(tokens.codeBg);
        label.setTextColor(tokens.textTertiary);
        copyButton.setIconColor(tokens.textTertiary);
        body.setTextColor(tokens.text);
    }

    /**
     * Switches between wrapping and horizontal scrolling. A no-op when the mode already matches.
     *
     * @param enabled true to wrap long lines, false to scroll them horizontally
     */
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

    /** Copies the retained literal to the primary clip and confirms with a short toast. */
    private void copyCode() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("LineCode code", code));
            Toast.makeText(getContext(), R.string.markdown_code_copied, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Horizontal host that keeps the code body's {@code maxWidth} in sync with the viewport width:
     * unbounded in scroll mode, clamped to the measured width in wrap mode.
     */
    private final class CodeViewport extends HorizontalScrollView {
        private CodeViewport(Context context) {
            super(context);
        }

        /** Updates the body width limit from the incoming width spec before measuring. */
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
