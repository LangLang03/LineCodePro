package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ChatMessage;
import cn.lineai.ui.model.ConversationTimeline;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.InputAttachment;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.ui.markdown.MarkdownLinkHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatMessageListView extends FrameLayout {
    private static final int MULTI_SELECT_BAR_EXTRA_PADDING = 64;

    private final ListView listView;
    private final MessageAdapter adapter;
    private final IconButtonView scrollToBottomButton;
    private boolean followTailEnabled;
    private ToolReviewListener toolReviewListener;
    private MarkdownLinkHandler markdownLinkHandler;
    private MessageActionListener messageActionListener;
    private boolean multiSelectMode = false;
    private final Set<String> selectedMessageIds = new HashSet<>();
    private int scrollButtonStyleKey;
    private LinearLayout multiSelectBar;
    private TextView multiSelectCountText;
    private MultiSelectListener multiSelectListener;
    /** 上一次已应用到 FAB 的状态，避免滚动每一帧重复 setVisibility/bringToFront。 */
    private boolean scrollToBottomShown;
    private boolean scrollToBottomPostPending;

    public interface EmptyStateListener {
        void onAddModel();

        void onOpenWorkspace();
    }

    public ChatMessageListView(Context context) {
        super(context);
        setBackgroundColor(LineTheme.BG);
        setClipToPadding(false);

        adapter = new MessageAdapter(context);
        listView = new TouchAwareListView(context);
        listView.setAdapter(adapter);
        listView.setBackgroundColor(LineTheme.BG);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setFadingEdgeLength(0);
        listView.setFastScrollEnabled(false);
        listView.setFocusable(false);
        listView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        listView.setPadding(0, LineTheme.dp(context, LineTheme.SM), 0, LineTheme.dp(context, LineTheme.SM));
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        listView.setSmoothScrollbarEnabled(true);
        listView.setStackFromBottom(false);
        listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        addView(listView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        scrollToBottomButton = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        scrollToBottomButton.setContentDescription(context.getString(R.string.message_list_scroll_to_bottom_desc));
        refreshScrollToBottomButtonStyle();
        scrollToBottomButton.setVisibility(GONE);
        scrollToBottomButton.setOnClickListener(v -> scrollToBottom());
        scrollToBottomButton.setElevation(LineTheme.dp(context, 8));
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                LineTheme.dp(context, 44),
                LineTheme.dp(context, 44),
                Gravity.END | Gravity.BOTTOM
        );
        buttonParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        buttonParams.bottomMargin = LineTheme.dp(context, LineTheme.LG);
        addView(scrollToBottomButton, buttonParams);
        addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                post(this::updateScrollToBottomVisibility));
        buildMultiSelectBar();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (!multiSelectMode) {
                return;
            }
            Object item = adapter.getItem(position);
            if (item instanceof ChatMessage) {
                toggleSelection(((ChatMessage) item).getId());
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
                        || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    followTailEnabled = false;
                }
                updateScrollToBottomVisibility();
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                updateScrollToBottomVisibility();
            }
        });
    }

    public void render(ChatUiState state) {
        refreshScrollToBottomButtonStyle();
        boolean conversationChanged = adapter.render(state);
        if (conversationChanged) {
            followTailEnabled = true;
        }
        if (followTailEnabled && adapter.getCount() > 0) {
            postScrollToBottom();
        } else if (!scrollToBottomPostPending) {
            scrollToBottomPostPending = true;
            listView.post(this::applyScrollToBottomVisibility);
        }
    }

    private void postScrollToBottom() {
        if (scrollToBottomPostPending) {
            return; // 同一帧内多次 render 只需要一滚到底
        }
        scrollToBottomPostPending = true;
        listView.post(() -> scrollToBottomInternal(false));
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
        adapter.setToolReviewListener(listener);
    }

    public void setMarkdownLinkHandler(MarkdownLinkHandler handler) {
        markdownLinkHandler = handler;
        adapter.setMarkdownLinkHandler(handler);
    }

    public void setMessageActionListener(MessageActionListener listener) {
        messageActionListener = listener;
        adapter.setMessageActionListener(listener);
    }

    public void setMultiSelectListener(MultiSelectListener listener) {
        multiSelectListener = listener;
    }

    public void setEmptyStateListener(EmptyStateListener listener) {
        adapter.emptyStateListener = listener;
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public boolean isSelected(String messageId) {
        return messageId != null && selectedMessageIds.contains(messageId);
    }

    public void toggleSelection(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return;
        }
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId);
        } else {
            selectedMessageIds.add(messageId);
        }
        adapter.multiSelectMode = true;
        adapter.selectedMessageIds = selectedMessageIds;
        updateMultiSelectCount();
        adapter.notifyDataSetChanged();
    }

    public void enterMultiSelectMode() {
        multiSelectMode = true;
        selectedMessageIds.clear();
        adapter.multiSelectMode = true;
        adapter.selectedMessageIds = selectedMessageIds;
        if (multiSelectBar != null) {
            multiSelectBar.setVisibility(VISIBLE);
        }
        listView.setPadding(0, LineTheme.dp(getContext(), LineTheme.SM), 0,
                LineTheme.dp(getContext(), LineTheme.SM) + MULTI_SELECT_BAR_EXTRA_PADDING);
        updateMultiSelectCount();
        updateScrollToBottomVisibility();
        invalidate();
        requestLayout();
        adapter.notifyDataSetChanged();
    }

    public void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedMessageIds.clear();
        adapter.multiSelectMode = false;
        adapter.selectedMessageIds = java.util.Collections.emptySet();
        if (multiSelectBar != null) {
            multiSelectBar.setVisibility(GONE);
        }
        listView.setPadding(0, LineTheme.dp(getContext(), LineTheme.SM), 0,
                LineTheme.dp(getContext(), LineTheme.SM));
        updateMultiSelectCount();
        updateScrollToBottomVisibility();
        invalidate();
        requestLayout();
        adapter.notifyDataSetChanged();
    }

    public List<ChatMessage> getSelectedMessages() {
        List<ChatMessage> result = new ArrayList<>();
        if (selectedMessageIds.isEmpty()) {
            return result;
        }
        for (ChatMessage message : adapter.getVisibleMessages()) {
            String id = message.getId();
            if (id != null && selectedMessageIds.contains(id)) {
                result.add(message);
            }
        }
        return result;
    }

    private void buildMultiSelectBar() {
        Context context = getContext();
        multiSelectBar = new LinearLayout(context);
        multiSelectBar.setOrientation(LinearLayout.HORIZONTAL);
        multiSelectBar.setGravity(Gravity.CENTER_VERTICAL);
        multiSelectBar.setBackgroundColor(LineTheme.SURFACE_ELEVATED);
        LineTheme.padding(multiSelectBar, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.SM);
        multiSelectBar.setElevation(LineTheme.dp(context, 8));
        multiSelectBar.setVisibility(GONE);

        multiSelectCountText = LineTheme.text(context,
                context.getString(R.string.screen_models_selected_count, 0),
                LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        multiSelectBar.addView(multiSelectCountText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        IconButtonView exportButton = new IconButtonView(context, IconButtonView.DOWNLOAD);
        exportButton.setContentDescription(context.getString(R.string.export_button_label));
        exportButton.setIconColor(LineTheme.TEXT_ON_COLOR);
        exportButton.setIconSizeDp(44, 20);
        exportButton.setBackground(LineTheme.roundedStroke(context, LineTheme.ACCENT, 22, LineTheme.ACCENT));
        exportButton.setOnClickListener(v -> {
            if (multiSelectListener != null) {
                multiSelectListener.onExportRequested(getSelectedMessages());
            }
        });
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(
                LineTheme.dp(context, 44), LineTheme.dp(context, 44));
        exportParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        multiSelectBar.addView(exportButton, exportParams);

        IconButtonView closeButton = new IconButtonView(context, IconButtonView.CLOSE);
        closeButton.setContentDescription(context.getString(R.string.common_close));
        closeButton.setIconColor(LineTheme.TEXT_SECONDARY);
        closeButton.setIconSizeDp(44, 20);
        closeButton.setOnClickListener(v -> {
            exitMultiSelectMode();
            if (multiSelectListener != null) {
                multiSelectListener.onMultiSelectExit();
            }
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LineTheme.dp(context, 44), LineTheme.dp(context, 44));
        closeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        multiSelectBar.addView(closeButton, closeParams);

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        addView(multiSelectBar, barParams);
    }

    private void updateMultiSelectCount() {
        if (multiSelectCountText == null) {
            return;
        }
        int count = selectedMessageIds.size();
        multiSelectCountText.setText(getContext().getString(R.string.screen_models_selected_count, count));
    }

    private void scrollToBottom() {
        followTailEnabled = true;
        scrollToBottomInternal(true);
    }

    private void scrollToBottomInternal(boolean animated) {
        scrollToBottomPostPending = false;
        int count = adapter.getCount();
        if (count <= 0) {
            updateScrollToBottomVisibility();
            return;
        }
        int target = count - 1;
        listView.setSelection(target);
        listView.post(() -> {
            int childIndex = target - listView.getFirstVisiblePosition();
            if (childIndex >= 0 && childIndex < listView.getChildCount()) {
                View child = listView.getChildAt(childIndex);
                int viewportBottom = listView.getHeight() - listView.getPaddingBottom();
                int delta = child.getBottom() - viewportBottom;
                if (delta > 0) {
                    if (animated) {
                        listView.smoothScrollBy(delta, 180);
                    } else {
                        listView.setSelectionFromTop(target, viewportBottom - child.getHeight());
                    }
                }
            }
            updateScrollToBottomVisibility();
        });
    }

    private void applyScrollToBottomVisibility() {
        scrollToBottomPostPending = false;
        updateScrollToBottomVisibility();
    }

    private void updateScrollToBottomVisibility() {
        boolean show = !multiSelectMode && adapter.getCount() > 0 && !isAtBottom();
        if (show == scrollToBottomShown) {
            return;
        }
        scrollToBottomShown = show;
        scrollToBottomButton.setVisibility(show ? VISIBLE : GONE);
        if (show) {
            scrollToBottomButton.bringToFront();
        }
    }

    private void refreshScrollToBottomButtonStyle() {
        int styleKey = 31 * LineTheme.ACCENT + LineTheme.TEXT_ON_COLOR;
        if (styleKey == scrollButtonStyleKey) {
            return;
        }
        scrollButtonStyleKey = styleKey;
        scrollToBottomButton.setIconColor(LineTheme.TEXT_ON_COLOR);
        scrollToBottomButton.setIconSizeDp(44, 20);
        scrollToBottomButton.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.ACCENT, 22, LineTheme.ACCENT));
    }

    private boolean isAtBottom() {
        int count = adapter.getCount();
        if (count == 0) {
            return true;
        }
        if (listView.getLastVisiblePosition() < count - 1) {
            return false;
        }
        int childCount = listView.getChildCount();
        if (childCount == 0) {
            return true;
        }
        View lastChild = listView.getChildAt(childCount - 1);
        int viewportBottom = listView.getHeight() - listView.getPaddingBottom();
        return lastChild.getBottom() <= viewportBottom + LineTheme.dp(getContext(), 2);
    }

    private static View createConfigureState(Context context, EmptyStateListener listener, boolean configure) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        LineTheme.padding(box, 28, 96, 28, 64);
        TextView title = LineTheme.text(context, context.getString(R.string.chat_empty_title), 28, LineTheme.TEXT, Typeface.NORMAL);
        box.addView(title);
        TextView desc = LineTheme.text(context, context.getString(configure
                ? R.string.message_list_configure_desc : R.string.chat_empty_message), 15, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        desc.setLineSpacing(LineTheme.dp(context, 6), 1);
        LinearLayout.LayoutParams description = new LinearLayout.LayoutParams(-1, -2);
        description.topMargin = LineTheme.dp(context, 20);
        box.addView(desc, description);
        if (configure) {
            TextView addModel = actionButton(context, context.getString(R.string.empty_state_add_model), true);
            addModel.setMinHeight(LineTheme.dp(context, 48));
            addModel.setOnClickListener(v -> { if (listener != null) listener.onAddModel(); });
            LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-2, -2);
            button.topMargin = LineTheme.dp(context, 28); box.addView(addModel, button);
        }
        return box;
    }

    private static LinearLayout actionRow(Context context, EmptyStateListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        row.setLayoutParams(rowParams);

        TextView addModel = actionButton(context, context.getString(R.string.empty_state_add_model), true);
        addModel.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddModel();
            }
        });
        row.addView(addModel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView openWorkspace = actionButton(context, context.getString(R.string.empty_state_open_workspace), false);
        openWorkspace.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpenWorkspace();
            }
        });
        LinearLayout.LayoutParams workspaceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        workspaceParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(openWorkspace, workspaceParams);
        return row;
    }

    private static TextView actionButton(Context context, String label, boolean primary) {
        TextView button = LineTheme.text(context, label, LineTheme.FONT_MD,
                primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT, Typeface.NORMAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(primary
                ? LineTheme.rounded(context, LineTheme.ACCENT, 22)
                : LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 22, LineTheme.BORDER_LIGHT));
        button.setPadding(LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.SM));
        return button;
    }

    private static View createNoticeView(Context context, String noticeText) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LineTheme.padding(row, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.SM);
        TextView label = LineTheme.text(context, noticeText, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private static final class MessageAdapter extends BaseAdapter {
        /** 缓存的是已解析完 Markdown 的整行视图，单行可达数十个 View；上限过高会直接制造 GC/卡顿。 */
        private static final int MAX_CACHED_ROWS = 64;
        private static final int VIEW_TYPE_CONFIGURE = 0;
        private static final int VIEW_TYPE_USER = 1;
        private static final int VIEW_TYPE_ASSISTANT = 2;
        private static final int VIEW_TYPE_NOTICE = 3;

        private final Context context;
        private final ArrayList<ChatMessage> visibleMessages = new ArrayList<>();
        private final LinkedHashMap<String, View> rowCache = new LinkedHashMap<>(32, 0.75f, true);
        private final ConversationTimeline.Builder timelineBuilder = new ConversationTimeline.Builder();
        private final HashMap<String, ToolResultCache> toolResultCache = new HashMap<>();
        private final HashMap<String, MergedMessageCache> mergedMessageCache = new HashMap<>();
        private boolean showConfigureState;
        private boolean generating;
        private List<ConversationTimeline.Row> timeline = java.util.Collections.emptyList();
        private final Map<String, Boolean> disclosure = new HashMap<>();
        private boolean thinkingAutoExpand;
        private boolean thinkingScroll;
        private boolean processAutoExpand;
        private boolean codeWrapEnabled;
        private boolean multiSelectMode;
        private Set<String> selectedMessageIds = java.util.Collections.emptySet();
        private String conversationId = "";
        private String projectPath = "";
        private ToolReviewListener toolReviewListener;
        private MarkdownLinkHandler markdownLinkHandler;
        private MessageActionListener messageActionListener;
        private EmptyStateListener emptyStateListener;

        MessageAdapter(Context context) {
            this.context = context;
        }

        boolean render(ChatUiState state) {
            ArrayList<ChatMessage> nextMessages = new ArrayList<>();
            if (state != null) {
                List<ChatMessage> messages = state.getMessages();
                HashMap<String, ToolResult> toolResults = new HashMap<>();
                for (ChatMessage message : messages) {
                    if (message.getRole() == ChatMessage.Role.TOOL && message.getToolCallId().length() > 0) {
                        toolResults.put(message.getToolCallId(), toolResultOf(message));
                    }
                }
                for (ChatMessage message : messages) {
                    if (message.isHidden()
                            || message.getRole() == ChatMessage.Role.SYSTEM
                            || message.getRole() == ChatMessage.Role.TOOL) {
                        continue;
                    }
                    nextMessages.add(message.hasToolCalls() ? mergeToolResults(message, toolResults) : message);
                }
            }
            boolean nextShowConfigureState = nextMessages.isEmpty() && state != null && !state.hasConfiguredModel();
            boolean nextThinkingAutoExpand = state != null && state.isThinkingAutoExpandEnabled();
            boolean nextThinkingScroll = state == null || state.isThinkingScrollEnabled();
            boolean nextProcessAutoExpand = state != null && state.isProcessAutoExpandEnabled();
            boolean nextCodeWrapEnabled = state != null && state.isCodeWrapEnabled();
            String nextConversationId = state == null ? "" : state.getConversationId();
            String nextProjectPath = state == null ? "" : state.getProjectPath();
            boolean conversationChanged = !stringEquals(conversationId, nextConversationId);

            if (generating == (state != null && state.isStreaming())
                    && showConfigureState == nextShowConfigureState
                    && thinkingAutoExpand == nextThinkingAutoExpand
                    && thinkingScroll == nextThinkingScroll
                    && processAutoExpand == nextProcessAutoExpand
                    && codeWrapEnabled == nextCodeWrapEnabled
                    && stringEquals(conversationId, nextConversationId)
                    && stringEquals(projectPath, nextProjectPath)
                    && sameMessages(nextMessages)) {
                return false;
            }

            boolean shrunk = nextMessages.size() < visibleMessages.size();
            if (conversationChanged) {
                rowCache.clear();
                disclosure.clear();
                timelineBuilder.reset();
                toolResultCache.clear();
                mergedMessageCache.clear();
            } else if (shrunk) {
                // 压缩/清空后一次性丢弃失效缓存，避免每次 render 重建 key 集合。
                toolResultCache.clear();
                mergedMessageCache.clear();
                pruneCache(nextMessages);
            }
            visibleMessages.clear();
            visibleMessages.addAll(nextMessages);
            timeline = timelineBuilder.build(nextMessages);
            generating = state != null && state.isStreaming();
            showConfigureState = nextShowConfigureState;
            thinkingAutoExpand = nextThinkingAutoExpand;
            thinkingScroll = nextThinkingScroll;
            processAutoExpand = nextProcessAutoExpand;
            codeWrapEnabled = nextCodeWrapEnabled;
            conversationId = nextConversationId;
            projectPath = nextProjectPath;
            if (!conversationChanged && !shrunk) {
                trimCache();
            }
            notifyDataSetChanged();
            return conversationChanged;
        }

        /** 同一 TOOL 消息实例只构造一次 ToolResult，保证上游消息/视图的引用稳定性。 */
        private ToolResult toolResultOf(ChatMessage message) {
            String key = cacheKey(message);
            ToolResultCache cached = toolResultCache.get(key);
            if (cached != null && cached.source == message) {
                return cached.result;
            }
            ToolResult result = ToolResult.withReview(
                    message.getToolCallId(),
                    message.getToolName(),
                    message.getContent(),
                    message.isError(),
                    message.getDiffId(),
                    message.getReviewState(),
                    message.getReviewMessage()
            );
            toolResultCache.put(key, new ToolResultCache(message, result));
            return result;
        }

        /**
         * 把工具结果合入助手消息。旧实现对每条带工具调用的消息都复制一个新 {@link ChatMessage}，
         * 导致流式时整条对话的消息实例全部失效，下游（时间线/token 估算/视图绑定）无法复用。
         */
        private ChatMessage mergeToolResults(ChatMessage message, Map<String, ToolResult> resultById) {
            ArrayList<ToolResult> results = new ArrayList<>();
            for (ToolCall call : message.getToolCalls()) {
                ToolResult result = resultById.get(call.getId());
                if (result != null) {
                    results.add(result);
                }
            }
            String key = cacheKey(message);
            MergedMessageCache cached = mergedMessageCache.get(key);
            if (cached != null && cached.source == message && sameResults(cached.results, results)) {
                return cached.merged;
            }
            ChatMessage merged = resultsEqual(message.getToolResults(), results) ? message : message.withToolResults(results);
            mergedMessageCache.put(key, new MergedMessageCache(message, results, merged));
            return merged;
        }

        private static boolean resultsEqual(List<ToolResult> existing, List<ToolResult> next) {
            if (existing == next) {
                return true;
            }
            if (existing.size() != next.size()) {
                return false;
            }
            for (int i = 0; i < next.size(); i++) {
                ToolResult left = existing.get(i);
                ToolResult right = next.get(i);
                if (left == right) {
                    continue;
                }
                if (left == null || right == null
                        || !left.getToolCallId().equals(right.getToolCallId())
                        || !left.getToolName().equals(right.getToolName())
                        || !left.getContent().equals(right.getContent())
                        || !left.getDiffId().equals(right.getDiffId())
                        || !left.getReviewState().equals(right.getReviewState())
                        || !left.getReviewMessage().equals(right.getReviewMessage())
                        || left.isError() != right.isError()) {
                    return false;
                }
            }
            return true;
        }

        private boolean sameResults(List<ToolResult> left, List<ToolResult> right) {
            if (left == right) {
                return true;
            }
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (left.get(i) != right.get(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getCount() {
            if (showConfigureState) {
                return 1;
            }
            return visibleMessages.isEmpty() ? 1 : multiSelectMode ? visibleMessages.size() : timeline.size();
        }

        @Override
        public Object getItem(int position) {
            if (visibleMessages.isEmpty()) {
                return null;
            }
            return messageAt(position);
        }

        @Override
        public long getItemId(int position) {
            if (visibleMessages.isEmpty()) {
                return -1L;
            }
            String id = messageAt(position).getId();
            return id == null ? position : id.hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        private ChatMessage messageAt(int position) {
            return multiSelectMode ? visibleMessages.get(position) : timeline.get(position).first;
        }

        @Override
        public int getItemViewType(int position) {
            if (visibleMessages.isEmpty()) {
                return VIEW_TYPE_CONFIGURE;
            }
            ChatMessage message = messageAt(position);
            if (!multiSelectMode && timeline.get(position).isTurn) return 4;
            if (message.isModelSwitchNotification()) {
                return VIEW_TYPE_NOTICE;
            }
            return message.getRole() == ChatMessage.Role.USER ? VIEW_TYPE_USER : VIEW_TYPE_ASSISTANT;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (visibleMessages.isEmpty()) {
                return createConfigureState(context, emptyStateListener, showConfigureState);
            }
            ChatMessage message = messageAt(position);

            if (!multiSelectMode && timeline.get(position).isTurn) {
                String key = cacheKey(message);
                AssistantTurnView view = convertView instanceof AssistantTurnView
                        ? (AssistantTurnView) convertView
                        : obtain(AssistantTurnView.class, key, new AssistantTurnView(context));
                view.bind(timeline.get(position), disclosure, projectPath, toolReviewListener,
                        markdownLinkHandler, messageActionListener, codeWrapEnabled,
                        generating && position == timeline.size() - 1, processAutoExpand);
                return view;
            }
            if (message.isModelSwitchNotification()) {
                String ck = cacheKey(message);
                View cached = rowCache.get(ck);
                if (cached != null && cached.getParent() == null) {
                    return cached;
                }
                View notice = createNoticeView(context, message.getModelSwitchNotification());
                putCache(ck, notice);
                return notice;
            }

            String cacheKey = cacheKey(message);
            boolean isUser = message.getRole() == ChatMessage.Role.USER;

            if (convertView != null) {
                if (isUser && convertView instanceof UserMessageView) {
                    UserMessageView view = (UserMessageView) convertView;
                    view.setMessageActionListener(messageActionListener);
                    view.bind(message);
                    applyMultiSelectStyle(view, message);
                    return view;
                }
                if (!isUser && convertView instanceof AssistantMessageView) {
                    AssistantMessageView view = (AssistantMessageView) convertView;
                    view.setToolReviewListener(toolReviewListener);
                    view.setMarkdownLinkHandler(markdownLinkHandler);
                    view.setMessageActionListener(messageActionListener);
                    view.setProjectPath(projectPath);
                    view.bind(message, thinkingAutoExpand, thinkingScroll, codeWrapEnabled);
                    applyMultiSelectStyle(view, message);
                    return view;
                }
            }

            if (isUser) {
                UserMessageView view = obtain(UserMessageView.class, cacheKey, new UserMessageView(context));
                view.setMessageActionListener(messageActionListener);
                view.bind(message);
                applyMultiSelectStyle(view, message);
                return view;
            }
            AssistantMessageView view = obtain(AssistantMessageView.class, cacheKey, new AssistantMessageView(context));
            view.setToolReviewListener(toolReviewListener);
            view.setMarkdownLinkHandler(markdownLinkHandler);
            view.setMessageActionListener(messageActionListener);
            view.setProjectPath(projectPath);
            view.bind(message, thinkingAutoExpand, thinkingScroll, codeWrapEnabled);
            applyMultiSelectStyle(view, message);
            return view;
        }

        private void applyMultiSelectStyle(View view, ChatMessage message) {
            if (message == null || message.getId() == null) {
                view.setBackground(null);
                restoreMessagePadding(view);
                return;
            }
            if (multiSelectMode && selectedMessageIds.contains(message.getId())) {
                view.setBackground(LineTheme.roundedStroke(
                        context, LineTheme.ACCENT_MUTED, 12, LineTheme.BORDER_LIGHT));
                restoreMessagePadding(view);
            } else {
                view.setBackground(null);
                restoreMessagePadding(view);
            }
        }

        private void restoreMessagePadding(View view) {
            if (view instanceof UserMessageView) {
                ((UserMessageView) view).restoreDefaultPadding();
            } else if (view instanceof AssistantMessageView) {
                ((AssistantMessageView) view).restoreDefaultPadding();
            } else {
                view.setPadding(0, 0, 0, 0);
            }
        }

        void setToolReviewListener(ToolReviewListener listener) {
            toolReviewListener = listener;
            for (View view : rowCache.values()) {
                if (view instanceof AssistantMessageView) {
                    ((AssistantMessageView) view).setToolReviewListener(listener);
                }
            }
        }

        void setMarkdownLinkHandler(MarkdownLinkHandler handler) {
            markdownLinkHandler = handler;
            for (View view : rowCache.values()) {
                if (view instanceof AssistantMessageView) {
                    ((AssistantMessageView) view).setMarkdownLinkHandler(handler);
                }
            }
        }

        void setMessageActionListener(MessageActionListener listener) {
            messageActionListener = listener;
            for (View view : rowCache.values()) {
                if (view instanceof UserMessageView) {
                    ((UserMessageView) view).setMessageActionListener(listener);
                } else if (view instanceof AssistantMessageView) {
                    ((AssistantMessageView) view).setMessageActionListener(listener);
                }
            }
        }

        List<ChatMessage> getVisibleMessages() {
            return new ArrayList<>(visibleMessages);
        }

        private String cacheKey(ChatMessage message) {
            return conversationId + ":" + message.getRole().name() + ":" + (message.getId() == null ? "" : message.getId());
        }

        private void pruneCache(List<ChatMessage> nextMessages) {
            Set<String> currentKeys = new HashSet<>();
            for (ChatMessage message : nextMessages) {
                currentKeys.add(cacheKey(message));
            }
            Iterator<Map.Entry<String, View>> iterator = rowCache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, View> entry = iterator.next();
                if (!currentKeys.contains(entry.getKey()) && entry.getValue().getParent() == null) {
                    iterator.remove();
                }
            }
            trimCache();
        }

        private void trimCache() {
            if (rowCache.size() <= MAX_CACHED_ROWS) {
                return;
            }
            Iterator<Map.Entry<String, View>> iterator = rowCache.entrySet().iterator();
            while (rowCache.size() > MAX_CACHED_ROWS && iterator.hasNext()) {
                Map.Entry<String, View> entry = iterator.next();
                if (entry.getValue().getParent() == null) {
                    iterator.remove();
                }
            }
        }

        private <T extends View> T obtain(Class<T> type, String key, T created) {
            View cached = rowCache.get(key);
            if (type.isInstance(cached) && cached.getParent() == null) {
                return type.cast(cached);
            }
            putCache(key, created);
            return created;
        }

        private void putCache(String key, View view) {
            View old = rowCache.put(key, view);
            if (old != null && old != view && old.getParent() == null) {
                old.setTag(null);
            }
            trimCache();
        }

        private boolean sameMessages(ArrayList<ChatMessage> nextMessages) {
            if (visibleMessages.size() != nextMessages.size()) {
                return false;
            }
            for (int i = 0; i < nextMessages.size(); i++) {
                if (!sameMessage(visibleMessages.get(i), nextMessages.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean sameMessage(ChatMessage a, ChatMessage b) {
            return a == b || (a != null
                    && b != null
                    && stringEquals(a.getId(), b.getId())
                    && a.getRole() == b.getRole()
                    && stringEquals(a.getContent(), b.getContent())
                    && stringEquals(a.getReasoningContent(), b.getReasoningContent())
                    && a.isStreaming() == b.isStreaming()
                    && a.isError() == b.isError()
                    && a.isHidden() == b.isHidden()
                    && stringEquals(a.getCompactStatus(), b.getCompactStatus())
                    && stringEquals(a.getModelSwitchNotification(), b.getModelSwitchNotification())
                    && a.getProcessingStartedAt() == b.getProcessingStartedAt()
                    && a.getProcessingFinishedAt() == b.getProcessingFinishedAt()
                    && sameAttachments(a, b)
                    && sameToolCalls(a, b)
                    && sameToolResults(a, b));
        }

        private boolean stringEquals(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }

        private static final class ToolResultCache {
            final ChatMessage source;
            final ToolResult result;

            ToolResultCache(ChatMessage source, ToolResult result) {
                this.source = source;
                this.result = result;
            }
        }

        private static final class MergedMessageCache {
            final ChatMessage source;
            final List<ToolResult> results;
            final ChatMessage merged;

            MergedMessageCache(ChatMessage source, List<ToolResult> results, ChatMessage merged) {
                this.source = source;
                this.results = results;
                this.merged = merged;
            }
        }

        private boolean sameToolCalls(ChatMessage a, ChatMessage b) {
            if (a.getToolCalls().size() != b.getToolCalls().size()) {
                return false;
            }
            for (int i = 0; i < a.getToolCalls().size(); i++) {
                ToolCall left = a.getToolCalls().get(i);
                ToolCall right = b.getToolCalls().get(i);
                if (!stringEquals(left.getId(), right.getId())
                        || !stringEquals(left.getName(), right.getName())
                        || !stringEquals(left.getArguments(), right.getArguments())) {
                    return false;
                }
            }
            return true;
        }

        private boolean sameToolResults(ChatMessage a, ChatMessage b) {
            if (a.getToolResults().size() != b.getToolResults().size()) {
                return false;
            }
            for (int i = 0; i < a.getToolResults().size(); i++) {
                ToolResult left = a.getToolResults().get(i);
                ToolResult right = b.getToolResults().get(i);
                if (!stringEquals(left.getToolCallId(), right.getToolCallId())
                        || !stringEquals(left.getToolName(), right.getToolName())
                        || !stringEquals(left.getContent(), right.getContent())
                        || !stringEquals(left.getDiffId(), right.getDiffId())
                        || !stringEquals(left.getReviewState(), right.getReviewState())
                        || !stringEquals(left.getReviewMessage(), right.getReviewMessage())
                        || left.isError() != right.isError()) {
                    return false;
                }
            }
            return true;
        }

        private boolean sameAttachments(ChatMessage a, ChatMessage b) {
            if (a.getAttachments().size() != b.getAttachments().size()) {
                return false;
            }
            for (int i = 0; i < a.getAttachments().size(); i++) {
                InputAttachment left = a.getAttachments().get(i);
                InputAttachment right = b.getAttachments().get(i);
                if (!stringEquals(left.getName(), right.getName())
                        || !stringEquals(left.getPath(), right.getPath())
                        || !stringEquals(left.getSource(), right.getSource())) {
                    return false;
                }
            }
            return true;
        }
    }

    private final class TouchAwareListView extends ListView {
        TouchAwareListView(Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (disallowIntercept) {
                followTailEnabled = false;
            }
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                followTailEnabled = false;
            } else if (action == MotionEvent.ACTION_UP) {
                performClick();
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    public interface MultiSelectListener {
        void onExportRequested(List<ChatMessage> selectedMessages);

        void onMultiSelectExit();
    }
}
