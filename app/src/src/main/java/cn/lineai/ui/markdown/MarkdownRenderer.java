package cn.lineai.ui.markdown;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;
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

public final class MarkdownRenderer {
    private final Context context;
    private final MarkdownInlineRenderer inlineRenderer;
    private boolean codeWrapEnabled;
    private MarkdownLinkHandler linkHandler;

    public MarkdownRenderer(Context context) {
        this.context = context;
        inlineRenderer = new MarkdownInlineRenderer(context);
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
        if (node instanceof Document) {
            renderChildren(target, node, depth);
            return;
        }
        if (node instanceof LinkReferenceDefinition) {
            return;
        }
        if (node instanceof Heading) {
            Heading heading = (Heading) node;
            addBlock(target, new MarkdownTextBlockView(
                    context,
                    inlineRenderer.render(node),
                    headingSize(heading.getLevel()),
                    true,
                    linkHandler
            ), depth == 0 ? 4 : 2, 6);
            return;
        }
        if (node instanceof Paragraph) {
            Image image = onlyImage(node);
            if (image != null) {
                addBlock(target, new MarkdownImageView(context, image.getDestination(), plainText(image)), depth == 0 ? 2 : 0, 7);
                return;
            }
            CharSequence text = inlineRenderer.render(node);
            if (text.toString().trim().length() > 0) {
                addBlock(target, new MarkdownTextBlockView(context, text, LineTheme.FONT_MD, false, linkHandler), depth == 0 ? 2 : 0, 7);
            }
            return;
        }
        if (node instanceof FencedCodeBlock) {
            FencedCodeBlock block = (FencedCodeBlock) node;
            addBlock(target, new MarkdownCodeBlockView(context, block.getLiteral(), languageFromInfo(block.getInfo()), codeWrapEnabled), 4, 8);
            return;
        }
        if (node instanceof IndentedCodeBlock) {
            IndentedCodeBlock block = (IndentedCodeBlock) node;
            addBlock(target, new MarkdownCodeBlockView(context, block.getLiteral(), "", codeWrapEnabled), 4, 8);
            return;
        }
        if (node instanceof BlockQuote) {
            MarkdownQuoteBlockView quote = new MarkdownQuoteBlockView(context);
            renderChildren(quote.getContent(), node, depth + 1);
            addBlock(target, quote, 3, 8);
            return;
        }
        if (node instanceof TableBlock) {
            addBlock(target, new MarkdownTableView(context, inlineRenderer, linkHandler, (TableBlock) node), 4, 8);
            return;
        }
        if (node instanceof BulletList || node instanceof OrderedList) {
            addList(target, (ListBlock) node, depth);
            return;
        }
        if (node instanceof ThematicBreak) {
            addBlock(target, new MarkdownThematicBreakView(context), 8, 8);
            return;
        }
        if (node instanceof HtmlBlock) {
            HtmlBlock block = (HtmlBlock) node;
            addBlock(target, new MarkdownCodeBlockView(context, block.getLiteral(), "html", codeWrapEnabled), 4, 8);
            return;
        }
        if (node.getFirstChild() != null) {
            renderChildren(target, node, depth);
        }
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
}
