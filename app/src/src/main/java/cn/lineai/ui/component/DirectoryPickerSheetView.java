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
import java.util.List;

public final class DirectoryPickerSheetView extends FrameLayout {
    public interface Listener {
        void onDirectoryPickerClosed();

        void onDirectoryPicked(String path);

        void onDirectoryPickerConfirmed();
    }

    private final View backdrop;
    private final LinearLayout panel;
    private final LinearLayout body;
    private final TextView titleView;
    private final TextView subtitleView;
    private final IconButtonView confirmButton;
    private Listener listener;
    private String selectedPath = "";

    public DirectoryPickerSheetView(Context context) {
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

        confirmButton = new IconButtonView(context, IconButtonView.CHECK);
        confirmButton.setIconColor(LineTheme.TEXT_ON_COLOR);
        confirmButton.setIconSizeDp(52, 22);
        confirmButton.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 26));
        confirmButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDirectoryPickerConfirmed();
            }
        });
        FrameLayout.LayoutParams confirmParams = new FrameLayout.LayoutParams(LineTheme.dp(context, 52), LineTheme.dp(context, 52));
        confirmParams.gravity = Gravity.BOTTOM | Gravity.END;
        confirmParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        confirmParams.bottomMargin = LineTheme.dp(context, LineTheme.LG);
        addView(confirmButton, confirmParams);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void show(String title, String subtitle, FileTreeNode tree, String selectedPath, boolean loading, String message) {
        this.selectedPath = selectedPath == null ? "" : selectedPath;
        titleView.setText(title == null ? "" : title);
        subtitleView.setText(subtitle == null ? "" : subtitle);
        body.removeAllViews();
        resizePanel();

        if (tree == null) {
            addStatus(message == null || message.length() == 0 ? "目录不可访问" : message);
        } else {
            ScrollView scrollView = new ScrollView(getContext());
            LinearLayout treeList = new LinearLayout(getContext());
            treeList.setOrientation(LinearLayout.VERTICAL);
            LineTheme.padding(treeList, LineTheme.SM, LineTheme.SM, LineTheme.SM, 90);
            scrollView.addView(treeList, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            body.addView(scrollView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addParentRow(treeList, tree);
            if (loading) {
                addInlineStatus(treeList, message == null || message.length() == 0 ? "正在读取目录..." : message);
            }
            addDirectoryContents(treeList, tree);
        }

        confirmButton.setVisibility(this.selectedPath.length() == 0 ? GONE : VISIBLE);
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void close() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        setVisibility(GONE);
        if (listener != null) {
            listener.onDirectoryPickerClosed();
        }
    }

    private void addStatus(String text) {
        TextView status = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(false);
        LineTheme.padding(status, LineTheme.XL, LineTheme.XL, LineTheme.XL, LineTheme.XL);
        body.addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void addInlineStatus(LinearLayout treeList, String text) {
        TextView status = LineTheme.text(getContext(), text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        LineTheme.padding(status, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        treeList.addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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

    private void addParentRow(LinearLayout treeList, FileTreeNode tree) {
        String current = selectedPath.length() == 0 && tree != null ? tree.getPath() : selectedPath;
        String parentPath = parentPath(current);
        if (parentPath.length() == 0) {
            return;
        }
        Context context = treeList.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDirectoryPicked(parentPath);
            }
        });
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        treeList.addView(row, rowParams);

        IconButtonView up = inlineIcon(context, IconButtonView.CHEVRON_LEFT, LineTheme.TEXT_TERTIARY, 16);
        row.addView(up, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(texts, textsParams);

        TextView label = LineTheme.textMedium(context, "上一级", LineTheme.FONT_SM, LineTheme.TEXT);
        texts.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView path = LineTheme.text(context, parentPath, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        path.setSingleLine(true);
        path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pathParams.topMargin = LineTheme.dp(context, 2);
        texts.addView(path, pathParams);
    }

    private String parentPath(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.length() == 0 || ".".equals(value) || "/".equals(value)) {
            return "";
        }
        int index = value.lastIndexOf('/');
        if (index > 0) {
            return value.substring(0, index);
        }
        if (index == 0) {
            return "/";
        }
        return "";
    }

    private void addDirectoryContents(LinearLayout treeList, FileTreeNode node) {
        if (node == null) {
            return;
        }
        if (!node.isDirectory()) {
            return;
        }
        List<FileTreeNode> children = node.getChildren();
        if (children.isEmpty()) {
            addInlineStatus(treeList, "空目录");
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            addNodeRow(treeList, children.get(i));
        }
    }

    private void addNodeRow(LinearLayout treeList, FileTreeNode node) {
        Context context = treeList.getContext();
        boolean selected = node.isDirectory() && node.getPath().equals(selectedPath);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(selected ? LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 8) : null);
        row.setClickable(node.isDirectory());
        if (node.isDirectory()) {
            row.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDirectoryPicked(node.getPath());
                }
            });
        }
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        treeList.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (node.isDirectory()) {
            IconButtonView chevron = inlineIcon(context, IconButtonView.CHEVRON_RIGHT, LineTheme.TEXT_TERTIARY, 16);
            row.addView(chevron, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        } else {
            row.addView(new View(context), new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        }

        int iconType = node.isDirectory()
                ? (node.isExpanded() ? IconButtonView.FOLDER_OPEN : IconButtonView.FOLDER)
                : IconButtonView.FILE;
        int iconColor = node.isDirectory()
                ? (selected ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY)
                : LineTheme.TEXT_TERTIARY;
        IconButtonView folder = inlineIcon(context, iconType, iconColor, 17);
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 17), LineTheme.dp(context, 17));
        folderParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(folder, folderParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(texts, textsParams);

        TextView name = selected
                ? LineTheme.textMedium(context, node.getName(), LineTheme.FONT_MD, LineTheme.ACCENT)
                : LineTheme.text(context, node.getName(), LineTheme.FONT_MD,
                        node.isDirectory() ? LineTheme.TEXT : LineTheme.TEXT_TERTIARY,
                        Typeface.NORMAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(name, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (selected) {
            TextView path = LineTheme.text(context, node.getPath(), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            path.setSingleLine(true);
            path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            pathParams.topMargin = LineTheme.dp(context, 2);
            texts.addView(path, pathParams);
        }
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
