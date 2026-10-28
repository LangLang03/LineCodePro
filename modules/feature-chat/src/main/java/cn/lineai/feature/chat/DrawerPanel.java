package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.Conversation;
import cn.lineai.core.model.WorkspaceNode;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Conversation drawer: backdrop, 360dp sidebar, tabs, new-conversation action and lists. */
public final class DrawerPanel extends FrameLayout {

    public interface Listener {
        void onNewConversation();

        void onSelect(String conversationId);

        void onDelete(String conversationId);

        void onOpenFile(String relativePath);

        List<WorkspaceNode> files(String path);

        default String projectLabel() { return ""; }

        default String projectPath() { return ""; }
    }

    private final android.view.View backdrop;
    private final LinearLayout sidebar;
    private final LinearLayout tabs;
    private final LinearLayout conversationsGroup;
    private final LinearLayout filesGroup;
    private final LinearLayout list;
    private final LinearLayout fileList;
    private final TextView empty;
    private final TextView emptyFiles;
    private final TextView pathLabel;
    private final IconButton backButton;
    private final TextView headerTitle;
    private final IconButton filesRefresh;
    private final TextView projectLabel;
    private final TextView projectPath;
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private Listener listener;
    private Tokens tokens;
    private String currentId = "";
    private boolean filesMode;

    public DrawerPanel(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setVisibility(GONE);

        backdrop = new android.view.View(context);
        backdrop.setBackgroundColor(tokens.overlay);
        backdrop.setOnClickListener(v -> close());
        addView(backdrop, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        sidebar = new LinearLayout(context);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(tokens.bg);
        int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - Ui.dp(context, 48),
                Ui.dp(context, 360));
        LayoutParams sidebarParams = new LayoutParams(width, LayoutParams.MATCH_PARENT, Gravity.START);
        addView(sidebar, sidebarParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(header, 24, 40, 16, 24);
        headerTitle = Ui.bold(context, context.getString(R.string.drawer_title_conversations),
                Tokens.FONT_LG, tokens.text);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        filesRefresh = new IconButton(context, Icons.REFRESH_CW);
        filesRefresh.setIconColor(tokens.textSecondary);
        filesRefresh.setIconSizeDp(32, 18);
        filesRefresh.setContentDescription(context.getString(R.string.common_refresh));
        filesRefresh.setVisibility(GONE);
        filesRefresh.setOnClickListener(v -> renderFiles());
        header.addView(filesRefresh, new LinearLayout.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32)));
        sidebar.addView(header, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Ui.padding(tabs, 2, 2, 2, 2);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabsParams.leftMargin = Ui.dp(context, 16);
        tabsParams.rightMargin = Ui.dp(context, 16);
        tabsParams.bottomMargin = Ui.dp(context, 12);
        sidebar.addView(tabs, tabsParams);
        renderTabs();

        conversationsGroup = new LinearLayout(context);
        conversationsGroup.setOrientation(LinearLayout.VERTICAL);
        sidebar.addView(conversationsGroup, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout newButton = new LinearLayout(context);
        newButton.setOrientation(LinearLayout.HORIZONTAL);
        newButton.setGravity(Gravity.CENTER_VERTICAL);
        newButton.setClickable(true);
        newButton.setFocusable(true);
        Ui.padding(newButton, 16, 12, 16, 12);
        newButton.setBackground(Ui.rounded(context, tokens.inputBg, 14));
        newButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNewConversation();
            }
        });
        IconButton plus = new IconButton(context, Icons.PLUS);
        plus.setIconColor(tokens.text);
        plus.setIconSizeDp(18, 18);
        plus.setClickable(false);
        newButton.addView(plus, new LinearLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));
        TextView label = Ui.bold(context, context.getString(R.string.drawer_new_conversation),
                Tokens.FONT_MD, tokens.text);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = Ui.dp(context, 8);
        newButton.addView(label, labelParams);
        LinearLayout.LayoutParams newParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 52));
        newParams.leftMargin = Ui.dp(context, 16);
        newParams.rightMargin = Ui.dp(context, 16);
        newParams.bottomMargin = Ui.dp(context, 12);
        conversationsGroup.addView(newButton, newParams);

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(list, 12, 12, 12, 32);
        content.addView(list, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        empty = Ui.text(context, context.getString(R.string.drawer_empty_conversations),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        emptyParams.topMargin = Ui.dp(context, 48);
        content.addView(empty, emptyParams);
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        conversationsGroup.addView(scroll, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        filesGroup = new LinearLayout(context);
        filesGroup.setOrientation(LinearLayout.VERTICAL);
        filesGroup.setVisibility(GONE);
        sidebar.addView(filesGroup, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout project = new LinearLayout(context);
        project.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(project, 16, 8, 16, 8);
        projectLabel = Ui.bold(context, "LineCode", 13, tokens.text);
        projectPath = Ui.text(context, "", 11, tokens.textTertiary, Typeface.NORMAL);
        projectPath.setSingleLine(true);
        projectPath.setEllipsize(android.text.TextUtils.TruncateAt.END);
        project.addView(projectLabel, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams projectPathParams = new LinearLayout.LayoutParams(-1, -2);
        projectPathParams.topMargin = Ui.dp(context, 2);
        project.addView(projectPath, projectPathParams);
        filesGroup.addView(project, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout breadcrumb = new LinearLayout(context);
        breadcrumb.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(breadcrumb, 16, 4, 16, 8);
        backButton = new IconButton(context, Icons.CHEVRON_LEFT);
        backButton.setIconColor(tokens.textSecondary);
        backButton.setIconSizeDp(32, 18);
        backButton.setContentDescription(context.getString(R.string.common_back));
        backButton.setOnClickListener(v -> { expanded.clear(); renderFiles(); });
        breadcrumb.addView(backButton, new LinearLayout.LayoutParams(
                Ui.dp(context, 32), Ui.dp(context, 32)));
        pathLabel = Ui.medium(context, "/", Tokens.FONT_SM, tokens.textSecondary);
        pathLabel.setSingleLine(true);
        pathLabel.setEllipsize(android.text.TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0, -2, 1f);
        pathParams.leftMargin = Ui.dp(context, 6);
        breadcrumb.addView(pathLabel, pathParams);
        filesGroup.addView(breadcrumb, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView fileScroll = new android.widget.ScrollView(context);
        fileScroll.setVerticalScrollBarEnabled(false);
        LinearLayout fileContent = new LinearLayout(context);
        fileContent.setOrientation(LinearLayout.VERTICAL);
        fileList = new LinearLayout(context);
        fileList.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(fileList, 8, 8, 8, 32);
        fileContent.addView(fileList, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        emptyFiles = Ui.text(context, context.getString(R.string.drawer_empty_files),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        emptyFiles.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams filesEmptyParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        filesEmptyParams.topMargin = Ui.dp(context, 48);
        fileContent.addView(emptyFiles, filesEmptyParams);
        fileScroll.addView(fileContent, new android.widget.ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        filesGroup.addView(fileScroll, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void renderTabs() {
        Context context = getContext();
        tabs.removeAllViews();
        LinearLayout conversationsTab = tab(context, Icons.MESSAGE_SQUARE,
                R.string.drawer_tab_conversations, !filesMode);
        conversationsTab.setOnClickListener(v -> selectTab(false));
        LinearLayout filesTab = tab(context, Icons.FOLDER,
                R.string.drawer_tab_files, filesMode);
        filesTab.setOnClickListener(v -> selectTab(true));
        LinearLayout.LayoutParams firstTab = new LinearLayout.LayoutParams(0, Ui.dp(context, 32), 1f);
        LinearLayout.LayoutParams secondTab = new LinearLayout.LayoutParams(0, Ui.dp(context, 32), 1f);
        secondTab.leftMargin = Ui.dp(context, 8);
        tabs.addView(conversationsTab, firstTab);
        tabs.addView(filesTab, secondTab);
    }

    private void selectTab(boolean files) {
        filesMode = files;
        renderTabs();
        conversationsGroup.setVisibility(files ? GONE : VISIBLE);
        filesGroup.setVisibility(files ? VISIBLE : GONE);
        headerTitle.setText(getContext().getString(files
                ? R.string.drawer_title_files : R.string.drawer_title_conversations));
        filesRefresh.setVisibility(files ? VISIBLE : GONE);
        if (files) {
            String label = listener == null ? "" : listener.projectLabel();
            String path = listener == null ? "" : listener.projectPath();
            projectLabel.setText(label.isEmpty() ? "LineCode" : label);
            projectPath.setText(path);
            if (expanded.isEmpty()) expanded.add("");
            renderFiles();
        }
    }

    private void renderFiles() {
        fileList.removeAllViews();
        pathLabel.setText("/");
        backButton.setVisibility(INVISIBLE);
        List<WorkspaceNode> nodes = listener == null
                ? Collections.<WorkspaceNode>emptyList() : listener.files("");
        if (nodes == null) nodes = Collections.emptyList();
        emptyFiles.setVisibility(nodes.isEmpty() ? VISIBLE : GONE);
        for (WorkspaceNode node : nodes) addFileNode(node, 0);
    }

    private void addFileNode(WorkspaceNode node, int depth) {
        fileList.addView(fileRow(node, depth), itemParams());
        if (!node.directory || !expanded.contains(node.path)) return;
        List<WorkspaceNode> children = listener == null
                ? Collections.<WorkspaceNode>emptyList() : listener.files(node.path);
        if (children == null) return;
        for (WorkspaceNode child : children) addFileNode(child, depth + 1);
    }

    private LinearLayout fileRow(WorkspaceNode node, int depth) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setMinimumHeight(Ui.dp(context, 48));
        int left = 16 + Math.min(depth, 6) * 16;
        Ui.padding(row, left, 12, 16, 12);
        boolean open = node.directory && expanded.contains(node.path);
        int icon = node.directory ? (open ? Icons.FOLDER_OPEN : Icons.FOLDER)
                : fileIcon(node.name);
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(node.directory ? tokens.accent : tokens.textSecondary);
        glyph.setIconSizeDp(24, 16);
        glyph.setClickable(false);
        row.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 24), Ui.dp(context, 24)));
        TextView name = Ui.medium(context, node.name, 13, tokens.text);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = Ui.dp(context, 8);
        row.addView(name, nameParams);
        row.setOnClickListener(v -> {
            if (node.directory) {
                if (!expanded.remove(node.path)) expanded.add(node.path);
                renderFiles();
            } else if (listener != null) {
                listener.onOpenFile(node.path);
            }
        });
        return row;
    }

    private static int fileIcon(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".py") || lower.endsWith(".c")
                || lower.endsWith(".cc") || lower.endsWith(".cpp") || lower.endsWith(".h")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".gradle")) {
            return Icons.FILE_CODE;
        }
        return Icons.FILE_TEXT;
    }

    private LinearLayout tab(Context context, int icon, int labelRes, boolean active) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setBackground(active ? Ui.rounded(context, tokens.surfaceElevated, 6) : null);
        Ui.padding(tab, 12, 8, 12, 8);
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(active ? tokens.text : tokens.textSecondary);
        glyph.setIconSizeDp(14, 14);
        glyph.setClickable(false);
        tab.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14)));
        TextView label = active
                ? Ui.medium(context, context.getString(labelRes), Tokens.FONT_SM, tokens.text)
                : Ui.text(context, context.getString(labelRes), Tokens.FONT_SM,
                        tokens.textSecondary, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = Ui.dp(context, 4);
        tab.addView(label, labelParams);
        return tab;
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void setConversations(List<Conversation> conversations) {
        setConversations(conversations, currentId);
    }

    public void setConversations(List<Conversation> conversations, String currentId) {
        this.currentId = currentId == null ? "" : currentId;
        list.removeAllViews();
        List<Conversation> items = conversations == null ? Collections.<Conversation>emptyList() : conversations;
        empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        for (Conversation conversation : items) {
            list.addView(item(conversation), itemParams());
        }
    }

    private LinearLayout item(Conversation conversation) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        boolean selected = conversation.id.equals(currentId);
        row.setBackground(Ui.roundedStroke(getContext(),
                selected ? tokens.inputBg : tokens.surfaceElevated, 12,
                selected ? tokens.accent : tokens.borderLight));
        Ui.padding(row, 8, 16, 4, 16);
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect(conversation.id);
            }
        });

        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(text, 8, 0, 8, 0);
        TextView title = Ui.medium(getContext(), conversation.title, 15, tokens.text);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(title, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView time = Ui.text(getContext(), formatTime(conversation.updatedAt),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = Ui.dp(getContext(), 6);
        text.addView(time, timeParams);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        IconButton trash = new IconButton(getContext(), Icons.TRASH_2);
        trash.setIconColor(tokens.textTertiary);
        trash.setIconSizeDp(48, 18);
        trash.setContentDescription(getContext().getString(R.string.common_delete));
        trash.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(conversation.id);
            }
        });
        row.addView(trash, new LinearLayout.LayoutParams(Ui.dp(getContext(), 48), Ui.dp(getContext(), 48)));
        return row;
    }

    private LinearLayout.LayoutParams itemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(getContext(), 8);
        return params;
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
        return format.format(new java.util.Date(timestamp));
    }

    /** Offsets the sidebar below the status bar; the backdrop still covers the full window. */
    public void setTopInset(int topPx) {
        sidebar.setPadding(sidebar.getPaddingLeft(), topPx,
                sidebar.getPaddingRight(), sidebar.getPaddingBottom());
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        backdrop.setBackgroundColor(current.overlay);
        sidebar.setBackgroundColor(current.bg);
        empty.setTextColor(current.textTertiary);
        emptyFiles.setTextColor(current.textTertiary);
        pathLabel.setTextColor(current.textSecondary);
        backButton.setIconColor(current.textSecondary);
        projectLabel.setTextColor(current.text);
        projectPath.setTextColor(current.textTertiary);
        filesRefresh.setIconColor(current.textSecondary);
        renderTabs();
        if (filesMode) renderFiles();
    }

    public void open() {
        setVisibility(VISIBLE);
        sidebar.animate().cancel();
        backdrop.animate().cancel();
        sidebar.setTranslationX(-sidebar.getWidth());
        backdrop.setAlpha(0f);
        sidebar.animate().translationX(0f).setDuration(220).start();
        backdrop.animate().alpha(1f).setDuration(220).start();
        if (filesMode) renderFiles();
    }

    public void close() {
        sidebar.animate().cancel();
        backdrop.animate().cancel();
        sidebar.animate().translationX(-Math.max(sidebar.getWidth(), 1)).setDuration(180)
                .withEndAction(() -> setVisibility(GONE)).start();
        backdrop.animate().alpha(0f).setDuration(180).start();
    }
}
