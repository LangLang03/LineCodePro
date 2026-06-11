package cn.lineai.ui.component;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.model.FileTreeNode;
import cn.lineai.ui.theme.LineTheme;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DrawerView extends FrameLayout {
    public interface Listener {
        void onCloseDrawer();

        void onNewConversation();

        void onConversationSelected(String id);

        void onConversationDeleted(String id);

        void onCurrentProjectRemoveRequested();

        void onFileNodeSelected(String path, boolean directory);

        void onFileNodeLongPressed(String path, String name, boolean directory, boolean root);

        void onFileTreeActivated();

        void onFileTreeRefresh();
    }

    private static final int TAB_CONVERSATIONS = 0;
    private static final int TAB_FILES = 1;

    private final View backdrop;
    private final LinearLayout sidebar;
    private final TextView headerTitle;
    private final LinearLayout headerActions;
    private final LinearLayout body;
    private final LinearLayout tabs;
    private List<ConversationRecord> conversations;
    private String currentConversationId = "";
    private String projectLabel = "LineCode";
    private String projectPath = "";
    private boolean projectRemovable;
    private FileTreeNode fileTree;
    private Listener listener;
    private int activeTab = TAB_CONVERSATIONS;
    private int renderedBodyTab = -1;
    private List<ConversationRecord> renderedConversations;
    private String renderedConversationId = "";
    private String renderedProjectLabel = "";
    private String renderedProjectPath = "";
    private boolean renderedProjectRemovable;
    private FileTreeNode renderedFileTree;

    public DrawerView(Context context) {
        super(context);
        setVisibility(GONE);
        setClickable(true);

        backdrop = new View(context);
        backdrop.setBackgroundColor(LineTheme.OVERLAY);
        backdrop.setOnClickListener(v -> close());
        addView(backdrop, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        sidebar = new LinearLayout(context);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(LineTheme.SURFACE_ELEVATED);
        FrameLayout.LayoutParams sidebarParams = new FrameLayout.LayoutParams(LineTheme.dp(context, 300), LayoutParams.MATCH_PARENT);
        sidebarParams.gravity = Gravity.START;
        addView(sidebar, sidebarParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(header, LineTheme.LG, 50, LineTheme.LG, LineTheme.MD);
        sidebar.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        headerTitle = LineTheme.text(context, "对话历史", LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        headerActions = new LinearLayout(context);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(headerActions, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(tabs, 2, 2, 2, 2);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        tabParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        tabParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        sidebar.addView(tabs, tabParams);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        sidebar.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        renderChrome();
        renderBody();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(
            List<ConversationRecord> conversations,
            String currentConversationId,
            String projectLabel,
            String projectPath,
            boolean projectRemovable,
            FileTreeNode fileTree
    ) {
        boolean sameBody = isSameBody(
                conversations,
                currentConversationId,
                projectLabel,
                projectPath,
                projectRemovable,
                fileTree
        );
        this.conversations = conversations;
        this.currentConversationId = currentConversationId == null ? "" : currentConversationId;
        this.projectLabel = projectLabel == null ? "LineCode" : projectLabel;
        this.projectPath = projectPath == null ? "" : projectPath;
        this.projectRemovable = projectRemovable;
        this.fileTree = fileTree;
        renderChrome();
        if (!sameBody) {
            renderBody();
        }
    }

    public void open() {
        if (getVisibility() == VISIBLE) {
            return;
        }
        setVisibility(VISIBLE);
        bringToFront();
        sidebar.setTranslationX(-LineTheme.dp(getContext(), 300));
        backdrop.setAlpha(0f);
        sidebar.animate().translationX(0).setDuration(180).start();
        backdrop.animate().alpha(1f).setDuration(200).start();
    }

    public void close() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        sidebar.animate().translationX(-LineTheme.dp(getContext(), 300)).setDuration(180).start();
        backdrop.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            setVisibility(GONE);
            if (listener != null) {
                listener.onCloseDrawer();
            }
        }).start();
    }

    public boolean isFilesTabActive() {
        return activeTab == TAB_FILES;
    }

    private void setActiveTab(int tab) {
        if (activeTab == tab) {
            return;
        }
        activeTab = tab;
        renderChrome();
        renderBody();
        if (activeTab == TAB_FILES && listener != null) {
            listener.onFileTreeActivated();
        }
    }

    private void renderChrome() {
        Context context = getContext();
        headerTitle.setText(activeTab == TAB_CONVERSATIONS ? "对话历史" : "文件管理器");
        headerActions.removeAllViews();

        if (activeTab == TAB_FILES) {
            IconButtonView refresh = headerIcon(context, IconButtonView.REFRESH_CW, LineTheme.ACCENT, 16);
            refresh.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileTreeRefresh();
                }
            });
            headerActions.addView(refresh);
        }

        tabs.removeAllViews();
        tabs.addView(tabButton(context, IconButtonView.MESSAGE_SQUARE, "对话", activeTab == TAB_CONVERSATIONS, () -> setActiveTab(TAB_CONVERSATIONS)),
                new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(tabButton(context, IconButtonView.FOLDER_OPEN, "文件", activeTab == TAB_FILES, () -> setActiveTab(TAB_FILES)),
                new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    private void renderBody() {
        body.removeAllViews();
        if (activeTab == TAB_CONVERSATIONS) {
            renderConversations();
        } else {
            renderFiles();
        }
        renderedBodyTab = activeTab;
        renderedConversations = conversations;
        renderedConversationId = currentConversationId;
        renderedProjectLabel = projectLabel;
        renderedProjectPath = projectPath;
        renderedProjectRemovable = projectRemovable;
        renderedFileTree = fileTree;
    }

    private boolean isSameBody(
            List<ConversationRecord> nextConversations,
            String nextConversationId,
            String nextProjectLabel,
            String nextProjectPath,
            boolean nextProjectRemovable,
            FileTreeNode nextFileTree
    ) {
        if (renderedBodyTab != activeTab) {
            return false;
        }
        if (activeTab == TAB_FILES) {
            return renderedFileTree == nextFileTree
                    && renderedProjectRemovable == nextProjectRemovable
                    && same(renderedProjectLabel, nextProjectLabel == null ? "LineCode" : nextProjectLabel)
                    && same(renderedProjectPath, nextProjectPath == null ? "" : nextProjectPath);
        }
        return renderedConversations == nextConversations
                && same(renderedConversationId, nextConversationId == null ? "" : nextConversationId);
    }

    private boolean same(String left, String right) {
        String safeLeft = left == null ? "" : left;
        String safeRight = right == null ? "" : right;
        return safeLeft.equals(safeRight);
    }

    private void renderConversations() {
        Context context = getContext();
        LinearLayout newButton = new LinearLayout(context);
        newButton.setOrientation(LinearLayout.HORIZONTAL);
        newButton.setGravity(Gravity.CENTER);
        newButton.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 12));
        newButton.setClickable(true);
        newButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNewConversation();
            }
            close();
        });
        IconButtonView plus = inlineIcon(context, IconButtonView.PLUS, android.graphics.Color.BLACK, 18);
        newButton.addView(plus, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        TextView label = LineTheme.text(context, "新建对话", LineTheme.FONT_MD, android.graphics.Color.BLACK, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        newButton.addView(label, labelParams);
        LinearLayout.LayoutParams newParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 45));
        newParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        newParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        newParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        body.addView(newButton, newParams);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(list, LineTheme.SM, 0, LineTheme.SM, 0);
        scrollView.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        body.addView(scrollView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        if (conversations == null || conversations.isEmpty()) {
            TextView empty = LineTheme.text(context, "暂无对话记录", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            emptyParams.topMargin = LineTheme.dp(context, 80);
            list.addView(empty, emptyParams);
            return;
        }
        for (ConversationRecord conversation : conversations) {
            addConversationItem(
                    list,
                    conversation.getId(),
                    conversation.getTitle(),
                    formatTime(conversation.getUpdatedAt()),
                    conversation.getId().equals(currentConversationId)
            );
        }
    }

    private void renderFiles() {
        Context context = getContext();
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 8, LineTheme.BORDER_LIGHT));
        LineTheme.padding(strip, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        if (projectRemovable) {
            strip.setClickable(true);
        }

        TextView project = LineTheme.text(context, projectLabel, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.BOLD);
        strip.addView(project, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView path = LineTheme.text(context, projectPath, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        path.setSingleLine(false);
        path.setMaxLines(2);
        path.setEllipsize(TextUtils.TruncateAt.END);
        path.setHorizontallyScrolling(false);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pathParams.topMargin = LineTheme.dp(context, 2);
        strip.addView(path, pathParams);
        if (projectRemovable) {
            attachRemoveProjectLongPress(strip);
        }

        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        stripParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        stripParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        stripParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        body.addView(strip, stripParams);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout tree = new LinearLayout(context);
        tree.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(tree, LineTheme.SM, LineTheme.SM, LineTheme.SM, 0);
        scrollView.addView(tree, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        body.addView(scrollView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        if (fileTree == null) {
            TextView empty = LineTheme.text(context, "正在准备目录...", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            emptyParams.topMargin = LineTheme.dp(context, 80);
            tree.addView(empty, emptyParams);
            return;
        }
        addFileNode(tree, fileTree, 0, true);
    }

    private void showRemoveProjectDialog() {
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("移除工作区")
                .setMessage("从已打开目录中移除「" + projectLabel + "」？\n\n不会删除真实目录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> {
                    if (listener != null) {
                        listener.onCurrentProjectRemoveRequested();
                    }
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(LineTheme.DANGER));
        dialog.show();
    }

    private void attachRemoveProjectLongPress(View view) {
        LongPressTouchHandler handler = new LongPressTouchHandler(this::showRemoveProjectDialog);
        attachRemoveProjectLongPress(view, handler);
    }

    private void attachRemoveProjectLongPress(View view, LongPressTouchHandler handler) {
        view.setHapticFeedbackEnabled(true);
        view.setOnTouchListener(handler);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachRemoveProjectLongPress(group.getChildAt(i), handler);
            }
        }
    }

    private IconButtonView headerIcon(Context context, int type, int color, int iconSizeDp) {
        IconButtonView icon = new IconButtonView(context, type);
        icon.setIconColor(color);
        icon.setIconSizeDp(32, iconSizeDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(context, 32), LineTheme.dp(context, 32));
        params.leftMargin = LineTheme.dp(context, LineTheme.SM);
        icon.setLayoutParams(params);
        return icon;
    }

    private View tabButton(Context context, int iconType, String text, boolean active, Runnable onClick) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER);
        tab.setBackground(active ? LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 6) : null);
        tab.setClickable(true);
        tab.setOnClickListener(v -> onClick.run());
        LineTheme.padding(tab, 0, LineTheme.SM, 0, LineTheme.SM);

        int color = active ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY;
        IconButtonView icon = inlineIcon(context, iconType, color, 14);
        tab.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 14), LineTheme.dp(context, 14)));

        TextView label = active
                ? LineTheme.textMedium(context, text, LineTheme.FONT_SM, color)
                : LineTheme.text(context, text, LineTheme.FONT_SM, color, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(context, 4);
        tab.addView(label, labelParams);
        return tab;
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

    private void addConversationItem(LinearLayout list, String id, String title, String time, boolean active) {
        Context context = list.getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackground(active ? LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 8) : null);
        item.setClickable(true);
        item.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationSelected(id);
            }
            close();
        });
        LineTheme.padding(item, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        list.addView(item, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 14));
        IconButtonView messageIcon = inlineIcon(context, IconButtonView.MESSAGE_SQUARE, active ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY, 16);
        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16), Gravity.CENTER);
        iconBox.addView(messageIcon, messageParams);
        item.addView(iconBox, new LinearLayout.LayoutParams(LineTheme.dp(context, 28), LineTheme.dp(context, 28)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        textsParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        item.addView(texts, textsParams);

        TextView titleView = active
                ? LineTheme.textMedium(context, title, LineTheme.FONT_SM, LineTheme.ACCENT)
                : LineTheme.text(context, title, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        titleView.setSingleLine(true);
        texts.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView timeView = LineTheme.text(context, time, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = LineTheme.dp(context, 2);
        texts.addView(timeView, timeParams);

        IconButtonView trash = sizedIcon(context, IconButtonView.TRASH_2, LineTheme.TEXT_TERTIARY, 22, 14);
        trash.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationDeleted(id);
            }
        });
        item.addView(trash, new LinearLayout.LayoutParams(LineTheme.dp(context, 22), LineTheme.dp(context, 22)));
    }

    private void addFileNode(LinearLayout tree, FileTreeNode node, int depth, boolean root) {
        int iconType;
        int iconColor;
        int iconSize;
        if (node.isDirectory()) {
            iconType = node.isExpanded() ? IconButtonView.FOLDER_OPEN : IconButtonView.FOLDER;
            iconColor = node.isExpanded() ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY;
            iconSize = 16;
        } else {
            iconType = iconForFile(node.getName());
            iconColor = colorForFile(node.getName(), iconType);
            iconSize = 14;
        }
        addFileRow(tree, node.getPath(), iconType, node.getName(), depth, root, node.isDirectory(), iconColor, iconSize);
        if (node.isDirectory() && node.isExpanded()) {
            List<FileTreeNode> children = node.getChildren();
            for (int i = 0; i < children.size(); i++) {
                addFileNode(tree, children.get(i), depth + 1, false);
            }
        }
    }

    private void addFileRow(
            LinearLayout tree,
            String path,
            int iconType,
            String name,
            int depth,
            boolean root,
            boolean directory,
            int iconColor,
            int iconSize
    ) {
        Context context = tree.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileNodeSelected(path, directory);
            }
        });
        row.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onFileNodeLongPressed(path, name, directory, root);
            }
            return true;
        });
        int left = LineTheme.SM + depth * 16;
        LineTheme.padding(row, left, 4, LineTheme.SM, 4);
        tree.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        IconButtonView icon = inlineIcon(context, iconType, iconColor, iconSize);
        row.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, iconSize), LineTheme.dp(context, iconSize)));

        TextView label = LineTheme.text(context, name, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(label, labelParams);

        if (root) {
            IconButtonView plus = sizedIcon(context, IconButtonView.PLUS, LineTheme.TEXT_TERTIARY, 22, 14);
            plus.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileNodeLongPressed(path, name, true, true);
                }
            });
            row.addView(plus, new LinearLayout.LayoutParams(LineTheme.dp(context, 22), LineTheme.dp(context, 22)));
        }
    }

    private String formatTime(long updatedAt) {
        if (updatedAt <= 0) {
            return "";
        }
        return new SimpleDateFormat("M/d HH:mm", Locale.US).format(new Date(updatedAt));
    }

    private int iconForFile(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".jsx")
                || lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".gradle")) {
            return IconButtonView.FILE_CODE;
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".log")) {
            return IconButtonView.FILE_TEXT;
        }
        return IconButtonView.FILE;
    }

    private int colorForFile(String name, int iconType) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (iconType == IconButtonView.FILE_CODE) {
            if (lower.endsWith(".xml")) {
                return LineTheme.WARNING;
            }
            return android.graphics.Color.parseColor("#F0DB4F");
        }
        if (iconType == IconButtonView.FILE_TEXT) {
            return LineTheme.TEXT_SECONDARY;
        }
        return LineTheme.TEXT_TERTIARY;
    }

    private IconButtonView sizedIcon(Context context, int type, int color, int containerSize, int iconSize) {
        IconButtonView icon = inlineIcon(context, type, color, iconSize);
        icon.setIconSizeDp(containerSize, iconSize);
        return icon;
    }

    private static final class LongPressTouchHandler implements View.OnTouchListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable action;
        private float downX;
        private float downY;
        private int touchSlop;
        private boolean fired;
        private Runnable pending;

        LongPressTouchHandler(Runnable action) {
            this.action = action;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int eventAction = event.getActionMasked();
            if (eventAction == MotionEvent.ACTION_DOWN) {
                touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                downX = event.getRawX();
                downY = event.getRawY();
                fired = false;
                cancel();
                pending = () -> {
                    fired = true;
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    action.run();
                };
                handler.postDelayed(pending, ViewConfiguration.getLongPressTimeout());
                return false;
            }
            if (eventAction == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getRawX() - downX);
                float dy = Math.abs(event.getRawY() - downY);
                if (dx > touchSlop || dy > touchSlop) {
                    cancel();
                }
                return fired;
            }
            if (eventAction == MotionEvent.ACTION_UP) {
                if (!fired) {
                    view.performClick();
                }
                cancel();
                return fired;
            }
            if (eventAction == MotionEvent.ACTION_CANCEL) {
                cancel();
                return fired;
            }
            return fired;
        }

        private void cancel() {
            if (pending != null) {
                handler.removeCallbacks(pending);
                pending = null;
            }
        }
    }
}
