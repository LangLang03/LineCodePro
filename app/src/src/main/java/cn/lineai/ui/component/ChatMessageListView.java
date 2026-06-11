package cn.lineai.ui.component;

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
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.InputAttachment;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.toolcall.ToolReviewListener;
import cn.lineai.ui.markdown.MarkdownLinkHandler;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatMessageListView extends FrameLayout {
    private final ListView listView;
    private final MessageAdapter adapter;
    private final IconButtonView scrollToBottomButton;
    private boolean followTailEnabled;
    private ToolReviewListener toolReviewListener;
    private MarkdownLinkHandler markdownLinkHandler;
    private MessageActionListener messageActionListener;

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
        scrollToBottomButton.setContentDescription("滚动到底部");
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
            listView.post(() -> scrollToBottomInternal(false));
        } else {
            listView.post(this::updateScrollToBottomVisibility);
        }
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

    private void scrollToBottom() {
        followTailEnabled = true;
        scrollToBottomInternal(true);
    }

    private void scrollToBottomInternal(boolean animated) {
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

    private void updateScrollToBottomVisibility() {
        boolean show = adapter.getCount() > 0 && !isAtBottom();
        scrollToBottomButton.setVisibility(show ? VISIBLE : GONE);
        if (show) {
            scrollToBottomButton.bringToFront();
        }
    }

    private void refreshScrollToBottomButtonStyle() {
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

    private static View createConfigureState(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LineTheme.padding(box, LineTheme.XL, 80, LineTheme.XL, 80);

        TextView title = LineTheme.text(context, "请先配置模型", LineTheme.FONT_XL, LineTheme.TEXT, Typeface.BOLD);
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView desc = LineTheme.text(context,
                "进入 设置 → 模型管理 → 添加模型，保存后再发送消息。",
                LineTheme.FONT_MD,
                LineTheme.TEXT_SECONDARY,
                Typeface.NORMAL);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        box.addView(desc, descParams);
        return box;
    }

    private static View createModelSwitchNotice(Context context, String noticeText) {
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
        private static final int MAX_CACHED_ROWS = 140;
        private static final int VIEW_TYPE_CONFIGURE = 0;
        private static final int VIEW_TYPE_USER = 1;
        private static final int VIEW_TYPE_ASSISTANT = 2;
        private static final int VIEW_TYPE_MODEL_SWITCH = 3;

        private final Context context;
        private final ArrayList<ChatMessage> visibleMessages = new ArrayList<>();
        private final LinkedHashMap<String, View> rowCache = new LinkedHashMap<>(32, 0.75f, true);
        private boolean showConfigureState;
        private boolean thinkingAutoExpand;
        private boolean thinkingScroll;
        private boolean codeWrapEnabled;
        private String conversationId = "";
        private String projectPath = "";
        private ToolReviewListener toolReviewListener;
        private MarkdownLinkHandler markdownLinkHandler;
        private MessageActionListener messageActionListener;

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
                        toolResults.put(message.getToolCallId(), new ToolResult(
                                message.getToolCallId(),
                                message.getToolName(),
                                message.getContent(),
                                message.isError(),
                                message.getDiffId(),
                                message.getReviewState(),
                                message.getReviewMessage()
                        ));
                    }
                }
                for (ChatMessage message : messages) {
                    if (message.isHidden()
                            || message.getRole() == ChatMessage.Role.SYSTEM
                            || message.getRole() == ChatMessage.Role.TOOL) {
                        continue;
                    }
                    nextMessages.add(message.hasToolCalls() ? message.withToolResults(toolResultsFor(message, toolResults)) : message);
                }
            }
            boolean nextShowConfigureState = nextMessages.isEmpty() && state != null && !state.hasConfiguredModel();
            boolean nextThinkingAutoExpand = state != null && state.isThinkingAutoExpandEnabled();
            boolean nextThinkingScroll = state == null || state.isThinkingScrollEnabled();
            boolean nextCodeWrapEnabled = state != null && state.isCodeWrapEnabled();
            String nextConversationId = state == null ? "" : state.getConversationId();
            String nextProjectPath = state == null ? "" : state.getProjectPath();
            boolean conversationChanged = !stringEquals(conversationId, nextConversationId);

            if (showConfigureState == nextShowConfigureState
                    && thinkingAutoExpand == nextThinkingAutoExpand
                    && thinkingScroll == nextThinkingScroll
                    && codeWrapEnabled == nextCodeWrapEnabled
                    && stringEquals(conversationId, nextConversationId)
                    && stringEquals(projectPath, nextProjectPath)
                    && sameMessages(nextMessages)) {
                return false;
            }

            if (conversationChanged) {
                rowCache.clear();
            }
            visibleMessages.clear();
            visibleMessages.addAll(nextMessages);
            showConfigureState = nextShowConfigureState;
            thinkingAutoExpand = nextThinkingAutoExpand;
            thinkingScroll = nextThinkingScroll;
            codeWrapEnabled = nextCodeWrapEnabled;
            conversationId = nextConversationId;
            projectPath = nextProjectPath;
            pruneCache();
            notifyDataSetChanged();
            return conversationChanged;
        }

        @Override
        public int getCount() {
            return showConfigureState ? 1 : visibleMessages.size();
        }

        @Override
        public Object getItem(int position) {
            return showConfigureState ? null : visibleMessages.get(position);
        }

        @Override
        public long getItemId(int position) {
            if (showConfigureState) {
                return -1L;
            }
            String id = visibleMessages.get(position).getId();
            return id == null ? position : id.hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int position) {
            if (showConfigureState) {
                return VIEW_TYPE_CONFIGURE;
            }
            ChatMessage message = visibleMessages.get(position);
            if (message.isModelSwitchNotification()) {
                return VIEW_TYPE_MODEL_SWITCH;
            }
            return message.getRole() == ChatMessage.Role.USER ? VIEW_TYPE_USER : VIEW_TYPE_ASSISTANT;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (showConfigureState) {
                return convertView == null ? createConfigureState(context) : convertView;
            }
            ChatMessage message = visibleMessages.get(position);

            if (message.isModelSwitchNotification()) {
                String ck = cacheKey(message);
                View cached = rowCache.get(ck);
                if (cached != null && canReturnCachedView(cached, convertView, parent)) {
                    return cached;
                }
                View notice = createModelSwitchNotice(context, message.getModelSwitchNotification());
                rowCache.put(ck, notice);
                trimCache();
                return notice;
            }

            String cacheKey = cacheKey(message);
            View cached = rowCache.get(cacheKey);
            if (cached != null && canReturnCachedView(cached, convertView, parent)) {
                bindCachedView(cached, message);
                return cached;
            }

            if (message.getRole() == ChatMessage.Role.USER) {
                UserMessageView view = cached instanceof UserMessageView ? (UserMessageView) cached : new UserMessageView(context);
                view.setMessageActionListener(messageActionListener);
                view.bind(message);
                rowCache.put(cacheKey, view);
                trimCache();
                return view;
            }
            AssistantMessageView view = cached instanceof AssistantMessageView ? (AssistantMessageView) cached : new AssistantMessageView(context);
            view.setToolReviewListener(toolReviewListener);
            view.setMarkdownLinkHandler(markdownLinkHandler);
            view.setMessageActionListener(messageActionListener);
            view.setProjectPath(projectPath);
            view.bind(message, thinkingAutoExpand, thinkingScroll, codeWrapEnabled);
            rowCache.put(cacheKey, view);
            trimCache();
            return view;
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

        private boolean canReturnCachedView(View cached, View convertView, android.view.ViewGroup parent) {
            if (cached.getParent() == null || cached == convertView) {
                return true;
            }
            return cached.getParent() == parent && cached == convertView;
        }

        private void bindCachedView(View cached, ChatMessage message) {
            if (cached instanceof UserMessageView) {
                ((UserMessageView) cached).setMessageActionListener(messageActionListener);
                ((UserMessageView) cached).bind(message);
                return;
            }
            if (cached instanceof AssistantMessageView) {
                ((AssistantMessageView) cached).setToolReviewListener(toolReviewListener);
                ((AssistantMessageView) cached).setMarkdownLinkHandler(markdownLinkHandler);
                ((AssistantMessageView) cached).setMessageActionListener(messageActionListener);
                ((AssistantMessageView) cached).setProjectPath(projectPath);
                ((AssistantMessageView) cached).bind(message, thinkingAutoExpand, thinkingScroll, codeWrapEnabled);
            }
        }

        private String cacheKey(ChatMessage message) {
            return conversationId + ":" + message.getRole().name() + ":" + (message.getId() == null ? "" : message.getId());
        }

        private void pruneCache() {
            Set<String> currentKeys = new HashSet<>();
            for (ChatMessage message : visibleMessages) {
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
                    && a.isHidden() == b.isHidden()
                    && stringEquals(a.getCompactStatus(), b.getCompactStatus())
                    && stringEquals(a.getModelSwitchNotification(), b.getModelSwitchNotification())
                    && sameAttachments(a, b)
                    && sameToolCalls(a, b)
                    && sameToolResults(a, b));
        }

        private boolean stringEquals(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }

        private ArrayList<ToolResult> toolResultsFor(ChatMessage message, Map<String, ToolResult> resultById) {
            ArrayList<ToolResult> results = new ArrayList<>();
            for (ToolCall call : message.getToolCalls()) {
                ToolResult result = resultById.get(call.getId());
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
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
}
