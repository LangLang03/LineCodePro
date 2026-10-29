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
        public final String name;
        public final String path;
        public final boolean directory;
        public final List<FileNode> children;

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
        void onNewConversation();
        void onSelect(String conversationId);
        void onDelete(String conversationId);

        default void onFileSelected(String path, String name) { }

        default void onFileLongPressed(String path, String name, boolean directory, boolean root) { }

        default void onProjectRemoveRequested() { }

        default String projectLabel() { return ""; }

        default String projectPath() { return ""; }

        default boolean projectRemovable() { return false; }

        /** Loads the whole tree once; {@code null} keeps the "preparing" empty state. */
        default void loadFileTree(Consumer<FileNode> callback) { callback.accept(null); }
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

    private Listener listener;
    private Tokens tokens;
    private String currentId = "";
    private boolean filesMode;

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

    private String string(int res) {
        return getContext().getString(res);
    }

    private int dp(float value) {
        return Ui.dp(getContext(), value);
    }

    private static int dp(Context context, float value) {
        return Ui.dp(context, value);
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private static LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

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

    private static void oneLine(TextView view, int maxHeightDp) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMaxHeight(Ui.dp(view.getContext(), maxHeightDp));
    }

    private static IconButton inlineIcon(Context context, int icon, int sizeDp) {
        IconButton button = new IconButton(context, icon);
        button.setIconSizeDp(sizeDp, sizeDp);
        button.setClickable(false);
        return button;
    }

    private TextView emptyState(Context context, int text) {
        TextView view = Ui.text(context, context.getString(text), Tokens.FONT_SM,
                tokens.textTertiary, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        Ui.padding(view, 0, 80, 0, 0);
        return view;
    }

    // ------------------------------------------------------------------ chrome

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

    public void setConversations(List<Conversation> conversations) {
        setConversations(conversations, currentId);
    }

    public void setConversations(List<Conversation> conversations, String currentId) {
        this.currentId = currentId == null ? "" : currentId;
        conversationList.removeAllViews();
        List<Conversation> items = conversations == null
                ? Collections.<Conversation>emptyList() : conversations;
        emptyConversations.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        for (Conversation conversation : items) {
            conversationList.addView(new ConversationRow(conversation,
                    conversation.id.equals(this.currentId)), margins(-1, -2, 0, 0, 0, 8));
        }
    }

    private final class ConversationRow extends LinearLayout {
        private final TextView title;
        private final TextView time;
        private final IconButton trash;
        private final boolean selected;

        ConversationRow(Conversation conversation, boolean selected) {
            super(DrawerPanel.this.getContext());
            this.selected = selected;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setClickable(true);
            setFocusable(true);
            Ui.padding(this, 8, 16, 4, 16);
            setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSelect(conversation.id);
                }
                close();
            });
            LinearLayout labels = column(getContext());
            Ui.padding(labels, 8, 0, 8, 0);
            title = selected
                    ? Ui.medium(getContext(), conversation.title, Tokens.FONT_MD, tokens.text)
                    : Ui.text(getContext(), conversation.title, Tokens.FONT_MD, tokens.text,
                            Typeface.NORMAL);
            title.setMaxHeight(dp(21));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title, lp(-1, -2));
            time = Ui.text(getContext(), formatTime(conversation.updatedAt), Tokens.FONT_XS,
                    tokens.textTertiary, Typeface.NORMAL);
            labels.addView(time, margins(-1, -2, 0, 6, 0, 0));
            addView(labels, lp(0, -2, 1f));
            trash = new IconButton(getContext(), Icons.TRASH_2);
            trash.setIconSizeDp(48, 16);
            trash.setContentDescription(string(R.string.common_delete));
            trash.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(conversation.id);
                }
            });
            addView(trash, lp(48, 48));
            restyle();
        }

        void restyle() {
            setBackground(Ui.roundedStroke(getContext(),
                    selected ? tokens.inputBg : tokens.surfaceElevated, 12,
                    selected ? tokens.accent : tokens.borderLight));
            title.setTextColor(tokens.text);
            time.setTextColor(tokens.textTertiary);
            trash.setIconColor(tokens.textTertiary);
        }
    }

    // ------------------------------------------------------------------ files

    private void loadTree() {
        if (listener == null) {
            fileTree.setTree(null);
            return;
        }
        fileTree.beginLoading();
        listener.loadFileTree(fileTree::setTree);
    }

    // ------------------------------------------------------------------- tabs

    private final class TabView extends LinearLayout {
        private final IconButton glyph;
        private final TextView label;
        private final boolean conversations;

        TabView(Context context, int icon, int labelRes, boolean conversations) {
            super(context);
            this.conversations = conversations;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            Ui.padding(this, 0, 8, 0, 8);
            glyph = inlineIcon(context, icon, 20);
            addView(glyph, lp(20, 20));
            label = Ui.text(context, context.getString(labelRes), Tokens.FONT_SM,
                    tokens.textTertiary, Typeface.NORMAL);
            label.setSingleLine(true);
            label.setMaxHeight(dp(18));
            addView(label, margins(-2, -2, 6, 0, 0, 0));
            restyle();
        }

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
            if (child instanceof ConversationRow) {
                ((ConversationRow) child).restyle();
            }
        }
    }

    // -------------------------------------------------------------------- API

    public void setListener(Listener value) {
        listener = value;
    }

    /** Restyles in place; no list is rebuilt, so scroll and expansion survive a theme switch. */
    public void applyTokens(Tokens current) {
        tokens = current;
        restyleStatic();
    }

    /** Offsets the sidebar below the status bar; the scrim still covers the full window. */
    public void setTopInset(int topPx) {
        sidebar.setPadding(sidebar.getPaddingLeft(), topPx,
                sidebar.getPaddingRight(), sidebar.getPaddingBottom());
    }

    public void open() {
        setVisibility(VISIBLE);
        animateSidebar(-width(), 0f, 180L, true);
    }

    public void close() {
        if (getVisibility() == VISIBLE) {
            animateSidebar(0f, -width(), 150L, false);
        }
    }

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

    private int width() {
        ViewGroup.LayoutParams params = sidebar.getLayoutParams();
        return params != null && params.width > 0 ? params.width : sidebarWidth(getContext());
    }

    private static int sidebarWidth(Context context) {
        int available = context.getResources().getDisplayMetrics().widthPixels - dp(context, 48);
        return Math.max(dp(context, MIN_WIDTH_DP),
                Math.min(dp(context, MAX_WIDTH_DP), available));
    }

    private static String formatTime(long timestamp) {
        return timestamp <= 0 ? "" : new SimpleDateFormat("M/d HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }
}
