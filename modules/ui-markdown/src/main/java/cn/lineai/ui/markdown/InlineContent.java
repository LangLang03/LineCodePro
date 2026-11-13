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

/**
 * Flattens commonmark inline nodes into spanned text on a {@link TextView}.
 *
 * <p>Package-private by design; {@link MarkdownLite} owns one instance and hands it to every block
 * renderer so inline styling and link handling stay uniform. The visitor produces spans only (no
 * image decoding): inline code gets a monospace color span, emphasis gets style spans, links are
 * wrapped in a {@link ClickableSpan} guarded by {@link #isWebLink}, and images render as their
 * alt-text label, itself linked when the parent is not already inside a link.</p>
 */
final class InlineContent {
    private Tokens tokens;
    private Consumer<String> linkHandler;

    /**
     * Creates the binder with an initial palette.
     *
     * @param tokens palette snapshot used for code and link spans at bind time
     */
    InlineContent(Tokens tokens) {
        this.tokens = tokens;
    }

    /**
     * Replaces the palette for subsequently bound text; retained spans repaint through the TextView
     * on the next invalidate.
     *
     * @param tokens new palette snapshot; null keeps the current one
     */
    void setTokens(Tokens tokens) {
        this.tokens = tokens;
    }

    /**
     * Registers the sink for allowed link destinations. When unset, links open through the system
     * browser intent path in {@link #open}.
     *
     * @param handler consumer receiving validated HTTP/HTTPS destinations; may be null to restore
     *     the default behaviour
     */
    void setLinkHandler(Consumer<String> handler) {
        linkHandler = handler;
    }

    /**
     * Renders the inline children of {@code node} into the view and attaches a link movement method
     * when any clickable span was produced.
     *
     * @param view target text view; its text is replaced with spannable content
     * @param node commonmark parent whose inline children are flattened
     */
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

    /**
     * Decides whether a link destination is safe to hand to a browser: HTTP or HTTPS only, with a
     * host, without user info, and free of whitespace, ISO control characters and backslashes so
     * scheme confusion and clipboard-injection tricks are rejected up front.
     *
     * @param destination raw link destination from the AST; may be null
     * @return true when the destination may be opened externally
     */
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

    /**
     * Opens a validated destination through the registered handler, or falls back to a browsable
     * system intent. Non-web destinations are dropped silently rather than resolved.
     */
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

    /**
     * Span-building visitor over the inline subtree. {@code linkDepth} tracks whether the visitor is
     * inside an allowed link so nested image destinations do not double-wrap the same range.
     */
    private final class InlineVisitor extends AbstractVisitor {
        private final SpannableStringBuilder text = new SpannableStringBuilder();
        private final String imageLabel;
        private int linkDepth;

        /** Creates the visitor carrying the localized fallback label for images without alt text. */
        private InlineVisitor(String imageLabel) {
            this.imageLabel = imageLabel;
        }

        /** Appends the literal text of the node. */
        @Override
        public void visit(Text node) {
            text.append(node.getLiteral());
        }

        /** Passes raw inline HTML through as plain text; it is never interpreted. */
        @Override
        public void visit(HtmlInline node) {
            text.append(node.getLiteral());
        }

        /** Soft breaks become newlines so paragraphs rewrap naturally. */
        @Override
        public void visit(SoftLineBreak node) {
            text.append('\n');
        }

        /** Hard breaks become newlines. */
        @Override
        public void visit(HardLineBreak node) {
            text.append('\n');
        }

        /** Wraps the rendered children in an italic style span. */
        @Override
        public void visit(Emphasis node) {
            styledChildren(node, new StyleSpan(Typeface.ITALIC));
        }

        /** Wraps the rendered children in a bold style span. */
        @Override
        public void visit(StrongEmphasis node) {
            styledChildren(node, new StyleSpan(Typeface.BOLD));
        }

        /** Renders inline code as monospace text with the code background span. */
        @Override
        public void visit(Code node) {
            int start = text.length();
            text.append(node.getLiteral());
            span(new TypefaceSpan("monospace"), start);
            span(new CodeColors(), start);
        }

        /** Wraps the children in a clickable span when the destination passes {@link #isWebLink}. */
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

        /** Renders the image as its alt text (or a fallback label), linked when not already inside
         * a link; no image is decoded. */
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

        /** Visits the children and wraps the produced range in the given span, dropping empty
         * ranges so trailing spans never leak onto following text. */
        private void styledChildren(Node node, Object style) {
            int start = text.length();
            visitChildren(node);
            span(style, start);
        }

        /** Applies the span over the produced range; a no-op when nothing was appended. */
        private void span(Object style, int start) {
            if (text.length() > start) {
                text.setSpan(style, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /** Paints inline code spans with body text color on the code background. */
    private final class CodeColors extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setColor(tokens.text);
            paint.bgColor = tokens.codeBg;
        }
    }

    /** Clickable span that routes taps through {@link #open} and paints with the accent color. */
    private final class WebLink extends ClickableSpan {
        private final String destination;

        /** Retains the validated destination for the click callback. */
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
