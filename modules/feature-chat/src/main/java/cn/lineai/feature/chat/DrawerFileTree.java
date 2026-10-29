package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Workspace tree body of the drawer, matching the C++ {@code FileBody} + {@code kFilePresentationRules}:
 * 48dp rows indented 16dp per level (deepest 5), folder_open/accent for open directories,
 * folder/secondary for closed ones, file_code for XML (255,159,10) and code (240,219,79),
 * file_text/secondary for text files and file/tertiary for everything else. A root row carries a
 * 22x14 plus action.
 */
final class DrawerFileTree extends ScrollView {

    interface Callback {
        void onFileSelected(String path, String name);

        void onLongPressed(String path, String name, boolean directory, boolean root);
    }

    private static final int MAX_DEPTH = 5;
    private static final int INDENT_DP = 16;
    private static final int XML_COLOR = Color.rgb(255, 159, 10);
    private static final int JS_COLOR = Color.rgb(240, 219, 79);
    private static final String[] CODE_EXTENSIONS = {
            ".java", ".kt", ".js", ".ts", ".tsx", ".jsx", ".json", ".gradle"};
    private static final String[] TEXT_EXTENSIONS = {".md", ".txt", ".log"};

    private final LinearLayout list;
    private final TextView empty;
    private final Set<String> expanded = new HashSet<>();
    private Tokens tokens;
    private DrawerPanel.FileNode tree;
    private Callback callback;
    private boolean loading;

    DrawerFileTree(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        empty = Ui.text(context, context.getString(R.string.drawer_files_preparing),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        Ui.padding(empty, 0, 80, 0, 0);
        content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(list, 8, 8, 8, 8);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        addView(content, new LayoutParams(-1, -2));
    }

    void setCallback(Callback value) {
        callback = value;
    }

    /** Starts (or restarts) loading; an existing tree stays visible while it refreshes. */
    void beginLoading() {
        loading = true;
        if (tree == null) {
            render();
        }
    }

    void setTree(DrawerPanel.FileNode node) {
        loading = false;
        tree = node;
        if (node != null) {
            expanded.add(node.path);
        }
        render();
    }

    private void render() {
        list.removeAllViews();
        empty.setVisibility(tree == null ? VISIBLE : GONE);
        empty.setText(getContext().getString(loading
                ? R.string.drawer_files_preparing : R.string.drawer_empty_files));
        if (tree != null) {
            append(tree, 0, true);
        }
    }

    private void append(DrawerPanel.FileNode node, int depth, boolean root) {
        list.addView(new Row(node, depth, root), new LinearLayout.LayoutParams(-1, -2));
        if (!node.directory || !expanded.contains(node.path)) {
            return;
        }
        for (DrawerPanel.FileNode child : node.children) {
            append(child, depth + 1, false);
        }
    }

    void applyTokens(Tokens current) {
        tokens = current;
        empty.setTextColor(current.textTertiary);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof Row) {
                ((Row) child).restyle();
            }
        }
    }

    private final class Row extends LinearLayout {
        private final DrawerPanel.FileNode node;
        private final IconButton glyph;
        private final TextView name;
        private final IconButton plus;
        private final boolean root;

        Row(DrawerPanel.FileNode node, int depth, boolean root) {
            super(DrawerFileTree.this.getContext());
            this.node = node;
            this.root = root;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(getContext(), 48));
            setClickable(true);
            setFocusable(true);
            Ui.padding(this, 16 + Math.min(depth, MAX_DEPTH) * INDENT_DP, 12, 16, 12);
            glyph = new IconButton(getContext(), Icons.FILE);
            glyph.setClickable(false);
            addView(glyph, new LinearLayout.LayoutParams(
                    Ui.dp(getContext(), 18), Ui.dp(getContext(), 18)));
            addView(new View(getContext()), new LinearLayout.LayoutParams(8, 1));
            name = Ui.text(getContext(), node.name, Tokens.FONT_SM, tokens.text, Typeface.NORMAL);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxHeight(Ui.dp(getContext(), 18));
            addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            plus = root ? new IconButton(getContext(), Icons.PLUS) : null;
            if (plus != null) {
                plus.setIconSizeDp(22, 14);
                plus.setClickable(true);
                plus.setContentDescription(getContext().getString(R.string.composer_attach_desc));
                plus.setOnClickListener(v -> press());
                addView(plus, new LinearLayout.LayoutParams(
                        Ui.dp(getContext(), 22), Ui.dp(getContext(), 14)));
            }
            setOnClickListener(v -> {
                if (node.directory) {
                    if (!expanded.remove(node.path)) {
                        expanded.add(node.path);
                    }
                    render();
                } else if (callback != null) {
                    callback.onFileSelected(node.path, node.name);
                }
            });
            setOnLongClickListener(v -> {
                press();
                return true;
            });
            restyle();
        }

        private void press() {
            if (callback != null) {
                callback.onLongPressed(node.path, node.name, node.directory, root);
            }
        }

        /** {icon, tint} in kFilePresentationRules order. */
        private int[] presentation() {
            if (node.directory) {
                boolean open = expanded.contains(node.path);
                return new int[] {open ? Icons.FOLDER_OPEN : Icons.FOLDER,
                        open ? tokens.accent : tokens.textSecondary};
            }
            String lower = node.name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xml")) {
                return new int[] {Icons.FILE_CODE, XML_COLOR};
            }
            if (hasExtension(lower, CODE_EXTENSIONS)) {
                return new int[] {Icons.FILE_CODE, JS_COLOR};
            }
            if (hasExtension(lower, TEXT_EXTENSIONS)) {
                return new int[] {Icons.FILE_TEXT, tokens.textSecondary};
            }
            return new int[] {Icons.FILE, tokens.textTertiary};
        }

        void restyle() {
            int[] presentation = presentation();
            glyph.setIconType(presentation[0]);
            glyph.setIconColor(presentation[1]);
            glyph.setIconSizeDp(18, node.directory ? 18 : 16);
            name.setTextColor(tokens.text);
            if (plus != null) {
                plus.setIconColor(tokens.textTertiary);
            }
        }
    }

    private static boolean hasExtension(String name, String[] extensions) {
        for (String extension : extensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
