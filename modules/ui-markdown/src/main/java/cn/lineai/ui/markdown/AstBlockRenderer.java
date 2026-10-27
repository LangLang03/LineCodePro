package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** One retained top-level AST subtree and its colour bindings. */
final class AstBlockRenderer {
    private final Context context;
    private final InlineContent inline;
    private final List<Consumer<Tokens>> colours = new ArrayList<>();
    private final List<CodeBlockView> codeBlocks = new ArrayList<>();
    private final View root;
    private Tokens tokens;
    private boolean wrap;

    AstBlockRenderer(Context context, InlineContent inline, Tokens tokens, Node node, boolean wrap) {
        this.context = context;
        this.inline = inline;
        this.tokens = tokens;
        this.wrap = wrap;
        root = block(node, 0);
    }

    View view() {
        return root;
    }

    void applyTheme(Tokens tokens) {
        this.tokens = tokens;
        for (Consumer<Tokens> colour : colours) {
            colour.accept(tokens);
        }
    }

    void setCodeWrapEnabled(boolean enabled) {
        wrap = enabled;
        for (CodeBlockView code : codeBlocks) {
            code.setCodeWrapEnabled(enabled);
        }
    }

    private View block(Node node, int depth) {
        if (node instanceof Heading || node instanceof Paragraph) {
            return prose(node, depth);
        }
        if (node instanceof ListBlock) {
            return spaced(list((ListBlock) node, depth), 1, 7);
        }
        if (node instanceof BlockQuote) {
            return spaced(quote(node, depth), 3, 8);
        }
        if (node instanceof TableBlock) {
            return spaced(table(node), 4, 8);
        }
        if (node instanceof FencedCodeBlock) {
            FencedCodeBlock code = (FencedCodeBlock) node;
            String info = code.getInfo().trim();
            String language = info.isEmpty() ? "" : info.split("\\s+", 2)[0];
            return code(code.getLiteral(), language);
        }
        if (node instanceof IndentedCodeBlock) {
            return code(((IndentedCodeBlock) node).getLiteral(), "");
        }
        if (node instanceof HtmlBlock) {
            return code(((HtmlBlock) node).getLiteral(), "html");
        }
        if (node instanceof ThematicBreak) {
            View rule = new View(context);
            colour(t -> rule.setBackgroundColor(t.borderLight));
            spaced(rule, 8, 8);
            rule.getLayoutParams().height = Math.max(1, dp(1));
            return rule;
        }
        LinearLayout group = column();
        children(group, node, depth);
        return spaced(group, 0, 0);
    }

    private View prose(Node node, int depth) {
        boolean heading = node instanceof Heading;
        int level = heading ? ((Heading) node).getLevel() : 0;
        int size = level == 1 ? 28 : level == 2 ? 24 : level == 3 ? 20 : 16;
        TextView text = text(size, t -> t.text, false);
        text.setLineSpacing(dp(heading ? 5 : 7), 1f);
        if (heading) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        inline.bind(text, node);
        int top = heading ? (depth == 0 ? 8 : 4) : (depth == 0 ? 2 : 0);
        int bottom = heading ? (depth == 0 ? 20 : 10) : (depth == 0 ? 18 : 8);
        return spaced(text, top, bottom);
    }

    private void children(LinearLayout parent, Node node, int depth) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof LinkReferenceDefinition)) {
                parent.addView(block(child, depth));
            }
        }
    }

    private View list(ListBlock node, int depth) {
        LinearLayout list = column();
        Integer start = node instanceof OrderedList ? ((OrderedList) node).getMarkerStartNumber() : 1;
        int number = start == null ? 1 : start;
        String[] bullets = {"\u2022", "-", "+"};
        for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.TOP);
            TextView marker = text(16, t -> t.textSecondary, false);
            String label = node instanceof OrderedList ? Integer.toString(number) + "." : bullets[depth % 3];
            marker.setText(label);
            marker.setGravity(Gravity.END);
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(22 + depth * 4), WRAP_CONTENT);
            markerParams.rightMargin = dp(8);
            row.addView(marker, markerParams);
            LinearLayout content = column();
            children(content, item, depth + 1);
            row.addView(content, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
            list.addView(spaced(row, 0, 3));
            number++;
        }
        return list;
    }

    private View quote(Node node, int depth) {
        LinearLayout quote = new LinearLayout(context);
        View rail = new View(context);
        background(rail, t -> t.borderLight, 2, false);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(3), MATCH_PARENT);
        railParams.rightMargin = dp(12);
        quote.addView(rail, railParams);
        LinearLayout content = column();
        children(content, node, depth + 1);
        quote.addView(content, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        return quote;
    }

    private View code(String literal, String language) {
        CodeBlockView code = new CodeBlockView(context, tokens, literal, language, wrap);
        codeBlocks.add(code);
        colour(code::applyTheme);
        return spaced(code, 4, 8);
    }

    private View table(Node node) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(true);
        scroll.setHorizontalFadingEdgeEnabled(false);
        scroll.setFadingEdgeLength(0);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(Ui.rounded(context, Color.TRANSPARENT, 12));
        scroll.setClipToOutline(true);
        TableLayout grid = new TableLayout(context);
        grid.setShrinkAllColumns(false);
        grid.setStretchAllColumns(true);
        scroll.addView(grid, new HorizontalScrollView.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        int bodyIndex = 0;
        for (Node section = node.getFirstChild(); section != null; section = section.getNext()) {
            boolean header = section instanceof TableHead;
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                tableRow(grid, row, header, bodyIndex);
                if (!header) {
                    bodyIndex++;
                }
            }
        }
        return scroll;
    }

    private void tableRow(TableLayout grid, Node rowNode, boolean header, int index) {
        TableRow row = new TableRow(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        grid.addView(row, new TableLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        for (Node node = rowNode.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof TableCell) {
                row.addView(cell((TableCell) node, header, index),
                        new TableRow.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            }
        }
    }

    private View cell(TableCell node, boolean header, int row) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setMinimumWidth(dp(84));
        cell.setMinimumHeight(dp(38));
        Ui.padding(cell, 12, 8, 12, 8);
        background(cell, t -> header ? t.surfaceLight : row % 2 == 0 ? t.surface : t.codeBg, 0, true);
        TextView text = text(13, t -> header ? t.text : t.textSecondary, header);
        int alignment = node.getAlignment() == TableCell.Alignment.RIGHT ? Gravity.END
                : node.getAlignment() == TableCell.Alignment.CENTER ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        text.setGravity(alignment | Gravity.CENTER_VERTICAL);
        text.setMaxWidth(dp(196));
        text.setSingleLine(false);
        inline.bind(text, node);
        cell.addView(text, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return cell;
    }

    private TextView text(int size, ToIntFunction<Tokens> foreground, boolean bold) {
        TextView text = Ui.text(context, "", size, foreground.applyAsInt(tokens),
                bold ? Typeface.BOLD : Typeface.NORMAL);
        colour(t -> {
            text.setTextColor(foreground.applyAsInt(t));
            text.setLinkTextColor(t.accent);
            text.setHighlightColor(t.accentMuted);
            text.invalidate();
        });
        return text;
    }

    private void background(View view, ToIntFunction<Tokens> fill, int radius, boolean border) {
        GradientDrawable drawable = Ui.rounded(context, fill.applyAsInt(tokens), radius);
        view.setBackground(drawable);
        colour(t -> {
            drawable.setColor(fill.applyAsInt(t));
            if (border) {
                drawable.setStroke(Math.max(1, dp(1)), t.borderLight);
            }
        });
    }

    private void colour(Consumer<Tokens> binding) {
        colours.add(binding);
        binding.accept(tokens);
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private View spaced(View view, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
