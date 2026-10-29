package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Collapsible reasoning block (C++ {@code ReasoningTimelineBlock}, legacy
 * {@code ThinkingBlockView}).
 *
 * <p>The header is a 48dp disclosure row with a 13sp secondary label; the body is 13sp tertiary
 * text, capped at 180dp with its own scroll view when the thinking-scroll preference is on.</p>
 */
final class ReasoningBlockView extends LinearLayout {

    private final LinearLayout header;
    private final TextView label;
    private final IconButton chevron;
    private final CappedScrollView scroller;
    private final TextView body;
    private Tokens tokens;
    private DisclosureState disclosure;
    private boolean byDefault;
    private String key = "";
    private boolean open;
    private boolean live;
    private boolean scrollable = true;

    ReasoningBlockView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(context, 48));
        Ui.padding(header, 0, 8, 0, 8);
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> toggle());
        label = Ui.text(context, "", Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        header.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(28, 16);
        chevron.setClickable(false);
        header.addView(chevron, new LayoutParams(Ui.dp(context, 28), Ui.dp(context, 32)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        scroller = new CappedScrollView(context, Ui.dp(context, 180));
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setFillViewport(false);
        body = Ui.text(context, "", Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        body.setLineSpacing(Ui.dp(context, 3), 1f);
        scroller.addView(body, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        LayoutParams bodyParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Ui.dp(context, 4);
        addView(scroller, bodyParams);
        setPadding(0, 0, 0, Ui.dp(context, 10));
        applyTokens(tokens);
    }

    void bind(String key, String text, boolean live, boolean scrollable, boolean byDefault,
              DisclosureState disclosure) {
        this.key = key;
        this.disclosure = disclosure;
        this.byDefault = byDefault;
        this.live = live;
        if (this.scrollable != scrollable) {
            this.scrollable = scrollable;
            scroller.setMaxHeight(scrollable ? Ui.dp(getContext(), 180) : Integer.MAX_VALUE);
            scroller.setVerticalScrollBarEnabled(scrollable);
        }
        if (!text.equals(body.getText().toString())) {
            body.setText(text);
        }
        open = disclosure.isOpen(key, byDefault);
        this.live = live;
        updateLabel();
        applyState();
    }

    /** Called when the turn finishes so a live "thinking" header becomes the done label. */
    void setLive(boolean value) {
        if (live == value) {
            return;
        }
        live = value;
        updateLabel();
    }

    private void updateLabel() {
        label.setText(getContext().getString(live
                ? R.string.message_assistant_thinking
                : R.string.thinking_done_label));
    }

    void applyTokens(Tokens current) {
        tokens = current;
        label.setTextColor(current.textSecondary);
        chevron.setIconColor(current.textTertiary);
        body.setTextColor(current.textTertiary);
        body.setHighlightColor(current.accentMuted);
        body.setLinkTextColor(current.accent);
    }

    private void toggle() {
        if (disclosure == null) {
            return;
        }
        disclosure.toggle(key);
        open = disclosure.isOpen(key, byDefault);
        applyState();
    }

    private void applyState() {
        chevron.setIconType(open ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
        scroller.setVisibility(open ? VISIBLE : GONE);
    }

    /** ScrollView with a hard height cap so a long thought cannot push the answer away. */
    private static final class CappedScrollView extends ScrollView {

        private int maxHeight;

        CappedScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        void setMaxHeight(int value) {
            maxHeight = value;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = heightMeasureSpec;
            if (maxHeight > 0 && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                height = MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeight),
                        MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, height);
        }
    }
}
