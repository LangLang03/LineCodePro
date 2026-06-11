package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import cn.lineai.ui.theme.LineTheme;

public final class UserMessageView extends LinearLayout {
    private final TextView contentText;
    private final LinearLayout attachmentList;
    private final MessageActionBarView actionBar;
    private String lastContent = "";
    private ChatMessage currentMessage;
    private MessageActionListener actionListener;

    public UserMessageView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        LineTheme.padding(this, LineTheme.LG, 0, LineTheme.LG, 6);

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
        actionBar.setListener(new MessageActionBarView.Listener() {
            @Override
            public void onCopy() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onCopyMessage(currentMessage);
                }
            }

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

    public void bind(ChatMessage message) {
        currentMessage = message;
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
        if ("已附加文件".equals(content.trim()) && message.hasAttachments()) {
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
