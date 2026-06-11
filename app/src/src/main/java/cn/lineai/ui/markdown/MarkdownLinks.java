package cn.lineai.ui.markdown;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.widget.TextView;

final class MarkdownLinks {
    private MarkdownLinks() {
    }

    static void apply(TextView textView, CharSequence text, MarkdownLinkHandler handler) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text == null ? "" : text);
        Linkify.addLinks(builder, Linkify.WEB_URLS);
        if (handler != null) {
            replaceUrlSpans(builder, handler);
        }
        textView.setText(builder);
        boolean hasLinks = builder.getSpans(0, builder.length(), ClickableSpan.class).length > 0;
        textView.setLinksClickable(hasLinks);
        textView.setMovementMethod(hasLinks ? LinkMovementMethod.getInstance() : null);
        textView.setHighlightColor(Color.TRANSPARENT);
    }

    private static void replaceUrlSpans(SpannableStringBuilder builder, MarkdownLinkHandler handler) {
        URLSpan[] spans = builder.getSpans(0, builder.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            int flags = builder.getSpanFlags(span);
            String url = span.getURL();
            builder.removeSpan(span);
            if (start >= 0 && end > start) {
                builder.setSpan(new MarkdownLinkSpan(url, handler), start, end,
                        flags == 0 ? Spanned.SPAN_EXCLUSIVE_EXCLUSIVE : flags);
            }
        }
    }
}
