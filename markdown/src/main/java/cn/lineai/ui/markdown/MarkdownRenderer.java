package cn.lineai.ui.markdown;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.ext.gfm.tables.TableBlock;
import java.util.HashMap;
import java.util.Map;

public final class MarkdownRenderer {
    private final Context context;
    private final MarkdownInlineRenderer inlineRenderer;
    private boolean codeWrapEnabled;
    private MarkdownLinkHandler linkHandler;
    private final Map<Class<? extends Node>, BlockRenderer> renderers = new HashMap<>();

    public MarkdownRenderer(Context context) {
        this.context = context;
        inlineRenderer = new MarkdownInlineRenderer(context);
        registerRenderers();
    }

    private void registerRenderers() {
        renderers.put(Document.class, (target, node, depth) -> renderChildren(target, node, depth));
        renderers.put(LinkReferenceDefinition.class, (target, node, depth) -> { });
        renderers.put(Heading.class, (target, node, depth) -> renderHeading(target, node, depth));
        renderers.put(Paragraph.class, (target, node, depth) -> renderParagraph(target, node, depth));
        renderers.put(FencedCodeBlock.class, (target, node, depth) -> renderFencedCodeBlock(target, node));
        renderers.put(IndentedCodeBlock.class, (target, node, depth) -> renderIndentedCodeBlock(target, node));
        renderers.put(BlockQuote.class, (target, node, depth) -> renderBlockQuote(target, node, depth));
        renderers.put(TableBlock.class, (target, node, depth) -> renderTableBlock(target, node));
        renderers.put(BulletList.class, (target, node, depth) -> addList(target, (ListBlock) node, depth));
        renderers.put(OrderedList.class, (target, node, depth) -> addList(target, (ListBlock) node, depth));
        renderers.put(ThematicBreak.class, (target, node, depth) -> renderThematicBreak(target));
        renderers.put(HtmlBlock.class, (target, node, depth) -> renderHtmlBlock(target, node));
    }

    public void renderInto(LinearLayout target, Node document) {
        renderChildren(target, document, 0);
    }

    public void setCodeWrapEnabled(boolean codeWrapEnabled) {
        this.codeWrapEnabled = codeWrapEnabled;
    }

    public void setLinkHandler(MarkdownLinkHandler linkHandler) {
        this.linkHandler = linkHandler;
        inlineRenderer.setLinkHandler(linkHandler);
    }

    private void renderChildren(LinearLayout target, Node parent, int depth) {
        Node child = parent.getFirstChild();
        while (child != null) {
            renderBlock(target, child, depth);
            child = child.getNext();
        }
    }

    private void renderBlock(LinearLayout target, Node node, int depth) {
        BlockRenderer renderer = renderers.get(node.getClass());
        if (renderer != null) {
            renderer.render(target, node, depth);
            return;
        }
        if (node.getFirstChild() != null) {
            renderChildren(target, node, depth);
        }
    }

    private void renderHeading(LinearLayout target, Node node, int depth) {
        Heading heading = (Heading) node;
        addBlock(target, new MarkdownTextBlockView(
                context,
                inlineRenderer.render(node),
                headingSize(heading.getLevel()),
                true,
                linkHandler
        ), depth == 0 ? 4 : 2, 6);
    }

    private void renderParagraph(LinearLayout target, Node node, int depth) {
        Image image = onlyImage(node);
        if (image != null) {
            addBlock(target, new MarkdownImageView(context, image.getDestination(), plainText(image)), depth == 0 ? 2 : 0, 7);
            return;
        }
        if (hasDirectImage(node)) {
            renderParagraphWithImages(target, node, depth);
            return;
        }
        CharSequence text = inlineRenderer.render(node);
        if (text.toString().trim().length() > 0) {
            addBlock(target, new MarkdownTextBlockView(context, text, LineTheme.FONT_MD, false, linkHandler), depth == 0 ? 2 : 0, 7);
        }
    }

    private void renderFencedCodeBlock(LinearLayout target, Node node) {
        FencedCodeBlock block = (FencedCodeBlock) node;
        addBlock(target, new MarkdownCodeBlockView(context, block.getLiteral(), languageFromInfo(block.getInfo()), codeWrapEnabled), 4, 8);
    }

    private void renderIndentedCodeBlock(LinearLayout target, Node node) {
        IndentedCodeBlock block = (IndentedCodeBlock) node;
        addBlock(target, new MarkdownCodeBlockView(context, block.getLiteral(), "", codeWrapEnabled), 4, 8);
    }

    private void renderBlockQuote(LinearLayout target, Node node, int depth) {
        MarkdownQuoteBlockView quote = new MarkdownQuoteBlockView(context);
        renderChildren(quote.getContent(), node, depth + 1);
        addBlock(target, quote, 3, 8);
    }

    private void renderTableBlock(LinearLayout target, Node node) {
        addBlock(target, new MarkdownTableView(context, inlineRenderer, linkHandler, (TableBlock) node), 4, 8);
    }

    private void renderThematicBreak(LinearLayout target) {
        addBlock(target, new MarkdownThematicBreakView(context), 8, 8);
    }

    private void renderHtmlBlock(LinearLayout target, Node node) {
        HtmlBlock block = (HtmlBlock) node;
        addBlock(target, new MarkdownCodeBlockView(context, block.getLiteral(), "html", codeWrapEnabled), 4, 8);
    }

    private void addList(LinearLayout target, ListBlock block, int depth) {
        MarkdownListBlockView listView = new MarkdownListBlockView(context);
        int index = 1;
        if (block instanceof OrderedList) {
            Integer start = ((OrderedList) block).getMarkerStartNumber();
            index = start == null ? 1 : start;
        }
        Node child = block.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem) {
                String marker = block instanceof OrderedList ? (index + ".") : bulletForDepth(depth);
                LinearLayout itemContent = listView.addItem(marker, depth);
                renderChildren(itemContent, child, depth + 1);
                index++;
            }
            child = child.getNext();
        }
        addBlock(target, listView, 1, 7);
    }

    private void addBlock(LinearLayout target, View view, int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = LineTheme.dp(context, topDp);
        params.bottomMargin = LineTheme.dp(context, bottomDp);
        target.addView(view, params);
    }

    private Image onlyImage(Node node) {
        Image image = null;
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Image) {
                if (image != null) {
                    return null;
                }
                image = (Image) child;
            } else if (child instanceof Text) {
                if (((Text) child).getLiteral().trim().length() > 0) {
                    return null;
                }
            } else {
                return null;
            }
            child = child.getNext();
        }
        return image;
    }

    private boolean hasDirectImage(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Image) {
                return true;
            }
            child = child.getNext();
        }
        return false;
    }

    private void renderParagraphWithImages(LinearLayout target, Node paragraph, int depth) {
        Node segmentStart = paragraph.getFirstChild();
        Node child = segmentStart;
        boolean renderedAny = false;
        while (child != null) {
            Node next = child.getNext();
            if (child instanceof Image) {
                renderedAny = addTextSegment(target, segmentStart, child, depth, renderedAny) || renderedAny;
                int top = renderedAny ? 1 : depth == 0 ? 2 : 0;
                addBlock(target, new MarkdownImageView(context, ((Image) child).getDestination(), plainText(child)), top, 7);
                renderedAny = true;
                segmentStart = next;
            }
            child = next;
        }
        addTextSegment(target, segmentStart, null, depth, renderedAny);
    }

    private boolean addTextSegment(LinearLayout target, Node firstInclusive, Node stopExclusive, int depth, boolean afterBlock) {
        if (firstInclusive == null || firstInclusive == stopExclusive) {
            return false;
        }
        CharSequence text = inlineRenderer.renderRange(firstInclusive, stopExclusive);
        if (text.toString().trim().length() == 0) {
            return false;
        }
        addBlock(target, new MarkdownTextBlockView(context, text, LineTheme.FONT_MD, false, linkHandler),
                afterBlock ? 1 : depth == 0 ? 2 : 0, 7);
        return true;
    }

    private String plainText(Node node) {
        StringBuilder builder = new StringBuilder();
        appendPlainText(builder, node);
        return builder.toString().trim();
    }

    private void appendPlainText(StringBuilder builder, Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Text) {
                builder.append(((Text) child).getLiteral());
            } else {
                appendPlainText(builder, child);
            }
            child = child.getNext();
        }
    }

    private int headingSize(int level) {
        if (level <= 1) {
            return 22;
        }
        if (level == 2) {
            return LineTheme.FONT_XL;
        }
        if (level == 3) {
            return LineTheme.FONT_LG;
        }
        return LineTheme.FONT_MD;
    }

    private String bulletForDepth(int depth) {
        int value = Math.abs(depth % 3);
        if (value == 1) {
            return "-";
        }
        if (value == 2) {
            return "+";
        }
        return "•";
    }

    private String languageFromInfo(String info) {
        if (info == null) {
            return "";
        }
        String trimmed = info.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private interface BlockRenderer {
        void render(LinearLayout target, Node node, int depth);
    }
}
