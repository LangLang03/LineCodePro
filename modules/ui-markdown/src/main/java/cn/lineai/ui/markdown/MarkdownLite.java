package cn.lineai.ui.markdown;

import android.content.Context;
import android.widget.LinearLayout;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import cn.lineai.ui.tokens.Tokens;

/** CommonMark blocks with retained views and independently updatable colours. */
public final class MarkdownLite extends LinearLayout {
    private final Parser parser = Parser.builder()
            .extensions(Collections.singletonList(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final List<AstBlockRenderer> blocks = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final InlineContent inline;
    private Tokens tokens;
    private String lastMarkdown;
    private boolean codeWrapEnabled;

    public MarkdownLite(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens == null ? Tokens.of(null) : tokens;
        inline = new InlineContent(this.tokens);
        setOrientation(VERTICAL);
        setClipToPadding(false);
    }

    public void render(String markdown, Tokens current) {
        String source = markdown == null ? "" : markdown;
        Tokens next = current == null ? tokens : current;
        if (next != tokens) {
            tokens = next;
            inline.setTokens(next);
            for (AstBlockRenderer block : blocks) {
                block.applyTheme(next);
            }
        }
        if (source.equals(lastMarkdown)) {
            return;
        }
        Node document = parser.parse(source);
        reconcile(document, source);
        lastMarkdown = source;
    }

    public void setCodeWrapEnabled(boolean enabled) {
        if (codeWrapEnabled == enabled) {
            return;
        }
        codeWrapEnabled = enabled;
        for (AstBlockRenderer block : blocks) {
            block.setCodeWrapEnabled(enabled);
        }
    }

    public void setLinkHandler(Consumer<String> handler) {
        inline.setLinkHandler(handler);
    }

    private void reconcile(Node document, String source) {
        int index = 0;
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof LinkReferenceDefinition) {
                continue;
            }
            String key = blockKey(node, source);
            if (index < keys.size() && key.equals(keys.get(index))) {
                index++;
                continue;
            }
            AstBlockRenderer block = new AstBlockRenderer(getContext(), inline, tokens,
                    node, codeWrapEnabled);
            if (index < blocks.size()) {
                removeView(blocks.set(index, block).view());
                keys.set(index, key);
            } else {
                blocks.add(block);
                keys.add(key);
            }
            addView(block.view(), index++);
        }
        while (blocks.size() > index) {
            removeView(blocks.remove(index).view());
            keys.remove(index);
        }
    }

    private String blockKey(Node node, String source) {
        StringBuilder key = new StringBuilder(node.getClass().getName());
        for (SourceSpan span : node.getSourceSpans()) {
            key.append('\n').append(source, span.getInputIndex(),
                    span.getInputIndex() + span.getLength());
        }
        appendResolvedLinks(key, node);
        return key.toString();
    }

    private void appendResolvedLinks(StringBuilder key, Node node) {
        String destination = null;
        if (node instanceof Link) {
            destination = ((Link) node).getDestination();
        } else if (node instanceof Image) {
            destination = ((Image) node).getDestination();
        }
        if (destination != null) {
            key.append('\u0000').append(destination.length()).append(':').append(destination);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            appendResolvedLinks(key, child);
        }
    }
}
