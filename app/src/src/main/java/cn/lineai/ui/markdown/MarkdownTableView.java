package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.node.Node;

public final class MarkdownTableView extends HorizontalScrollView {
    private final Context context;
    private final MarkdownInlineRenderer inlineRenderer;
    private final MarkdownLinkHandler linkHandler;
    private final TableLayout tableLayout;
    private int bodyRowIndex;

    public MarkdownTableView(Context context, MarkdownInlineRenderer inlineRenderer, MarkdownLinkHandler linkHandler, TableBlock tableBlock) {
        super(context);
        this.context = context;
        this.inlineRenderer = inlineRenderer;
        this.linkHandler = linkHandler;
        setFillViewport(true);
        setHorizontalScrollBarEnabled(true);
        setHorizontalFadingEdgeEnabled(false);
        setFadingEdgeLength(0);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(true);
        setBackground(LineTheme.rounded(context, Color.TRANSPARENT, 12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
        }

        tableLayout = new TableLayout(context);
        tableLayout.setShrinkAllColumns(false);
        tableLayout.setStretchAllColumns(true);
        addView(tableLayout, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        render(tableBlock);
    }

    private void render(TableBlock tableBlock) {
        Node section = tableBlock.getFirstChild();
        while (section != null) {
            if (section instanceof TableHead) {
                renderRows(section, true);
            } else if (section instanceof TableBody) {
                renderRows(section, false);
            }
            section = section.getNext();
        }
    }

    private void renderRows(Node section, boolean header) {
        Node rowNode = section.getFirstChild();
        while (rowNode != null) {
            if (rowNode instanceof org.commonmark.ext.gfm.tables.TableRow) {
                addRow(rowNode, header);
            }
            rowNode = rowNode.getNext();
        }
    }

    private void addRow(Node rowNode, boolean header) {
        android.widget.TableRow row = new android.widget.TableRow(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        tableLayout.addView(row, new TableLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        Node cellNode = rowNode.getFirstChild();
        while (cellNode != null) {
            if (cellNode instanceof TableCell) {
                row.addView(createCell((TableCell) cellNode, header), new android.widget.TableRow.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.MATCH_PARENT
                ));
            }
            cellNode = cellNode.getNext();
        }
        if (!header) {
            bodyRowIndex++;
        }
    }

    private View createCell(TableCell cell, boolean header) {
        LinearLayout cellView = new LinearLayout(context);
        cellView.setOrientation(LinearLayout.VERTICAL);
        cellView.setGravity(Gravity.CENTER_VERTICAL);
        cellView.setMinimumWidth(LineTheme.dp(context, 84));
        cellView.setMinimumHeight(LineTheme.dp(context, 38));
        cellView.setBackground(LineTheme.roundedStroke(context, cellBackground(header), 0, LineTheme.BORDER_LIGHT));
        LineTheme.padding(cellView, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        TextView textView = LineTheme.text(
                context,
                "",
                LineTheme.FONT_SM,
                header ? LineTheme.TEXT : LineTheme.TEXT_SECONDARY,
                header ? Typeface.BOLD : Typeface.NORMAL
        );
        MarkdownLinks.apply(textView, inlineRenderer.render(cell), linkHandler);
        textView.setGravity(gravityFor(cell.getAlignment()) | Gravity.CENTER_VERTICAL);
        textView.setMaxWidth(LineTheme.dp(context, 196));
        textView.setSingleLine(false);
        cellView.addView(textView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return cellView;
    }

    private int gravityFor(TableCell.Alignment alignment) {
        if (alignment == TableCell.Alignment.RIGHT) {
            return Gravity.END;
        }
        if (alignment == TableCell.Alignment.CENTER) {
            return Gravity.CENTER_HORIZONTAL;
        }
        return Gravity.START;
    }

    private int cellBackground(boolean header) {
        if (header) {
            return LineTheme.SURFACE_LIGHT;
        }
        return bodyRowIndex % 2 == 0 ? LineTheme.SURFACE : LineTheme.CODE_BG;
    }
}
