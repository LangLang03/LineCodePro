package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.model.Message;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * One timeline row: a user bubble or a folded assistant turn.
 *
 * <p>Rebinding is idempotent and never recreates children, so markdown scroll position, expanded
 * thinking and review blocks survive every streaming update and theme switch.</p>
 */
final class MessageRow extends LinearLayout {

    private final boolean user;
    private final TextView userText;
    private final LinearLayout attachments;
    private final MessageActionBar actions;
    private final AssistantTurnView turn;
    private Tokens tokens;
    private RowEnv env;
    private Message message;
    private String lastText = "";
    private String lastAttachmentKey = "";
    private String lastSignature = "";
    private RowEnv lastEnv;
    private boolean actionsVisible;
    private boolean selected;

    private MessageRow(Context context, Tokens tokens, boolean user) {
        super(context);
        this.tokens = tokens;
        this.user = user;
        setOrientation(VERTICAL);
        if (user) {
            setGravity(Gravity.END);
            Ui.padding(this, 16, 16, 16, 32);
            userText = new TextView(context);
            userText.setTextSize(16);
            userText.setIncludeFontPadding(false);
            userText.setLineSpacing(Ui.dp(context, 5), 1.0f);
            userText.setBackground(Ui.rounded(context, tokens.userBubble, 18));
            userText.setTextColor(Ui.textOn(tokens.userBubble));
            Ui.padding(userText, 15, 10, 15, 10);
            userText.setVisibility(GONE);
            addView(userText, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            attachments = new LinearLayout(context);
            attachments.setOrientation(HORIZONTAL);
            attachments.setGravity(Gravity.END);
            attachments.setVisibility(GONE);
            LayoutParams attachmentParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);
            attachmentParams.topMargin = Ui.dp(context, 4);
            addView(attachments, attachmentParams);
            actions = new MessageActionBar(context, tokens, true);
            actions.setOnDismiss(() -> actionsVisible = false);
            actions.setVisibility(GONE);
            LayoutParams actionParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);
            actionParams.topMargin = Ui.dp(context, 3);
            addView(actions, actionParams);
            turn = null;
        } else {
            setGravity(Gravity.START);
            userText = null;
            attachments = null;
            actions = null;
            turn = new AssistantTurnView(context, tokens);
            addView(turn, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
        }
        setOnClickListener(v -> {
            if (env == null || env.selection == null || message == null) {
                return;
            }
            if (env.selection.multiSelect()) {
                env.selection.toggleSelection(message.id, message);
            } else {
                // A tap anywhere on a row closes an open action bar, like C++ row clicks.
                env.selection.dismissActions();
            }
        });
        setOnLongClickListener(v -> {
            if (env == null || message == null) {
                return false;
            }
            if (env.selection != null && env.selection.multiSelect()) {
                env.selection.toggleSelection(message.id, message);
                return true;
            }
            if (!user || message.isStreaming()) {
                return false;
            }
            boolean wasOpen = actionsVisible;
            if (env.selection != null) {
                env.selection.dismissActions();
            }
            if (!wasOpen) {
                toggleActions();
            }
            return true;
        });
    }

    static MessageRow create(Context context, TurnAssembler.Row row, Tokens tokens) {
        return new MessageRow(context, tokens, row.user);
    }

    void bind(TurnAssembler.Row value, Tokens current, RowEnv rowEnv) {
        boolean envChanged = lastEnv == null || lastEnv.provider != rowEnv.provider
                || !lastEnv.preferences.equals(rowEnv.preferences)
                || lastEnv.actions != rowEnv.actions || lastEnv.links != rowEnv.links;
        if (!envChanged && value.signature.equals(lastSignature)) {
            return;
        }
        lastEnv = rowEnv;
        lastSignature = value.signature;
        tokens = current;
        env = rowEnv;
        message = value.subject;
        boolean multiSelect = rowEnv.selection != null && rowEnv.selection.multiSelect();
        selected = multiSelect && message != null && rowEnv.selection.isSelected(message.id);
        applySelection();
        if (!value.user) {
            turn.bind(value, current, rowEnv);
            applyTokens(current);
            return;
        }
        Message user = value.userMessage;
        String text = user == null ? "" : user.plainText();
        if (!text.equals(lastText)) {
            lastText = text;
            userText.setText(text);
            actionsVisible = false;
            actions.setVisibility(GONE);
        }
        userText.setVisibility(text.isEmpty() ? GONE : VISIBLE);
        renderAttachments(user == null ? null : user);
        actions.bind(user);
        actions.setActions(rowEnv.actions, user);
        actions.setVisibility(actionsVisible ? VISIBLE : GONE);
        applyTokens(current);
    }

    void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        if (user) {
            userText.setTextColor(Ui.textOn(current.userBubble));
            userText.setBackground(Ui.rounded(getContext(), current.userBubble, 18));
            userText.setHighlightColor(current.accentMuted);
            restyleAttachments(current);
            actions.applyTokens(current);
        } else {
            turn.applyTokens(current);
        }
        applySelection();
    }

    void setRowSelected(boolean value) {
        selected = value;
        applySelection();
    }

    void setMultiSelect(boolean enabled) {
        if (!enabled) {
            selected = false;
        }
        applySelection();
    }

    /** Hides an open action bar; the turn row owns its own bar next to the final answer. */
    void hideActions() {
        actionsVisible = false;
        if (user) {
            actions.setVisibility(GONE);
        } else {
            turn.hideActions();
        }
    }

    private void restyleAttachments(Tokens current) {
        for (int index = 0; index < attachments.getChildCount(); index++) {
            android.view.View child = attachments.getChildAt(index);
            if (child instanceof TextView) {
                TextView chip = (TextView) child;
                chip.setTextColor(current.textSecondary);
                chip.setBackground(Ui.roundedStroke(getContext(), current.surfaceLight, 14,
                        current.borderLight));
            } else if (child instanceof ImageView) {
                child.setBackground(Ui.rounded(getContext(), current.userBubble, 14));
            }
        }
    }

    private void toggleActions() {
        actionsVisible = !actionsVisible;
        actions.setVisibility(actionsVisible ? VISIBLE : GONE);
    }

    private void applySelection() {
        if (selected) {
            setBackground(Ui.roundedStroke(getContext(), tokens.accentMuted, 12,
                    tokens.borderLight));
        } else {
            setBackgroundColor(tokens.bg);
        }
    }

    private void renderAttachments(Message user) {
        StringBuilder key = new StringBuilder();
        if (user != null) {
            for (cn.lineai.core.model.ContentBlock block : user.blocks) {
                if (block.type == cn.lineai.core.model.ContentBlock.Type.IMAGE
                        || block.type == cn.lineai.core.model.ContentBlock.Type.FILE) {
                    key.append(block.refId).append('\u0001');
                }
            }
        }
        if (key.toString().equals(lastAttachmentKey)) {
            return;
        }
        lastAttachmentKey = key.toString();
        attachments.removeAllViews();
        int count = 0;
        if (user != null) {
            for (cn.lineai.core.model.ContentBlock block : user.blocks) {
                if (block.type == cn.lineai.core.model.ContentBlock.Type.IMAGE) {
                    count += addImage(block.refId);
                } else if (block.type == cn.lineai.core.model.ContentBlock.Type.FILE) {
                    count += addFileChip(block.refId);
                }
            }
        }
        attachments.setVisibility(count == 0 ? GONE : VISIBLE);
    }

    private int addImage(String path) {
        android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            return 0;
        }
        ImageView image = new ImageView(getContext());
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(Ui.rounded(getContext(), tokens.userBubble, 14));
        image.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(getContext(), 150), Ui.dp(getContext(), 116));
        params.leftMargin = Ui.dp(getContext(), 6);
        params.bottomMargin = Ui.dp(getContext(), 6);
        attachments.addView(image, params);
        return 1;
    }

    private int addFileChip(String path) {
        TextView chip = Ui.medium(getContext(), new java.io.File(path).getName(),
                Tokens.FONT_XS, tokens.textSecondary);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        chip.setMaxWidth(Ui.dp(getContext(), 220));
        chip.setBackground(Ui.roundedStroke(getContext(), tokens.surfaceLight, 14,
                tokens.borderLight));
        Ui.padding(chip, Tokens.SPACE_SM, 4, Tokens.SPACE_SM, 4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = Ui.dp(getContext(), 6);
        params.bottomMargin = Ui.dp(getContext(), 6);
        attachments.addView(chip, params);
        return 1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (user) {
            int available = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                    - getPaddingLeft() - getPaddingRight());
            userText.setMaxWidth((int) (available * 0.90f));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
