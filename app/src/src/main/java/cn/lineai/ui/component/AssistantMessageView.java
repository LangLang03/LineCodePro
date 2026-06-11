package cn.lineai.ui.component;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import cn.lineai.model.ChatMessage;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.toolcall.ToolCallBlockView;
import cn.lineai.ui.component.toolcall.ToolReviewListener;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.markdown.MarkdownLinkHandler;
import cn.lineai.ui.markdown.MarkdownView;
import java.util.ArrayList;

public final class AssistantMessageView extends LinearLayout {
    private final ContextCompactBlockView compactBlockView;
    private final ThinkingBlockView thinkingBlockView;
    private final MarkdownView contentView;
    private final LinearLayout toolCallsContainer;
    private final MessageActionBarView actionBar;
    private String lastMessageId = "";
    private String lastReasoning = "";
    private String lastContent = "";
    private boolean lastStreaming;
    private boolean lastThinkingAutoExpand;
    private boolean lastThinkingScrollable = true;
    private String lastCompactStatus = "";
    private String lastToolSignature = "";
    private String projectPath = "";
    private MarkdownLinkHandler markdownLinkHandler;
    private ToolReviewListener toolReviewListener;
    private MessageActionListener actionListener;
    private ChatMessage currentMessage;

    public AssistantMessageView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.START);
        LineTheme.padding(this, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);

        compactBlockView = new ContextCompactBlockView(context);
        LinearLayout.LayoutParams compactParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        compactParams.topMargin = LineTheme.dp(context, 2);
        compactParams.bottomMargin = LineTheme.dp(context, 2);
        addView(compactBlockView, compactParams);

        thinkingBlockView = new ThinkingBlockView(context);
        LinearLayout.LayoutParams thinkingParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        thinkingParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(thinkingBlockView, thinkingParams);

        contentView = new MarkdownView(context);
        addView(contentView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        toolCallsContainer = new LinearLayout(context);
        toolCallsContainer.setOrientation(VERTICAL);
        LinearLayout.LayoutParams toolParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        addView(toolCallsContainer, toolParams);

        actionBar = new MessageActionBarView(context, MessageActionBarView.ALIGN_LEFT, false);
        actionBar.setListener(new MessageActionBarView.Listener() {
            @Override
            public void onCopy() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onCopyMessage(currentMessage);
                }
            }

            @Override
            public void onRecall() {
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 22));
        actionParams.topMargin = LineTheme.dp(context, 3);
        addView(actionBar, actionParams);
    }

    public void bind(ChatMessage message) {
        bind(message, false, true, false);
    }

    public void bind(ChatMessage message, boolean thinkingAutoExpand, boolean thinkingScrollable) {
        bind(message, thinkingAutoExpand, thinkingScrollable, false);
    }

    public void bind(ChatMessage message, boolean thinkingAutoExpand, boolean thinkingScrollable, boolean codeWrapEnabled) {
        currentMessage = message;
        String messageId = message.getId() == null ? "" : message.getId();
        String reasoning = message.getReasoningContent();
        String safeReasoning = reasoning == null ? "" : reasoning;
        boolean hasReasoning = safeReasoning.trim().length() > 0;
        if (message.isCompactBlock()) {
            compactBlockView.setVisibility(VISIBLE);
            compactBlockView.bind(message.getCompactStatus());
            thinkingBlockView.setVisibility(GONE);
            contentView.setVisibility(GONE);
            toolCallsContainer.setVisibility(GONE);
            toolCallsContainer.removeAllViews();
            actionBar.setVisibility(GONE);
            lastMessageId = messageId;
            lastReasoning = safeReasoning;
            lastContent = "";
            lastStreaming = message.isStreaming();
            lastCompactStatus = message.getCompactStatus();
            lastToolSignature = "";
            return;
        }
        compactBlockView.setVisibility(GONE);
        String content = message.isStreaming() && message.getContent().length() == 0 && !hasReasoning ? "..." : message.getContent();
        contentView.setCodeWrapEnabled(codeWrapEnabled);
        contentView.setLinkHandler(markdownLinkHandler);

        if (hasReasoning) {
            thinkingBlockView.setVisibility(VISIBLE);
            if (!lastMessageId.equals(messageId)
                    || !lastReasoning.equals(safeReasoning)
                    || lastStreaming != message.isStreaming()
                    || lastThinkingAutoExpand != thinkingAutoExpand
                    || lastThinkingScrollable != thinkingScrollable) {
                thinkingBlockView.bind(messageId, safeReasoning, message.isStreaming(), thinkingAutoExpand, thinkingScrollable);
            }
        } else {
            thinkingBlockView.setVisibility(GONE);
        }
        if (content.trim().length() == 0 && message.hasToolCalls()) {
            contentView.setVisibility(GONE);
        } else {
            contentView.setVisibility(VISIBLE);
        }
        if (!lastContent.equals(content)) {
            contentView.setMarkdown(content);
        }
        bindToolCalls(message);
        actionBar.setVisibility(message.isStreaming() || message.getContent().trim().isEmpty() ? GONE : VISIBLE);
        lastMessageId = messageId;
        lastReasoning = safeReasoning;
        lastContent = content;
        lastStreaming = message.isStreaming();
        lastThinkingAutoExpand = thinkingAutoExpand;
        lastThinkingScrollable = thinkingScrollable;
        lastCompactStatus = "";
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
        for (int i = 0; i < toolCallsContainer.getChildCount(); i++) {
            if (toolCallsContainer.getChildAt(i) instanceof ToolCallBlockView) {
                ((ToolCallBlockView) toolCallsContainer.getChildAt(i)).setToolReviewListener(listener);
            }
        }
    }

    public void setMessageActionListener(MessageActionListener listener) {
        actionListener = listener;
    }

    public void setMarkdownLinkHandler(MarkdownLinkHandler handler) {
        markdownLinkHandler = handler;
        contentView.setLinkHandler(handler);
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
        for (int i = 0; i < toolCallsContainer.getChildCount(); i++) {
            if (toolCallsContainer.getChildAt(i) instanceof ToolCallBlockView) {
                ((ToolCallBlockView) toolCallsContainer.getChildAt(i)).setProjectPath(this.projectPath);
            }
        }
    }

    private void bindToolCalls(ChatMessage message) {
        String signature = toolSignature(message);
        if (signature.equals(lastToolSignature)) {
            return;
        }
        lastToolSignature = signature;
        if (!message.hasToolCalls()) {
            toolCallsContainer.setVisibility(GONE);
            toolCallsContainer.removeAllViews();
            return;
        }
        ArrayList<ToolCall> visibleToolCalls = visibleToolCalls(message);
        if (visibleToolCalls.isEmpty()) {
            toolCallsContainer.setVisibility(GONE);
            toolCallsContainer.removeAllViews();
            return;
        }
        toolCallsContainer.setVisibility(VISIBLE);
        int targetCount = visibleToolCalls.size();
        while (toolCallsContainer.getChildCount() > targetCount) {
            toolCallsContainer.removeViewAt(toolCallsContainer.getChildCount() - 1);
        }
        while (toolCallsContainer.getChildCount() < targetCount) {
            ToolCallBlockView block = new ToolCallBlockView(getContext());
            block.setToolReviewListener(toolReviewListener);
            block.setProjectPath(projectPath);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
            toolCallsContainer.addView(block, params);
        }
        for (int i = 0; i < targetCount; i++) {
            ToolCall call = visibleToolCalls.get(i);
            ToolResult result = message.getToolResult(call.getId());
            ToolCallBlockView block = (ToolCallBlockView) toolCallsContainer.getChildAt(i);
            block.setToolReviewListener(toolReviewListener);
            block.setProjectPath(projectPath);
            block.bind(call, result);
        }
    }

    private ArrayList<ToolCall> visibleToolCalls(ChatMessage message) {
        ArrayList<ToolCall> visible = new ArrayList<>();
        if (message == null) {
            return visible;
        }
        for (ToolCall call : message.getToolCalls()) {
            ToolResult result = message.getToolResult(call.getId());
            if (isHiddenSuccessfulImageGeneration(call, result)) {
                continue;
            }
            visible.add(call);
        }
        return visible;
    }

    private boolean isHiddenSuccessfulImageGeneration(ToolCall call, ToolResult result) {
        return call != null
                && "image_generation".equals(call.getName())
                && result != null
                && !result.isError()
                && result.getContent().trim().length() > 0
                && result.getReviewState().length() == 0;
    }

    private String toolSignature(ChatMessage message) {
        if (message == null || !message.hasToolCalls()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(projectPath).append('\n');
        for (ToolCall call : message.getToolCalls()) {
            builder.append(call.getId()).append('|')
                    .append(call.getName()).append('|')
                    .append(call.getArguments()).append('|');
            ToolResult result = message.getToolResult(call.getId());
            if (result != null) {
                builder.append(result.getContent()).append('|')
                        .append(result.isError()).append('|')
                        .append(result.getDiffId()).append('|')
                        .append(result.getReviewState()).append('|')
                        .append(result.getReviewMessage());
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}
