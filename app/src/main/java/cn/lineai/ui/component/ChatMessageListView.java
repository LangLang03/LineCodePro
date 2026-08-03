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
    private LinearLayout multiSelectBar;
    private TextView multiSelectCountText;
    private MultiSelectListener multiSelectListener;

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
        boolean show = !multiSelectMode && adapter.getCount() > 0 && !isAtBottom();
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

    private static View createConfigureState(Context context, EmptyStateListener listener) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LineTheme.padding(box, LineTheme.XL, 80, LineTheme.XL, 80);

        TextView prompt = LineTheme.text(context, "›_", LineTheme.FONT_XL, LineTheme.ACCENT, Typeface.NORMAL);
        prompt.setTypeface(Typeface.MONOSPACE);
        box.addView(prompt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView title = LineTheme.text(context, context.getString(R.string.message_list_configure_title), LineTheme.FONT_TITLE, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        box.addView(title, titleParams);

        TextView desc = LineTheme.text(context,
                context.getString(R.string.message_list_configure_desc),
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

        box.addView(actionRow(context, listener, true),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static View createWelcomeState(Context context, EmptyStateListener listener) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LineTheme.padding(box, LineTheme.XL, 80, LineTheme.XL, 80);

        TextView prompt = LineTheme.text(context, "›_", LineTheme.FONT_XL, LineTheme.ACCENT, Typeface.NORMAL);
        prompt.setTypeface(Typeface.MONOSPACE);
        box.addView(prompt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView title = LineTheme.text(context, context.getString(R.string.empty_state_welcome_title), LineTheme.FONT_DISPLAY, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        box.addView(title, titleParams);

        TextView desc = LineTheme.text(context,
                context.getString(R.string.empty_state_welcome_desc),
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

        box.addView(actionRow(context, listener, false),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static LinearLayout actionRow(Context context, EmptyStateListener listener, boolean includeAddModel) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        row.setLayoutParams(rowParams);

        if (includeAddModel) {
            TextView addModel = actionButton(context, context.getString(R.string.empty_state_add_model), true);
            addModel.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddModel();
                }
            });
            row.addView(addModel, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        TextView openWorkspace = actionButton(context, context.getString(R.string.empty_state_open_workspace), false);
        openWorkspace.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpenWorkspace();
            }
        });
        LinearLayout.LayoutParams workspaceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (includeAddModel) {
            workspaceParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        }
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
        private static final int MAX_CACHED_ROWS = 140;
        private static final int VIEW_TYPE_CONFIGURE = 0;
        private static final int VIEW_TYPE_USER = 1;
        private static final int VIEW_TYPE_ASSISTANT = 2;
        private static final int VIEW_TYPE_NOTICE = 3;
        private static final int VIEW_TYPE_WELCOME = 4;

        private final Context context;
        private final ArrayList<ChatMessage> visibleMessages = new ArrayList<>();
        private final LinkedHashMap<String, View> rowCache = new LinkedHashMap<>(32, 0.75f, true);
        private boolean showConfigureState;
        private boolean showWelcomeState;
        private boolean thinkingAutoExpand;
        private boolean thinkingScroll;
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
                        toolResults.put(message.getToolCallId(), ToolResult.withReview(
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
            boolean nextShowWelcomeState = nextMessages.isEmpty() && state != null && state.hasConfiguredModel();
            boolean nextThinkingAutoExpand = state != null && state.isThinkingAutoExpandEnabled();
            boolean nextThinkingScroll = state == null || state.isThinkingScrollEnabled();
            boolean nextCodeWrapEnabled = state != null && state.isCodeWrapEnabled();
            String nextConversationId = state == null ? "" : state.getConversationId();
            String nextProjectPath = state == null ? "" : state.getProjectPath();
            boolean conversationChanged = !stringEquals(conversationId, nextConversationId);

            if (showConfigureState == nextShowConfigureState
                    && showWelcomeState == nextShowWelcomeState
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
            showWelcomeState = nextShowWelcomeState;
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
            if (showConfigureState || showWelcomeState) {
                return 1;
            }
            return visibleMessages.size();
        }

        @Override
        public Object getItem(int position) {
            if (showConfigureState || showWelcomeState) {
                return null;
            }
            return visibleMessages.get(position);
        }

        @Override
        public long getItemId(int position) {
            if (showConfigureState || showWelcomeState) {
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
            return 5;
        }

        @Override
        public int getItemViewType(int position) {
            if (showConfigureState) {
                return VIEW_TYPE_CONFIGURE;
            }
            if (showWelcomeState) {
                return VIEW_TYPE_WELCOME;
            }
            ChatMessage message = visibleMessages.get(position);
            if (message.isModelSwitchNotification()) {
                return VIEW_TYPE_NOTICE;
            }
            return message.getRole() == ChatMessage.Role.USER ? VIEW_TYPE_USER : VIEW_TYPE_ASSISTANT;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (showConfigureState) {
                return convertView == null
                        ? createConfigureState(context, emptyStateListener)
                        : convertView;
            }
            if (showWelcomeState) {
                return convertView == null
                        ? createWelcomeState(context, emptyStateListener)
                        : convertView;
            }
            ChatMessage message = visibleMessages.get(position);

            if (message.isModelSwitchNotification()) {
                if (convertView != null) {
                    return convertView;
                }
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
                        context, LineTheme.ACCENT, 12, LineTheme.ACCENT));
                view.setPadding(LineTheme.dp(context, 4), LineTheme.dp(context, 4),
                        LineTheme.dp(context, 4), LineTheme.dp(context, 4));
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

    public interface MultiSelectListener {
        void onExportRequested(List<ChatMessage> selectedMessages);

        void onMultiSelectExit();
    }
}
