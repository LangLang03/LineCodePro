package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import cn.lineai.ui.theme.LineTheme;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;

public final class MarkdownInlineRenderer {
    private final Context context;
    private MarkdownLinkHandler linkHandler;

    public MarkdownInlineRenderer(Context context) {
        this.context = context;
    }

    public CharSequence render(Node container) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendChildren(builder, container);
        return builder;
    }

    public void setLinkHandler(MarkdownLinkHandler linkHandler) {
        this.linkHandler = linkHandler;
    }

    public MarkdownLinkHandler getLinkHandler() {
        return linkHandler;
    }

    private void appendChildren(SpannableStringBuilder builder, Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            appendNode(builder, child);
            child = child.getNext();
        }
    }

    private void appendNode(SpannableStringBuilder builder, Node node) {
        if (node instanceof Text) {
            builder.append(((Text) node).getLiteral());
            return;
        }
        if (node instanceof Code) {
            appendInlineCode(builder, ((Code) node).getLiteral());
            return;
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            builder.append('\n');
            return;
        }
        if (node instanceof Emphasis) {
            spanChildren(builder, node, new StyleSpan(Typeface.ITALIC));
            return;
        }
        if (node instanceof StrongEmphasis) {
            spanChildren(builder, node, new StyleSpan(Typeface.BOLD));
            return;
        }
        if (node instanceof Link) {
            Link link = (Link) node;
            int start = builder.length();
            appendChildren(builder, node);
            int end = builder.length();
            if (end > start && link.getDestination() != null && link.getDestination().length() > 0) {
                String destination = link.getDestination();
                builder.setSpan(linkHandler == null ? new URLSpan(destination) : new MarkdownLinkSpan(destination, linkHandler),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(LineTheme.ACCENT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (node instanceof Image) {
            Image image = (Image) node;
            int start = builder.length();
            appendChildren(builder, node);
            String alt = builder.subSequence(start, builder.length()).toString();
            builder.delete(start, builder.length());
            builder.append("[图片");
            if (alt.trim().length() > 0) {
                builder.append(": ").append(alt.trim());
            }
            builder.append(']');
            return;
        }
        if (node instanceof HtmlInline) {
            builder.append(((HtmlInline) node).getLiteral());
            return;
        }
        appendChildren(builder, node);
    }

    private void spanChildren(SpannableStringBuilder builder, Node node, Object span) {
        int start = builder.length();
        appendChildren(builder, node);
        int end = builder.length();
        if (end > start) {
            builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void appendInlineCode(SpannableStringBuilder builder, String literal) {
        int start = builder.length();
        builder.append(literal == null ? "" : literal);
        int end = builder.length();
        if (end <= start) {
            return;
        }
        builder.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(LineTheme.TEXT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new BackgroundColorSpan(LineTheme.CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
