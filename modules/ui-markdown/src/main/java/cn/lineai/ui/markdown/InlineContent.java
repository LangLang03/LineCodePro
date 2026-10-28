package cn.lineai.ui.markdown;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.TextView;

import org.commonmark.node.AbstractVisitor;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

import cn.lineai.ui.md.R;
import cn.lineai.ui.tokens.Tokens;

final class InlineContent {
    private Tokens tokens;
    private Consumer<String> linkHandler;

    InlineContent(Tokens tokens) {
        this.tokens = tokens;
    }

    void setTokens(Tokens tokens) {
        this.tokens = tokens;
    }

    void setLinkHandler(Consumer<String> handler) {
        linkHandler = handler;
    }

    void bind(TextView view, Node node) {
        InlineVisitor visitor = new InlineVisitor(view.getContext().getString(R.string.markdown_image_label));
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            child.accept(visitor);
        }
        view.setText(visitor.text, TextView.BufferType.SPANNABLE);
        if (visitor.text.getSpans(0, visitor.text.length(), ClickableSpan.class).length > 0) {
            view.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    static boolean isWebLink(String destination) {
        if (destination == null || destination.isEmpty()) {
            return false;
        }
        for (int i = 0; i < destination.length(); i++) {
            char value = destination.charAt(i);
            if (Character.isWhitespace(value) || Character.isISOControl(value) || value == '\\') {
                return false;
            }
        }
        try {
            URI uri = new URI(destination);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (URISyntaxException invalid) {
            return false;
        }
    }

    private void open(View view, String destination) {
        if (!isWebLink(destination)) {
            return;
        }
        if (linkHandler != null) {
            linkHandler.accept(destination);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(destination));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            view.getContext().startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            android.widget.Toast.makeText(view.getContext(),
                    cn.lineai.ui.i18n.R.string.browser_open_failed, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private final class InlineVisitor extends AbstractVisitor {
        private final SpannableStringBuilder text = new SpannableStringBuilder();
        private final String imageLabel;
        private int linkDepth;

        private InlineVisitor(String imageLabel) {
            this.imageLabel = imageLabel;
        }

        @Override
        public void visit(Text node) {
            text.append(node.getLiteral());
        }

        @Override
        public void visit(HtmlInline node) {
            text.append(node.getLiteral());
        }

        @Override
        public void visit(SoftLineBreak node) {
            text.append('\n');
        }

        @Override
        public void visit(HardLineBreak node) {
            text.append('\n');
        }

        @Override
        public void visit(Emphasis node) {
            styledChildren(node, new StyleSpan(Typeface.ITALIC));
        }

        @Override
        public void visit(StrongEmphasis node) {
            styledChildren(node, new StyleSpan(Typeface.BOLD));
        }

        @Override
        public void visit(Code node) {
            int start = text.length();
            text.append(node.getLiteral());
            span(new TypefaceSpan("monospace"), start);
            span(new CodeColors(), start);
        }

        @Override
        public void visit(Link node) {
            int start = text.length();
            boolean allowed = isWebLink(node.getDestination());
            if (allowed) {
                linkDepth++;
            }
            visitChildren(node);
            if (allowed) {
                linkDepth--;
                span(new WebLink(node.getDestination()), start);
            }
        }

        @Override
        public void visit(Image node) {
            int start = text.length();
            visitChildren(node);
            if (text.length() == start) {
                text.append(imageLabel);
            }
            if (linkDepth == 0 && isWebLink(node.getDestination())) {
                span(new WebLink(node.getDestination()), start);
            }
        }

        private void styledChildren(Node node, Object style) {
            int start = text.length();
            visitChildren(node);
            span(style, start);
        }

        private void span(Object style, int start) {
            if (text.length() > start) {
                text.setSpan(style, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private final class CodeColors extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setColor(tokens.text);
            paint.bgColor = tokens.codeBg;
        }
    }

    private final class WebLink extends ClickableSpan {
        private final String destination;

        private WebLink(String destination) {
            this.destination = destination;
        }

        @Override
        public void onClick(View widget) {
            open(widget, destination);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setColor(tokens.accent);
            paint.setUnderlineText(true);
        }
    }
}
