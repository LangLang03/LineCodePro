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

/**
 * CommonMark renderer that retains one view per top-level block and updates it in place.
 *
 * <p>Incremental renderer built on the commonmark parser (with the GFM tables extension). Two
 * render paths share the retained-view pool: {@link #render} reconciles the block list against
 * source-span keys so unchanged blocks keep their views (and their text selection), and
 * {@link #renderIncremental} materialises a fresh document in chunks posted to the main queue so a
 * long document never blocks a frame. Theme changes always restyle the retained views in place;
 * only source changes replace views.</p>
 */
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
    private long passGeneration;
    private Node passDocument;
    private Node passCursor;
    private String passSource;
    private Runnable passComplete;

    /**
     * Creates an empty renderer.
     *
     * @param context hosting context
     * @param tokens initial palette; null falls back to the default token set
     */
    public MarkdownLite(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens == null ? Tokens.of(null) : tokens;
        inline = new InlineContent(this.tokens);
        setOrientation(VERTICAL);
        setClipToPadding(false);
    }

    /**
     * Restyles retained blocks in place; a repeated token instance is a no-op. Any in-flight
     * incremental pass keeps running and picks the new palette up through the shared binder.
     *
     * @param current palette snapshot to apply; null keeps the current one
     */
    public void applyTheme(Tokens current) {
        Tokens next = current == null ? tokens : current;
        if (next == tokens) {
            return;
        }
        tokens = next;
        inline.setTokens(next);
        for (AstBlockRenderer block : blocks) {
            block.applyTheme(next);
        }
    }

    /**
     * Reconciles the rendered blocks against the source synchronously.
     *
     * <p>Blocks whose type and source spans are unchanged are retained untouched; only inserted,
     * replaced or trailing blocks are rebuilt, which preserves text selection and scroll state for
     * edited documents. An identical source is a no-op apart from the theme application, and any
     * in-flight incremental pass is cancelled first.
     *
     * @param markdown CommonMark source; null is rendered as empty
     * @param current palette snapshot applied before rendering; null keeps the current one
     */
    public void render(String markdown, Tokens current) {
        String source = markdown == null ? "" : markdown;
        applyTheme(current);
        if (source.equals(lastMarkdown)) {
            return;
        }
        cancelPass();
        Node document = parser.parse(source);
        reconcile(document, source);
        lastMarkdown = source;
    }

    /**
     * Renders the document in chunks of top-level blocks.
     *
     * <p>The first chunk is added synchronously so the caller's frame shows real content; every
     * following chunk is posted to the main queue, which keeps a long tutorial from blocking the
     * page transition. A newer pass or a detached view cancels the remaining chunks, and
     * {@code onComplete} fires exactly once when the source was fully materialised.</p>
     * @param markdown CommonMark source; null is rendered as empty
     * @param current palette snapshot applied before rendering; null keeps the current one
     * @param blocksPerChunk number of top-level blocks added per main-queue hop; values below one
     *     are clamped to one
     * @param onComplete invoked exactly once when the full source is materialised, on the main
     *     thread; may be null. Also invoked immediately when the source is unchanged and no pass is
     *     active
     */
    public void renderIncremental(String markdown, Tokens current, int blocksPerChunk,
                                  Runnable onComplete) {
        String source = markdown == null ? "" : markdown;
        applyTheme(current);
        if (passDocument == null && source.equals(lastMarkdown)) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        startPass(source, onComplete);
        buildChunk(passGeneration, Math.max(1, blocksPerChunk));
    }

    /**
     * Toggles wrap mode on every retained code block. A no-op when the mode already matches.
     *
     * @param enabled true to wrap long code lines, false to scroll them horizontally
     */
    public void setCodeWrapEnabled(boolean enabled) {
        if (codeWrapEnabled == enabled) {
            return;
        }
        codeWrapEnabled = enabled;
        for (AstBlockRenderer block : blocks) {
            block.setCodeWrapEnabled(enabled);
        }
    }

    /**
     * Registers the sink for validated link destinations clicked inside rendered text.
     *
     * @param handler consumer receiving HTTP/HTTPS destinations; may be null to restore the default
     *     system-browser behaviour
     */
    public void setLinkHandler(Consumer<String> handler) {
        inline.setLinkHandler(handler);
    }

    /**
     * Begins a fresh render pass: bumps the generation, clears the view tree and retained pools,
     * parses the source and arms the chunk cursor. The generation bump invalidates callbacks from
     * any earlier pass.
     */
    private void startPass(String source, Runnable onComplete) {
        passGeneration++;
        removeAllViews();
        blocks.clear();
        keys.clear();
        lastMarkdown = null;
        passDocument = parser.parse(source);
        passCursor = passDocument.getFirstChild();
        passSource = source;
        passComplete = onComplete;
    }

    /** Abandons any in-flight pass by bumping the generation and dropping the pass state. */
    private void cancelPass() {
        passGeneration++;
        passDocument = null;
        passCursor = null;
        passSource = null;
        passComplete = null;
    }

    /**
     * Adds at most {@code limit} top-level blocks for the given generation, then either finishes
     * the pass or posts the next chunk. Stale generations return immediately.
     */
    private void buildChunk(long pass, int limit) {
        if (pass != passGeneration) {
            return;
        }
        int added = 0;
        while (passCursor != null && added < limit) {
            Node node = passCursor;
            passCursor = node.getNext();
            if (node instanceof LinkReferenceDefinition) {
                continue;
            }
            AstBlockRenderer block = new AstBlockRenderer(getContext(), inline, tokens, node,
                    codeWrapEnabled);
            blocks.add(block);
            keys.add(blockKey(node, passSource));
            addView(block.view());
            added++;
        }
        if (passCursor == null) {
            finishPass(pass);
        } else {
            post(() -> continuePass(pass, limit));
        }
    }

    /** Continuation guard for the posted chunk runnable; stops when detached or superseded. */
    private void continuePass(long pass, int limit) {
        if (pass != passGeneration || !isAttachedToWindow()) {
            return;
        }
        buildChunk(pass, limit);
    }

    /** Commits the finished pass: records the source as rendered and fires the completion callback
     * exactly once, still guarded by the generation check. */
    private void finishPass(long pass) {
        if (pass != passGeneration) {
            return;
        }
        String source = passSource;
        Runnable complete = passComplete;
        passDocument = null;
        passCursor = null;
        passSource = null;
        passComplete = null;
        lastMarkdown = source;
        if (complete != null) {
            complete.run();
        }
    }

    /**
     * Synchronous reconcile used by {@link #render}: matches retained blocks against the new
     * document by key, replaces the first mismatch in place and trims trailing surplus blocks.
     */
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

    /**
     * Builds the retention key for a top-level block: node type plus its exact source text plus
     * every resolved link/image destination in the subtree, so a reworded destination still
     * invalidates the block even when the visible text is identical.
     */
    private String blockKey(Node node, String source) {
        StringBuilder key = new StringBuilder(node.getClass().getName());
        for (SourceSpan span : node.getSourceSpans()) {
            key.append('\n').append(source, span.getInputIndex(),
                    span.getInputIndex() + span.getLength());
        }
        appendResolvedLinks(key, node);
        return key.toString();
    }

    /** Recursively appends resolved link and image destinations of the subtree to the key. */
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
