package cn.lineai.ui.theme;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.R;
import cn.lineai.ui.theme.LineTheme;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

public final class ThinkingBlockView extends LinearLayout {
    private static final Map<String, Boolean> EXPANDED_BY_ID = new HashMap<>();
    private static final long STREAMING_PULSE_MS = 900L;
    private static final float STREAMING_PULSE_MIN_ALPHA = 0.45f;

    private final LinearLayout header;
    private final TextView labelView;
    private final IconButtonView chevronView;
    private final BoundedScrollView contentScrollView;
    private final TextView contentView;
    private String messageId = "";
    private boolean expanded;
    private ObjectAnimator pulseAnimator;
    private boolean streaming;

    public ThinkingBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        LineTheme.padding(header, 0, 4, 0, 4);
        this.header = header;

        TextView mark = LineTheme.text(context, "✦", 10, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        header.addView(mark, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        labelView = LineTheme.text(context, context.getString(R.string.thinking_label), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(context, 4);
        header.addView(labelView, labelParams);

        chevronView = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevronView.setIconColor(LineTheme.TEXT_TERTIARY);
        chevronView.setIconSizeDp(12, 12);
        chevronView.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 12), LineTheme.dp(context, 12));
        chevronParams.leftMargin = LineTheme.dp(context, 4);
        header.addView(chevronView, chevronParams);
        header.setOnClickListener(v -> {
            expanded = !expanded;
            EXPANDED_BY_ID.put(messageId, expanded);
            updateExpanded();
        });
        addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        contentScrollView = new BoundedScrollView(context);
        contentScrollView.setFillViewport(false);
        contentScrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        contentScrollView.setVerticalScrollBarEnabled(true);

        contentView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        contentView.setLineSpacing(LineTheme.dp(context, 4), 1f);
        contentScrollView.addView(contentView, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        addView(contentScrollView, contentParams);
    }

    public void bind(String id, String content, boolean streaming) {
        bind(id, content, streaming, false, true);
    }

    public void bind(String id, String content, boolean streaming, boolean autoExpand, boolean scrollable) {
        messageId = id == null ? "" : id;
        Boolean savedExpanded = EXPANDED_BY_ID.get(messageId);
        expanded = savedExpanded == null ? autoExpand : savedExpanded;
        labelView.setText(streaming
                ? getContext().getString(R.string.thinking_label)
                : getContext().getString(R.string.thinking_done_label));
        contentView.setText(styledContent(content == null ? "" : content));
        contentView.setMaxLines(Integer.MAX_VALUE);
        contentScrollView.setMaxHeightDp(scrollable ? 180 : 0);
        updateExpanded();
        setStreaming(streaming);
    }

    private void setStreaming(boolean streaming) {
        if (this.streaming == streaming) {
            return;
        }
        this.streaming = streaming;
        if (streaming) {
            pulseAnimator = ObjectAnimator.ofFloat(header, View.ALPHA, 1f, STREAMING_PULSE_MIN_ALPHA);
            pulseAnimator.setDuration(STREAMING_PULSE_MS);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnimator.start();
        } else {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
            header.setAlpha(1f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    static CharSequence styledContent(String content) {
        InlineEmphasisParser.Parsed parsed = InlineEmphasisParser.parse(content);
        SpannableString styled = new SpannableString(parsed.getText());
        for (InlineEmphasisParser.Span span : parsed.getSpans()) {
            styled.setSpan(
                    new StyleSpan(typefaceStyle(span.getStyle())),
                    span.getStart(),
                    span.getEnd(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return styled;
    }

    private static int typefaceStyle(int style) {
        if (style == InlineEmphasisParser.BOLD) {
            return Typeface.BOLD;
        }
        if (style == InlineEmphasisParser.ITALIC) {
            return Typeface.ITALIC;
        }
        return Typeface.BOLD_ITALIC;
    }

    private void updateExpanded() {
        chevronView.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        contentScrollView.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }
}
