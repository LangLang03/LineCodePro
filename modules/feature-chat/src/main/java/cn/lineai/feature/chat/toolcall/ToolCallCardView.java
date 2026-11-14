package cn.lineai.feature.chat.toolcall;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cn.lineai.feature.chat.ToolPresentationProvider;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Card for a single tool call.
 *
 * <p>The collapsed card is one row: a 28dp accent icon well, a bold title with a monospace
 * target/command summary, a status label and a chevron. Expanding reveals the renderer-specific
 * body (diff, shell output, agent result, TODO list...). The chat group owns the per-card
 * disclosure so rebinding a streaming result keeps what the user opened.</p>
 */
public class ToolCallCardView extends LinearLayout {

    private final LinearLayout content;
    private final List<Runnable> appliers = new ArrayList<>();
    private ToolPresentationProvider provider;
    private ToolDiffLookup diffLookup;
    private ToolCardRenderer renderer = GenericToolRenderer.INSTANCE;
    private ToolPresentation presentation;
    private Tokens tokens;
    private ToolCallEntry entry;
    private ToolCardFrame frame;
    private String boundCallId = "";
    private String lastSignature = "";
    private boolean expanded;
    private boolean expandable;
    private Consumer<Boolean> expandedListener;

    /** Builds the vertical content container; the frame supplies header and body rows. */
    public ToolCallCardView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        Ui.padding(this, 0, 0, 0, 0);
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    /** Replaces the presentation source and rebinds; the icon picks the renderer. */
    public void setProvider(ToolPresentationProvider value) {
        provider = value;
        if (entry != null) bind(entry, tokens);
    }

    /** Replaces the diff bridge and rebinds so write cards refresh their diff. */
    public void setDiffLookup(ToolDiffLookup value) {
        diffLookup = value;
        if (entry != null) bind(entry, tokens);
    }

    /** Presentation source handed to nested cards and renderers. */
    ToolPresentationProvider provider() {
        return provider;
    }

    /** Diff bridge handed to write-style renderers. */
    ToolDiffLookup diffLookup() {
        return diffLookup;
    }

    /**
     * Rebinds the card: resolves presentation and renderer, resets the expansion only when the
     * call id changes, and skips the rebuild when the signature is unchanged.
     */
    public void bind(ToolCallEntry value, Tokens current) {
        tokens = current;
        if (value == null) {
            setVisibility(GONE);
            entry = null;
            return;
        }
        setVisibility(VISIBLE);
        entry = value;
        presentation = provider == null ? null
                : provider.presentation(value.name, value.arguments);
        if (presentation == null) {
            presentation = ToolPresentation.generic(value.name, value.arguments);
        }
        renderer = ToolRenderers.renderer(presentation.icon);
        expandable = renderer.expandable(value, presentation);
        if (!value.callId.equals(boundCallId)) {
            boundCallId = value.callId;
            expanded = expandable && renderer.initiallyExpanded();
        }
        if (!expandable) expanded = false;
        String signature = signature(value, presentation);
        if (signature.equals(lastSignature) && frame != null) {
            frame.applyTokens(tokens);
            return;
        }
        lastSignature = signature;
        render();
    }

    /** Applies tokens through the live frame; no rebuild, so rows are preserved. */
    public void applyTokens(Tokens current) {
        tokens = current;
        if (frame != null) {
            frame.applyTokens(current);
        } else {
            setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
    }

    /** Programmatic expansion used by restores; does not notify the listener. */
    public void setExpanded(boolean value) {
        if (!expandable || entry == null) return;
        expanded = value;
        render();
    }

    /** Current expansion state. */
    public boolean isExpanded() {
        return expanded;
    }

    /** Whether this card exposes a chevron and body at all. */
    boolean isExpandable() {
        return expandable;
    }

    /** Notified only for user toggles; programmatic restores do not echo back. */
    public void setOnExpandedChanged(Consumer<Boolean> listener) {
        expandedListener = listener;
    }

    /** Chevron tap: flips the expansion and notifies the owning group. */
    void toggleExpanded() {
        if (!expandable) return;
        expanded = !expanded;
        render();
        if (expandedListener != null) expandedListener.accept(expanded);
    }

    /** Everything the card renders; identical signatures skip the rebuild. */
    private static String signature(ToolCallEntry value, ToolPresentation presentation) {
        StringBuilder builder = new StringBuilder();
        builder.append(value.callId).append('|').append(value.name)
                .append('|').append(value.arguments.length())
                .append('|').append(value.result.length())
                .append('|').append(value.failed ? 1 : 0)
                .append('|').append(value.running ? 1 : 0)
                .append('|').append(value.diffId)
                .append('|').append(value.reviewState)
                .append('|').append(presentation.icon)
                .append('|').append(presentation.title)
                .append('|').append(presentation.action);
        return builder.toString();
    }

    /** Rebuilds header and body through the renderer, hiding an empty header. */
    private void render() {
        content.removeAllViews();
        appliers.clear();
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setMinimumHeight(Ui.dp(getContext(), 48));
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(VERTICAL);
        body.setVisibility(GONE);
        content.addView(header, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        content.addView(body, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        frame = new ToolCardFrame(getContext(), tokens, this, header, body, appliers);
        renderer.render(frame, entry, presentation);
        if (header.getChildCount() == 0) {
            header.setMinimumHeight(0);
            header.setVisibility(GONE);
        } else {
            header.setVisibility(VISIBLE);
        }
        frame.finish();
        frame.applyTokens(tokens);
    }
}
