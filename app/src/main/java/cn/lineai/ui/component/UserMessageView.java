package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;

public final class UserMessageView extends LinearLayout {
    private static final long ENTRANCE_FADE_MS = 220L;

    private final TextView contentText;
    private final LinearLayout attachmentList;
    private final MessageActionBarView actionBar;
    private final int defaultPaddingLeft;
    private final int defaultPaddingTop;
    private final int defaultPaddingRight;
    private final int defaultPaddingBottom;
    private String lastContent = "";
    private String lastAnimatedMessageId = "";
    private ChatMessage currentMessage;
    private MessageActionListener actionListener;

    public UserMessageView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        LineTheme.padding(this, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        defaultPaddingLeft = getPaddingLeft();
        defaultPaddingTop = getPaddingTop();
        defaultPaddingRight = getPaddingRight();
        defaultPaddingBottom = getPaddingBottom();

        contentText = LineTheme.text(context, "", 16, LineTheme.TEXT_ON_COLOR, Typeface.NORMAL);
        contentText.setLineSpacing(LineTheme.dp(context, 2), 1.0f);
        contentText.setBackground(LineTheme.userBubble(context));
        LineTheme.padding(contentText, LineTheme.MD, 5, LineTheme.MD, 5);
        int horizontalPaddingPx = LineTheme.dp(context, LineTheme.LG) * 2;
        int availableWidth = context.getResources().getDisplayMetrics().widthPixels - horizontalPaddingPx;
        contentText.setMaxWidth((int) (availableWidth * 0.80f));
        addView(contentText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        attachmentList = new LinearLayout(context);
        attachmentList.setOrientation(VERTICAL);
        attachmentList.setGravity(Gravity.END);
        LinearLayout.LayoutParams attachmentParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        attachmentParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        addView(attachmentList, attachmentParams);

        actionBar = new MessageActionBarView(context, MessageActionBarView.ALIGN_RIGHT, true);
        actionBar.setActionListener(new MessageActionBarView.ActionListener() {
            @Override
            public void onCopy() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onCopyMessage(currentMessage);
                }
            }

            @Override
            public void onQuote() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onQuoteMessage(currentMessage);
                }
            }

            @Override
            public void onShare() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onShareMessage(currentMessage);
                }
            }
        });
        actionBar.setSelectListener(new MessageActionBarView.SelectListener() {
            @Override
            public void onSelect() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onSelectText(currentMessage);
                }
            }

            @Override
            public void onMultiSelect() {
                if (actionListener != null) {
                    actionListener.onMultiSelectToggle();
                }
            }
        });
        actionBar.setRecallListener(new MessageActionBarView.RecallListener() {
            @Override
            public void onRecall() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onRecallMessage(currentMessage);
                }
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 22));
        actionParams.topMargin = LineTheme.dp(context, 3);
        addView(actionBar, actionParams);
    }

    public void setMessageActionListener(MessageActionListener listener) {
        actionListener = listener;
    }

    public void restoreDefaultPadding() {
        setPadding(defaultPaddingLeft, defaultPaddingTop, defaultPaddingRight, defaultPaddingBottom);
    }

    public void bind(ChatMessage message) {
        currentMessage = message;
        String messageId = message.getId() == null ? "" : message.getId();
        if (!lastAnimatedMessageId.equals(messageId)) {
            lastAnimatedMessageId = messageId;
            setAlpha(0f);
            animate().alpha(1f).setDuration(ENTRANCE_FADE_MS).start();
        }
        String content = visibleUserContent(message);
        if (!lastContent.equals(content)) {
            contentText.setText(content);
            lastContent = content;
        }
        contentText.setVisibility(content.length() == 0 ? GONE : VISIBLE);
        renderAttachments(message);
    }

    private String visibleUserContent(ChatMessage message) {
        if (message == null) {
            return "";
        }
        String content = message.getContent();
        if (content.length() == 0 && message.hasAttachments()) {
            return "";
        }
        if (getContext().getString(R.string.message_user_attached_files).equals(content.trim()) && message.hasAttachments()) {
            return "";
        }
        return content;
    }

    private void renderAttachments(ChatMessage message) {
        attachmentList.removeAllViews();
        if (message == null || !message.hasAttachments()) {
            attachmentList.setVisibility(GONE);
            return;
        }
        attachmentList.setVisibility(VISIBLE);
        for (InputAttachment attachment : message.getAttachments()) {
            attachmentList.addView(attachmentChip(attachment));
        }
    }

    private TextView attachmentChip(InputAttachment attachment) {
        TextView chip = LineTheme.textMedium(getContext(), attachment.getName(), LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        chip.setMaxWidth(LineTheme.dp(getContext(), 220));
        chip.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(chip, LineTheme.SM, 4, LineTheme.SM, 4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
        chip.setLayoutParams(params);
        return chip;
    }
}
