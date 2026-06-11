package cn.lineai.ui.markdown;

import android.content.Context;
import android.widget.LinearLayout;
import java.util.Collections;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

public final class MarkdownView extends LinearLayout {
    private final Parser parser;
    private final MarkdownRenderer renderer;
    private String lastMarkdown;
    private boolean codeWrapEnabled;
    private MarkdownLinkHandler linkHandler;

    public MarkdownView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipToPadding(false);
        Iterable<Extension> extensions = Collections.singletonList(TablesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = new MarkdownRenderer(context);
    }

    public void setCodeWrapEnabled(boolean enabled) {
        if (codeWrapEnabled == enabled) {
            return;
        }
        codeWrapEnabled = enabled;
        renderer.setCodeWrapEnabled(enabled);
        rerender();
    }

    public void setLinkHandler(MarkdownLinkHandler linkHandler) {
        if (this.linkHandler == linkHandler) {
            return;
        }
        this.linkHandler = linkHandler;
        renderer.setLinkHandler(linkHandler);
        rerender();
    }

    public void setMarkdown(String markdown) {
        String value = markdown == null ? "" : markdown;
        if (value.equals(lastMarkdown)) {
            return;
        }
        lastMarkdown = value;
        renderValue(value);
    }

    private void rerender() {
        if (lastMarkdown == null) {
            return;
        }
        renderValue(lastMarkdown);
    }

    private void renderValue(String value) {
        removeAllViews();
        if (value.trim().length() == 0) {
            return;
        }
        Node document = parser.parse(value);
        renderer.renderInto(this, document);
    }
}
