package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.model.FileTreeNode;
import cn.lineai.ui.theme.LineTheme;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AttachmentPickerSheetView extends FrameLayout {
    public interface Listener {
        void onAttachmentPickerClosed();

        void onAttachmentNodeSelected(String path, boolean directory);

        void onAttachmentFileToggled(String path, String name, String source);
    }

    private final View backdrop;
    private final LinearLayout panel;
    private final LinearLayout body;
    private final TextView titleView;
    private final TextView subtitleView;
    private Listener listener;
    private Set<String> selectedPaths = new HashSet<>();
    private String source = "local";

    public AttachmentPickerSheetView(Context context) {
        super(context);
        setVisibility(GONE);
        setClickable(true);

        backdrop = new View(context);
        backdrop.setBackgroundColor(LineTheme.OVERLAY);
        backdrop.setOnClickListener(v -> close());
        addView(backdrop, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 560));
        panelParams.gravity = Gravity.BOTTOM;
        addView(panel, panelParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(header, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        panel.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        titleView = LineTheme.text(context, "", LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        titles.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        subtitleView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = LineTheme.dp(context, 3);
        titles.addView(subtitleView, subtitleParams);

        IconButtonView close = new IconButtonView(context, IconButtonView.CLOSE);
        close.setIconColor(LineTheme.TEXT_SECONDARY);
        close.setIconSizeDp(36, 18);
        close.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 18));
        close.setOnClickListener(v -> close());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36));
        closeParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        header.addView(close, closeParams);

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        panel.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void show(
            String title,
            FileTreeNode tree,
            List<String> selectedPaths,
            boolean loading,
            String message,
            String source
    ) {
        this.selectedPaths = new HashSet<>(selectedPaths == null ? java.util.Collections.emptyList() : selectedPaths);
        this.source = source == null || source.length() == 0 ? "local" : source;
        titleView.setText(title == null ? "" : title);
        subtitleView.setText("已选择 " + this.selectedPaths.size() + " 个文件");
        body.removeAllViews();
        resizePanel();

        if (loading) {
            addStatus(message == null || message.length() == 0 ? "正在读取文件..." : message);
        } else if (tree == null) {
            addStatus(message == null || message.length() == 0 ? "没有可选择的文件" : message);
        } else {
            ScrollView scrollView = new ScrollView(getContext());
            LinearLayout treeList = new LinearLayout(getContext());
            treeList.setOrientation(LinearLayout.VERTICAL);
            LineTheme.padding(treeList, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.LG);
            scrollView.addView(treeList, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            body.addView(scrollView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addNodeRow(treeList, tree, 0, true);
        }

        setVisibility(VISIBLE);
        bringToFront();
    }

    public void close() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        setVisibility(GONE);
        if (listener != null) {
            listener.onAttachmentPickerClosed();
        }
    }

    private void resizePanel() {
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        if (params == null) {
            return;
        }
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int targetHeight = Math.max(LineTheme.dp(getContext(), 360), screenHeight - LineTheme.dp(getContext(), 48));
        if (params.height != targetHeight) {
            params.height = targetHeight;
            panel.setLayoutParams(params);
        }
    }

    private void addStatus(String text) {
        TextView status = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(false);
        LineTheme.padding(status, LineTheme.XL, LineTheme.XL, LineTheme.XL, LineTheme.XL);
        body.addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void addNodeRow(LinearLayout treeList, FileTreeNode node, int depth, boolean root) {
        if (node == null) {
            return;
        }
        Context context = treeList.getContext();
        boolean selected = !node.isDirectory() && selectedPaths.contains(node.getPath());
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (listener == null) {
                return;
            }
            if (node.isDirectory()) {
                listener.onAttachmentNodeSelected(node.getPath(), true);
            } else {
                listener.onAttachmentFileToggled(node.getPath(), node.getName(), source);
            }
        });
        LineTheme.padding(row, LineTheme.MD + depth * 18, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        treeList.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (node.isDirectory()) {
            IconButtonView chevron = inlineIcon(context, node.isExpanded() ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT,
                    LineTheme.TEXT_TERTIARY, 16);
            row.addView(chevron, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        } else {
            row.addView(new View(context), new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        }

        int iconType = node.isDirectory()
                ? (node.isExpanded() ? IconButtonView.FOLDER_OPEN : IconButtonView.FOLDER)
                : IconButtonView.FILE;
        int iconColor = node.isDirectory() ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY;
        IconButtonView icon = inlineIcon(context, iconType, iconColor, 17);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 17), LineTheme.dp(context, 17));
        iconParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(texts, textsParams);

        TextView name = LineTheme.text(context, node.getName(), LineTheme.FONT_MD,
                root ? LineTheme.TEXT : LineTheme.TEXT_SECONDARY,
                root ? Typeface.BOLD : Typeface.NORMAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(name, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (root) {
            TextView path = LineTheme.text(context, node.getPath(), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            path.setSingleLine(true);
            path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            pathParams.topMargin = LineTheme.dp(context, 2);
            texts.addView(path, pathParams);
        }

        if (!node.isDirectory()) {
            IconButtonView pick = new IconButtonView(context, selected ? IconButtonView.CHECK : IconButtonView.PLUS);
            pick.setIconColor(selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
            pick.setIconSizeDp(26, 14);
            pick.setBackground(LineTheme.rounded(context, selected ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 13));
            pick.setClickable(false);
            LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 26), LineTheme.dp(context, 26));
            pickParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            row.addView(pick, pickParams);
        }

        if (node.isDirectory() && node.isExpanded()) {
            List<FileTreeNode> children = node.getChildren();
            if (children.isEmpty()) {
                addEmptyRow(treeList, depth + 1);
            } else {
                for (int i = 0; i < children.size(); i++) {
                    addNodeRow(treeList, children.get(i), depth + 1, false);
                }
            }
        }
    }

    private void addEmptyRow(LinearLayout treeList, int depth) {
        TextView status = LineTheme.text(getContext(), "空目录", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        status.setSingleLine(true);
        LineTheme.padding(status, LineTheme.MD + depth * 18, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        treeList.addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private IconButtonView inlineIcon(Context context, int type, int color, int size) {
        IconButtonView icon = new IconButtonView(context, type);
        icon.setIconColor(color);
        icon.setClickable(false);
        icon.setFocusable(false);
        icon.setPadding(0, 0, 0, 0);
        icon.setMinimumWidth(0);
        icon.setMinimumHeight(0);
        icon.setMaxWidth(LineTheme.dp(context, size));
        icon.setMaxHeight(LineTheme.dp(context, size));
        return icon;
    }
}
