package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import cn.lineai.core.model.Conversation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.DotMatrixSpinner;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Modal drawer mirroring C++ {@code drawer.cpp} + {@code LegacyDrawerStyle}: 360dp sidebar
 * (240dp floor, 48dp reveal), overlay scrim, 180ms EaseOut open / 150ms EaseIn close, 17sp bold
 * header (40/16/24/24), 14dp tabs, 52.62dp new-chat action, 48dp tree rows (16dp indent, cap 5)
 * and the extension-keyed file presentation rules.
 */
public final class DrawerPanel extends FrameLayout {

    /** One workspace tree node; children render only while expanded. */
    public static final class FileNode {
        /** Display name of the node. */
        public final String name;
        /** Slash-separated path relative to the workspace root. */
        public final String path;
        /** True for directories. */
        public final boolean directory;
        /** Child nodes; unmodifiable copy. */
        public final List<FileNode> children;

        /** @param name      display name
         * @param path      workspace-relative path
         * @param directory true for directories
         * @param children  child nodes, defensively copied */
        public FileNode(String name, String path, boolean directory, List<FileNode> children) {
            this.name = name == null ? "" : name;
            this.path = path == null ? "" : path;
            this.directory = directory;
            this.children = children == null ? Collections.<FileNode>emptyList()
                    : new ArrayList<>(children);
        }
    }

    /** Application-owned commands; the drawer never navigates or touches storage itself. */
    public interface Listener {
        /** Starts an empty conversation and closes the drawer. */
        void onNewConversation();
        /** Opens the selected conversation and closes the drawer. */
        void onSelect(String conversationId);
        /** Deletes the conversation without closing the drawer. */
        void onDelete(String conversationId);

        /** Opens or attaches the tapped file. */
        default void onFileSelected(String path, String name) { }

        /** Attachment entry point for the long-pressed tree node. */
        default void onFileLongPressed(String path, String name, boolean directory, boolean root) { }

        /** Long-press on a removable project strip. */
        default void onProjectRemoveRequested() { }

        /** Label shown in the files tab; empty falls back to the default label. */
        default String projectLabel() { return ""; }

        /** Path shown under the project label. */
        default String projectPath() { return ""; }

        /** True when the project strip supports long-press removal. */
        default boolean projectRemovable() { return false; }

        /** Loads the whole tree once; {@code null} keeps the "preparing" empty state. */
        default void loadFileTree(Consumer<FileNode> callback) { callback.accept(null); }

        /** Switches the chat view to a sub agent session; empty id selects the root. */
        default void onSelectAgent(String agentId) { }
    }

    private static final float MAX_WIDTH_DP = 360f;
    private static final float MIN_WIDTH_DP = 240f;

    private final View scrim;
    private final LinearLayout sidebar;
    private final TextView headerTitle;
    private final IconButton refresh;
    private final TabView conversationsTab;
    private final TabView filesTab;
    private final LinearLayout conversationsGroup;
    private final LinearLayout filesGroup;
    private final LinearLayout newConversation;
    private final TextView newLabel;
    private final IconButton newPlus;
    private final TextView emptyConversations;
    private final LinearLayout conversationList;
    private final LinearLayout projectStrip;
    private final TextView projectLabel;
    private final TextView projectPath;
    private final DrawerFileTree fileTree;
    private final DrawerAgentsView agentView;

    private Listener listener;
    private Tokens tokens;
    private String currentId = "";
    /** Conversation currently streaming; empty when idle. */
    private String runningId = "";
    private boolean filesMode;

    /** Builds scrim, sidebar, header, tabs, conversation list and the files group. */
    public DrawerPanel(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setVisibility(GONE);
        scrim = new View(context);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> close());
        addView(scrim, new LayoutParams(-1, -1));
        sidebar = column(context);
        addView(sidebar, new LayoutParams(sidebarWidth(context), -1, Gravity.START));

        headerTitle = Ui.bold(context, string(R.string.drawer_title_conversations),
                Tokens.FONT_LG, tokens.text);
        refresh = new IconButton(context, Icons.REFRESH_CW);
        refresh.setIconSizeDp(32, 16);
        refresh.setContentDescription(string(R.string.common_refresh));
        refresh.setOnClickListener(v -> loadTree());
        refresh.setVisibility(GONE);
        LinearLayout header = row(context);
        Ui.padding(header, 24, 40, 16, 24);
        header.addView(headerTitle, lp(0, -2, 1f));
        header.addView(refresh, margins(32, 32, 8, 0, 0, 0));
        sidebar.addView(header, lp(-1, -2));

        conversationsTab = new TabView(context, Icons.MESSAGE_SQUARE,
                R.string.drawer_tab_conversations, true);
        filesTab = new TabView(context, Icons.FOLDER_OPEN, R.string.drawer_tab_files, false);
        conversationsTab.setOnClickListener(v -> selectTab(false));
        filesTab.setOnClickListener(v -> selectTab(true));
        LinearLayout tabsOutside = row(context);
        Ui.padding(tabsOutside, 16, 0, 16, 12);
        LinearLayout tabs = row(context);
        Ui.padding(tabs, 2, 2, 2, 2);
        tabs.addView(conversationsTab, lp(0, -2, 1f));
        tabs.addView(filesTab, lp(0, -2, 1f));
        tabsOutside.addView(tabs, lp(-1, -2));
        sidebar.addView(tabsOutside, lp(-1, -2));

        conversationsGroup = column(context);
        newConversation = row(context);
        newConversation.setClickable(true);
        newConversation.setFocusable(true);
        Ui.padding(newConversation, 16, 12, 16, 12);
        newPlus = inlineIcon(context, Icons.PLUS, 18);
        newConversation.addView(newPlus, lp(18, 18));
        newLabel = Ui.bold(context, string(R.string.drawer_new_conversation),
                Tokens.FONT_MD, tokens.text);
        newConversation.addView(newLabel, margins(-2, -2, 8, 0, 0, 0));
        newConversation.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNewConversation();
            }
            close();
        });
        LinearLayout.LayoutParams newParams = margins(-1, -2, 16, 0, 16, 12);
        newParams.height = Math.round(dp(52.62f));
        conversationsGroup.addView(newConversation, newParams);
        ScrollView conversationScroll = new ScrollView(context);
        conversationScroll.setVerticalScrollBarEnabled(false);
        LinearLayout conversationContent = column(context);
        emptyConversations = emptyState(context, R.string.drawer_empty_conversations);
        conversationContent.addView(emptyConversations, lp(-1, -2));
        conversationList = column(context);
        Ui.padding(conversationList, 12, 12, 12, 32);
        conversationContent.addView(conversationList, lp(-1, -2));
        conversationScroll.addView(conversationContent, lp(-1, -2));
        conversationsGroup.addView(conversationScroll, lp(-1, 0, 1f));
        sidebar.addView(conversationsGroup, lp(-1, 0, 1f));

        filesGroup = column(context);
        filesGroup.setVisibility(GONE);
        projectStrip = column(context);
        Ui.padding(projectStrip, 8, 8, 8, 8);
        projectLabel = Ui.bold(context, string(R.string.header_project_default),
                Tokens.FONT_SM, tokens.text);
        projectLabel.setSingleLine(true);
        projectLabel.setEllipsize(TextUtils.TruncateAt.END);
        projectPath = Ui.text(context, "", Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        oneLine(projectPath, 30);
        projectStrip.addView(projectLabel, lp(-1, -2));
        projectStrip.addView(projectPath, margins(-1, -2, 0, 6, 0, 0));
        LinearLayout projectWrapper = row(context);
        Ui.padding(projectWrapper, 16, 0, 16, 8);
        projectWrapper.addView(projectStrip, lp(0, -2, 1f));
        agentView = new DrawerAgentsView(context, tokens, agentId -> {
            if (listener != null) {
                listener.onSelectAgent(agentId);
            }
        });
        projectWrapper.addView(agentView);
        filesGroup.addView(projectWrapper, lp(-1, -2));
        fileTree = new DrawerFileTree(context, tokens);
        fileTree.setCallback(new DrawerFileTree.Callback() {
            @Override
            public void onFileSelected(String path, String name) {
                if (listener != null) {
                    listener.onFileSelected(path, name);
                }
            }

            @Override
            public void onLongPressed(String path, String name,
                                      boolean directory, boolean root) {
                if (listener != null) {
                    listener.onFileLongPressed(path, name, directory, root);
                }
            }
        });
        filesGroup.addView(fileTree, lp(-1, 0, 1f));
        sidebar.addView(filesGroup, lp(-1, 0, 1f));
        restyleStatic();
    }

    // --------------------------------------------------------------- helpers

    /** Localized string shorthand. */
    private String string(int res) {
        return getContext().getString(res);
    }

    /** Instance dp conversion. */
    private int dp(float value) {
        return Ui.dp(getContext(), value);
    }

    /** Static dp conversion. */
    private static int dp(Context context, float value) {
        return Ui.dp(context, value);
    }

    /** Vertical container. */
    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /** Centered horizontal container. */
    private static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    /** Bare layout parameters. */
    private static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    /** Weighted layout parameters. */
    private static LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    /** Layout parameters in dp with all four margins. */
    private LinearLayout.LayoutParams margins(int width, int height, int left, int top,
            int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        params.leftMargin = dp(left);
        params.topMargin = dp(top);
        params.rightMargin = dp(right);
        params.bottomMargin = dp(bottom);
        return params;
    }

    /** Single-line ellipsized label with a height cap. */
    private static void oneLine(TextView view, int maxHeightDp) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMaxHeight(Ui.dp(view.getContext(), maxHeightDp));
    }

    /** Non-clickable square glyph. */
    private static IconButton inlineIcon(Context context, int icon, int sizeDp) {
        IconButton button = new IconButton(context, icon);
        button.setIconSizeDp(sizeDp, sizeDp);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setClickable(false);
        return button;
    }

    /** Centered tertiary empty-state label. */
    private TextView emptyState(Context context, int text) {
        TextView view = Ui.text(context, context.getString(text), Tokens.FONT_SM,
                tokens.textTertiary, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        Ui.padding(view, 0, 80, 0, 0);
        return view;
    }

    // ------------------------------------------------------------------ chrome

    /** Switches between conversations and files; entering files binds the project and tree. */
    private void selectTab(boolean files) {
        if (filesMode == files) {
            return;
        }
        filesMode = files;
        conversationsGroup.setVisibility(files ? GONE : VISIBLE);
        filesGroup.setVisibility(files ? VISIBLE : GONE);
        headerTitle.setText(string(files
                ? R.string.drawer_title_files : R.string.drawer_title_conversations));
        refresh.setVisibility(files ? VISIBLE : GONE);
        restyleStatic();
        if (files) {
            bindProject();
            loadTree();
        }
    }

    /** Refreshes the project strip from the listener, wiring removal when supported. */
    private void bindProject() {
        String label = listener == null ? "" : listener.projectLabel();
        projectLabel.setText(label.isEmpty() ? string(R.string.header_project_default) : label);
        projectPath.setText(listener == null ? "" : listener.projectPath());
        boolean removable = listener != null && listener.projectRemovable();
        projectStrip.setFocusable(removable);
        if (removable) {
            projectStrip.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onProjectRemoveRequested();
                }
                return true;
            });
        } else {
            projectStrip.setOnLongClickListener(null);
        }
    }

    // ---------------------------------------------------------- conversations

    /** Re-renders the conversation list keeping the current selection. */
    /** Updates the live sub agent rows and the chip count. */
    public void setAgents(java.util.List<cn.lineai.tool.api.AgentEntry> agents,
                          String selectedAgentId) {
        agentView.setAgents(agents, selectedAgentId);
    }

    public void setConversations(List<Conversation> conversations) {
        setConversations(conversations, currentId, runningId);
    }

    /** Re-renders the conversation list with the given selection; null input means empty. */
    public void setConversations(List<Conversation> conversations, String currentId) {
        setConversations(conversations, currentId, runningId);
    }

    /**
     * Re-renders the conversation list with the selection and the conversation whose turn is
     * currently running (its delete action is replaced by a progress spinner).
     */
    public void setConversations(List<Conversation> conversations, String currentId,
                                 String runningId) {
        this.currentId = currentId == null ? "" : currentId;
        this.runningId = runningId == null ? "" : runningId;
        conversationList.removeAllViews();
        List<Conversation> items = conversations == null
                ? Collections.<Conversation>emptyList() : conversations;
        emptyConversations.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        for (Conversation conversation : items) {
            DrawerConversationRow row = new DrawerConversationRow(getContext(), tokens,
                    conversation, conversation.id.equals(this.currentId),
                    conversation.id.equals(this.runningId));
            row.callback(new DrawerConversationRow.Callback() {
                @Override public void onSelect(String id) {
                    if (listener != null) {
                        listener.onSelect(id);
                    }
                    close();
                }

                @Override public void onDelete(String id) {
                    if (listener != null) {
                        listener.onDelete(id);
                    }
                }
            });
            conversationList.addView(row, margins(-1, -2, 0, 0, 0, 8));
        }
    }

    // ------------------------------------------------------------------ files

    /** Triggers a tree reload through the listener; no listener means the empty state. */
    private void loadTree() {
        if (listener == null) {
            fileTree.setTree(null);
            return;
        }
        fileTree.beginLoading();
        listener.loadFileTree(fileTree::setTree);
    }

    // ------------------------------------------------------------------- tabs

    /** One 14dp-height tab with an active pill background. */
    private final class TabView extends LinearLayout {
        private final IconButton glyph;
        private final TextView label;
        private final boolean conversations;

        /** Builds the tab; styling depends on which tab is active. */
        TabView(Context context, int icon, int labelRes, boolean conversations) {
            super(context);
            this.conversations = conversations;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            Ui.padding(this, 0, 8, 0, 8);
            glyph = inlineIcon(context, icon, 24);
            addView(glyph, lp(24, 24));
            label = Ui.text(context, context.getString(labelRes), Tokens.FONT_SM,
                    tokens.textTertiary, Typeface.NORMAL);
            label.setSingleLine(true);
            label.setMaxHeight(dp(18));
            addView(label, margins(-2, -2, 8, 0, 0, 0));
            restyle();
        }

        /** Applies the active or inactive tab styling. */
        void restyle() {
            boolean active = conversations != filesMode;
            setBackground(active ? Ui.rounded(getContext(), tokens.surfaceElevated, 6) : null);
            int tint = active ? tokens.accent : tokens.textTertiary;
            glyph.setIconColor(tint);
            label.setTextColor(tint);
            label.setTypeface(active
                    ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        }
    }

    /** Restyles every chrome element and delegates to rows, tabs and the tree. */
    private void restyleStatic() {
        scrim.setBackgroundColor(tokens.overlay);
        sidebar.setBackgroundColor(tokens.bg);
        headerTitle.setTextColor(tokens.text);
        refresh.setIconColor(tokens.accent);
        emptyConversations.setTextColor(tokens.textTertiary);
        fileTree.applyTokens(tokens);
        projectLabel.setTextColor(tokens.text);
        projectPath.setTextColor(tokens.textTertiary);
        newConversation.setBackground(Ui.rounded(getContext(), tokens.inputBg, 14));
        newLabel.setTextColor(tokens.text);
        newPlus.setIconColor(tokens.text);
        conversationsTab.restyle();
        filesTab.restyle();
        for (int i = 0; i < conversationList.getChildCount(); i++) {
            View child = conversationList.getChildAt(i);
            if (child instanceof DrawerConversationRow) {
                ((DrawerConversationRow) child).restyle();
            }
        }
    }

    // -------------------------------------------------------------------- API

    /** Installs the application-owned commands. */
    public void setListener(Listener value) {
        listener = value;
    }

    /** Restyles in place; no list is rebuilt, so scroll and expansion survive a theme switch. */
    public void applyTokens(Tokens current) {
        tokens = current;
        restyleStatic();
        agentView.applyTokens(current);
    }

    /** Offsets the sidebar below the status bar; the scrim still covers the full window. */
    public void setTopInset(int topPx) {
        sidebar.setPadding(sidebar.getPaddingLeft(), topPx,
                sidebar.getPaddingRight(), sidebar.getPaddingBottom());
    }

    /** Shows the drawer and slides the sidebar in with the C++ easing. */
    public void open() {
        refreshProject();
        setVisibility(VISIBLE);
        animateSidebar(-width(), 0f, 180L, true);
    }

    /** Rebinds the project header and reloads the tree after the selected workspace changes. */
    public void refreshProject() {
        bindProject();
        if (filesMode) {
            loadTree();
        }
    }

    /** Slides the drawer out and hides it when the animation ends. */
    public void close() {
        if (getVisibility() == VISIBLE) {
            animateSidebar(0f, -width(), 150L, false);
        }
    }

    /** Runs the paired sidebar translation and scrim fade. */
    private void animateSidebar(float from, float to, long duration, boolean opening) {
        sidebar.animate().cancel();
        scrim.animate().cancel();
        if (opening) {
            sidebar.setTranslationX(from);
            scrim.setAlpha(0f);
        }
        PathInterpolator easing = opening ? new PathInterpolator(0f, 0f, 0.58f, 1f)
                : new PathInterpolator(0.42f, 0f, 1f, 1f);
        sidebar.animate().translationX(to).setDuration(duration).setInterpolator(easing)
                .withEndAction(() -> {
                    if (!opening) {
                        setVisibility(GONE);
                    }
                }).start();
        scrim.animate().alpha(opening ? 1f : 0f).setDuration(duration)
                .setInterpolator(easing).start();
    }

    /** Current sidebar width, falling back to the computed default. */
    private int width() {
        ViewGroup.LayoutParams params = sidebar.getLayoutParams();
        return params != null && params.width > 0 ? params.width : sidebarWidth(getContext());
    }

    /** Sidebar width clamped to 240..360dp with a 48dp reveal floor. */
    private static int sidebarWidth(Context context) {
        int available = context.getResources().getDisplayMetrics().widthPixels - dp(context, 48);
        return Math.max(dp(context, MIN_WIDTH_DP),
                Math.min(dp(context, MAX_WIDTH_DP), available));
    }

    /** Compact "M/d HH:mm" timestamp; non-positive values render empty. */
    private static String formatTime(long timestamp) {
        return timestamp <= 0 ? "" : new SimpleDateFormat("M/d HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }
}
