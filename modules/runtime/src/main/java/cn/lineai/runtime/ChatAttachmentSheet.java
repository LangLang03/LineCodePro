package cn.lineai.runtime;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import cn.lineai.feature.chat.DrawerPanel;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Workspace attachment tree picker from C++ {@code ChatAttachmentPicker} (chat_overlays.cpp):
 * 640dp panel, 17sp bold title, 11sp selected-count subtitle, 52dp rows indented 18dp per level,
 * 26dp selection circles and a 48dp close action.
 */
final class ChatAttachmentSheet {

    private static final int PANEL_HEIGHT_DP = 640;
    private static final int ROW_MIN_DP = 52;
    private static final int INDENT_DP = 18;
    private static final int MAX_DEPTH = 24;
    private static final int CHECKER_DP = 26;

    private ChatAttachmentSheet() {
    }

    static void show(PageHost host, DrawerPanel.FileNode tree, List<String> selectedPaths,
                     Consumer<String> onToggle) {
        new Sheet(host, tree, selectedPaths, onToggle).present();
    }

    private static final class Sheet {
        private final PageHost host;
        private final Context context;
        private final Tokens tokens;
        private final DrawerPanel.FileNode tree;
        private final Set<String> selected = new HashSet<>();
        private final Set<String> expanded = new HashSet<>();
        private final Consumer<String> onToggle;
        private final LinearLayout body;
        private final TextView count;

        Sheet(PageHost host, DrawerPanel.FileNode tree, List<String> selectedPaths,
              Consumer<String> onToggle) {
            this.host = host;
            this.context = host.activity();
            this.tokens = host.tokens();
            this.tree = tree;
            this.onToggle = onToggle;
            if (selectedPaths != null) {
                selected.addAll(selectedPaths);
            }
            if (tree != null) {
                expanded.add(tree.path);
            }
            body = ChatSheets.column(context);
            Ui.padding(body, 8, 8, 8, 16);
            count = ChatSheets.text(context, "", Tokens.FONT_XS, tokens.textTertiary);
        }

        void present() {
            LinearLayout panel = ChatSheets.column(context);
            panel.addView(header(), new LinearLayout.LayoutParams(-1, -2));
            panel.addView(ChatSheets.divider(context, tokens));
            android.widget.ScrollView bodyScroll = new android.widget.ScrollView(context);
            bodyScroll.setVerticalScrollBarEnabled(false);
            bodyScroll.addView(body, new android.widget.ScrollView.LayoutParams(-1, -2));
            panel.addView(bodyScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            render();
            // C++ ChatAttachmentPicker keeps a fixed 640dp frame; a bare wrap-content
            // ScrollView would collapse it to the tree's height (a thin strip).
            panel.setMinimumHeight(ChatSheets.dp(context, PANEL_HEIGHT_DP));
            Sheets.showBottom(context, tokens, panel, PANEL_HEIGHT_DP, PANEL_HEIGHT_DP);
        }

        private LinearLayout header() {
            LinearLayout header = ChatSheets.row(context);
            Ui.padding(header, 20, 20, 20, 16);
            LinearLayout labels = ChatSheets.column(context);
            labels.addView(Ui.bold(context,
                            context.getString(R.string.attachment_picker_title_local),
                            Tokens.FONT_LG, tokens.text),
                    new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
            countParams.topMargin = ChatSheets.dp(context, 3);
            labels.addView(count, countParams);
            header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            header.addView(new View(context),
                    new LinearLayout.LayoutParams(ChatSheets.dp(context, 12), 1));
            header.addView(ChatSheets.closeButton(host), new LinearLayout.LayoutParams(
                    ChatSheets.dp(context, 48), ChatSheets.dp(context, 48)));
            return header;
        }

        private void render() {
            body.removeAllViews();
            count.setText(context.getString(R.string.sheet_attachment_selected_count,
                    selected.size()));
            if (tree == null) {
                TextView status = ChatSheets.text(context,
                        context.getString(R.string.sheet_status_no_files), Tokens.FONT_SM,
                        tokens.textSecondary);
                status.setGravity(Gravity.CENTER);
                Ui.padding(status, 20, 20, 20, 20);
                body.addView(status, new LinearLayout.LayoutParams(-1, -2));
                return;
            }
            append(tree, 0, true);
        }

        private void append(DrawerPanel.FileNode node, int depth, boolean root) {
            body.addView(row(node, depth, root), new LinearLayout.LayoutParams(-1, -2));
            boolean open = node.directory && expanded.contains(node.path);
            if (!open) {
                return;
            }
            if (node.children.isEmpty()) {
                TextView empty = ChatSheets.text(context,
                        context.getString(R.string.sheet_status_empty_dir), Tokens.FONT_XS,
                        tokens.textTertiary);
                Ui.padding(empty, 12 + Math.min(depth + 1, MAX_DEPTH) * INDENT_DP, 8, 12, 8);
                body.addView(empty, new LinearLayout.LayoutParams(-1, -2));
                return;
            }
            for (DrawerPanel.FileNode child : node.children) {
                append(child, depth + 1, false);
            }
        }

        private LinearLayout row(DrawerPanel.FileNode node, int depth, boolean root) {
            LinearLayout row = ChatSheets.row(context);
            row.setMinimumHeight(ChatSheets.dp(context, ROW_MIN_DP));
            Ui.padding(row, 12 + Math.min(depth, MAX_DEPTH) * INDENT_DP, 8, 12, 8);
            row.setClickable(true);
            row.setFocusable(true);
            boolean open = node.directory && expanded.contains(node.path);
            if (node.directory) {
                ImageView chevron = icon(open ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT,
                        tokens.textTertiary);
                row.addView(chevron, iconParams(16));
            } else {
                row.addView(new View(context), new LinearLayout.LayoutParams(
                        ChatSheets.dp(context, 16), ChatSheets.dp(context, 1)));
            }
            row.addView(spacer(8));
            row.addView(icon(node.directory
                            ? (open ? Icons.FOLDER_OPEN : Icons.FOLDER) : Icons.FILE,
                    node.directory
                            ? (open ? tokens.accent : tokens.textSecondary) : tokens.textSecondary),
                    iconParams(17));
            row.addView(spacer(8));
            row.addView(labels(node, root), new LinearLayout.LayoutParams(0, -2, 1f));
            if (!node.directory) {
                row.addView(spacer(8));
                row.addView(checker(node), new LinearLayout.LayoutParams(
                        ChatSheets.dp(context, CHECKER_DP), ChatSheets.dp(context, CHECKER_DP)));
            }
            row.setOnClickListener(v -> {
                if (node.directory) {
                    if (!expanded.remove(node.path)) {
                        expanded.add(node.path);
                    }
                    render();
                    return;
                }
                if (!selected.remove(node.path)) {
                    selected.add(node.path);
                }
                if (onToggle != null) {
                    onToggle.accept(node.path);
                }
                render();
            });
            return row;
        }

        private LinearLayout labels(DrawerPanel.FileNode node, boolean root) {
            LinearLayout labels = ChatSheets.column(context);
            TextView name = root
                    ? Ui.bold(context, node.name, Tokens.FONT_MD, tokens.text)
                    : Ui.text(context, node.name, Tokens.FONT_MD, tokens.textSecondary,
                            android.graphics.Typeface.NORMAL);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setMaxHeight(ChatSheets.dp(context, 19));
            labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
            if (root && node.path != null && !node.path.isEmpty()) {
                TextView path = ChatSheets.text(context, node.path, Tokens.FONT_XS,
                        tokens.textTertiary);
                path.setSingleLine(true);
                path.setEllipsize(android.text.TextUtils.TruncateAt.END);
                path.setMaxHeight(ChatSheets.dp(context, 15));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = ChatSheets.dp(context, 2);
                labels.addView(path, params);
            }
            return labels;
        }

        private View checker(DrawerPanel.FileNode node) {
            boolean checked = selected.contains(node.path);
            ImageView circle = new ImageView(context);
            circle.setImageResource(Icons.drawable(checked ? Icons.CHECK : Icons.PLUS));
            int tint = checked ? tokens.textOnColor : tokens.textSecondary;
            circle.setColorFilter(tint);
            circle.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int inner = ChatSheets.dp(context, 14);
            int padding = (ChatSheets.dp(context, CHECKER_DP) - inner) / 2;
            circle.setPadding(padding, padding, padding, padding);
            circle.setBackground(Ui.rounded(context,
                    checked ? tokens.accent : tokens.surfaceLight, 13));
            return circle;
        }

        private ImageView icon(int res, int tint) {
            ImageView view = new ImageView(context);
            view.setImageResource(Icons.drawable(res));
            view.setColorFilter(tint);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return view;
        }

        private LinearLayout.LayoutParams iconParams(int sizeDp) {
            return new LinearLayout.LayoutParams(ChatSheets.dp(context, sizeDp),
                    ChatSheets.dp(context, sizeDp));
        }

        private View spacer(int widthDp) {
            View view = new View(context);
            view.setLayoutParams(new LinearLayout.LayoutParams(
                    ChatSheets.dp(context, widthDp), 1));
            return view;
        }
    }
}
