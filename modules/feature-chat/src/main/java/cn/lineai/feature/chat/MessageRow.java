package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/**
 * One message row. Rebinding is idempotent: the caller only rebinds rows whose content signature
 * changed, and typing in a streaming answer only re-renders the markdown tail.
 */
final class MessageRow extends LinearLayout {

    private static final int REASONING_PREVIEW_LINES = 2;

    private final boolean user;
    private final boolean tool;
    private final TextView userText;
    private final LinearLayout userImages;
    private final MarkdownLite markdown;
    private final WorkingStatusView workingStatus;
    private final LinearLayout reasoningBox;
    private final TextView reasoningHeader;
    private final TextView reasoningBody;
    private final ToolRow toolRow;
    private ToolPresentationProvider presentationProvider;
    private boolean reasoningExpanded;
    private boolean reasoningAlwaysExpanded;
    private boolean reasoningAutoExpand;
    private boolean processAutoExpand;
    private String signature = "";
    private String lastText = "";
    private String lastReasoning = "";
    private String lastImageKey = "";

    private MessageRow(Context context, Role role, Tokens tokens) {
        super(context);
        this.user = role == Role.USER;
        this.tool = role == Role.TOOL;
        setOrientation(VERTICAL);
        if (user) {
            setGravity(Gravity.END);
            Ui.padding(this, 16, 16, 16, 32);
            userImages = new LinearLayout(context);
            userImages.setOrientation(HORIZONTAL);
            userImages.setGravity(Gravity.END);
            userImages.setVisibility(GONE);
            addView(userImages, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            userText = new TextView(context);
            userText.setTextSize(16);
            userText.setIncludeFontPadding(false);
            userText.setLineSpacing(Ui.dp(context, 5), 1.0f);
            userText.setBackground(Ui.rounded(context, tokens.userBubble, 18));
            userText.setTextColor(Ui.textOn(tokens.userBubble));
            Ui.padding(userText, 15, 10, 15, 10);
            addView(userText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            markdown = null;
            workingStatus = null;
            reasoningBox = null;
            reasoningHeader = null;
            reasoningBody = null;
            toolRow = null;
        } else if (tool) {
            userText = null;
            userImages = null;
            markdown = null;
            workingStatus = null;
            reasoningBox = null;
            reasoningHeader = null;
            reasoningBody = null;
            toolRow = new ToolRow(context, tokens);
            addView(toolRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            setGravity(Gravity.START);
            Ui.padding(this, 16, 0, 16, 28);
            userText = null;
            userImages = null;
            toolRow = null;

            reasoningBox = new LinearLayout(context);
            reasoningBox.setOrientation(VERTICAL);
            reasoningBox.setBackground(Ui.rounded(context, tokens.surfaceLight, 10));
            Ui.padding(reasoningBox, 10, 8, 10, 8);
            reasoningBox.setVisibility(GONE);
            reasoningBox.setClickable(true);
            reasoningBox.setOnClickListener(v -> toggleReasoning());
            reasoningHeader = Ui.medium(context,
                    context.getString(R.string.message_assistant_thinking),
                    Tokens.FONT_XS, tokens.textTertiary);
            reasoningBox.addView(reasoningHeader,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            reasoningBody = Ui.text(context, "", Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
            reasoningBody.setLineSpacing(Ui.dp(context, 3), 1f);
            reasoningBody.setMaxLines(REASONING_PREVIEW_LINES);
            reasoningBody.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams reasoningParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            reasoningParams.topMargin = Ui.dp(context, 4);
            reasoningBox.addView(reasoningBody, reasoningParams);
            LayoutParams boxParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            boxParams.bottomMargin = Ui.dp(context, 8);
            addView(reasoningBox, boxParams);

            markdown = new MarkdownLite(context, tokens);
            addView(markdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            workingStatus = new WorkingStatusView(context, tokens);
            workingStatus.setVisibility(GONE);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            statusParams.topMargin = Ui.dp(context, 2);
            addView(workingStatus, statusParams);
        }
    }

    static MessageRow create(Context context, Message message, Tokens tokens) {
        return new MessageRow(context, message.role, tokens);
    }

    void presentationProvider(ToolPresentationProvider value) {
        presentationProvider = value;
    }

    void bind(Message message, Tokens tokens) {
        String text = message.plainText();
        String reasoning = message.reasoningText();
        String next = signature(message, text, reasoning);
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        lastText = text;
        lastReasoning = reasoning;
        if (user) {
            renderUserAttachments(message, tokens);
            userText.setText(text);
            userText.setVisibility(text.isEmpty() ? GONE : VISIBLE);
            userText.setMaxWidth(maxWidth());
            return;
        }
        if (tool) {
            toolRow.bind(message, tokens, presentationProvider);
            return;
        }
        boolean streaming = message.isStreaming();
        boolean empty = text.isEmpty() && reasoning.isEmpty();
        setVisibility(empty && !streaming ? GONE : VISIBLE);
        if (!reasoning.equals(reasoningBody.getText().toString())) {
            reasoningExpanded = reasoningAlwaysExpanded || reasoningAutoExpand;
            reasoningBody.setText(reasoning);
        }
        reasoningBox.setVisibility(reasoning.isEmpty() ? GONE : VISIBLE);
        applyReasoningState();
        markdown.render(text, tokens);
        workingStatus.setVisibility(streaming ? VISIBLE : GONE);
        if (streaming) {
            workingStatus.start();
        } else {
            workingStatus.stop();
        }
    }

    void preferences(cn.lineai.core.model.UiPreferences preferences,
                     java.util.function.Consumer<String> links) {
        reasoningAlwaysExpanded = !preferences.thinkingScroll();
        reasoningAutoExpand = preferences.thinkingAutoExpand();
        processAutoExpand = preferences.processAutoExpand();
        if (reasoningAlwaysExpanded) reasoningExpanded = true;
        if (reasoningBody != null) applyReasoningState();
        if (toolRow != null) toolRow.preferences(processAutoExpand);
        if (markdown != null) {
            markdown.setCodeWrapEnabled(preferences.codeWrap());
            markdown.setLinkHandler(links);
        }
    }

    void applyTokens(Tokens tokens) {
        if (user) {
            userText.setTextColor(Ui.textOn(tokens.userBubble));
            userText.setBackground(Ui.rounded(getContext(), tokens.userBubble, 18));
            return;
        }
        if (tool) {
            toolRow.applyTokens(tokens);
            return;
        }
        reasoningBox.setBackground(Ui.rounded(getContext(), tokens.surfaceLight, 10));
        reasoningHeader.setTextColor(tokens.textTertiary);
        reasoningBody.setTextColor(tokens.textTertiary);
        markdown.render(lastText, tokens);
        workingStatus.applyTokens(tokens);
    }

    private void toggleReasoning() {
        if (reasoningBody.getText().length() == 0) {
            return;
        }
        reasoningExpanded = !reasoningExpanded;
        applyReasoningState();
    }

    private void applyReasoningState() {
        reasoningBody.setMaxLines(reasoningExpanded
                ? Integer.MAX_VALUE : REASONING_PREVIEW_LINES);
        reasoningBody.setEllipsize(reasoningExpanded
                ? null : TextUtils.TruncateAt.END);
    }

    private void renderUserAttachments(Message message, Tokens tokens) {
        StringBuilder key = new StringBuilder();
        for (cn.lineai.core.model.ContentBlock block : message.blocks) {
            if (block.type == cn.lineai.core.model.ContentBlock.Type.IMAGE
                    || block.type == cn.lineai.core.model.ContentBlock.Type.FILE) {
                key.append(block.refId).append('\u0001');
            }
        }
        if (key.toString().equals(lastImageKey)) return;
        lastImageKey = key.toString();
        userImages.removeAllViews();
        int count = 0;
        for (cn.lineai.core.model.ContentBlock block : message.blocks) {
            if (block.type == cn.lineai.core.model.ContentBlock.Type.IMAGE) {
                android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(block.refId);
                if (bitmap == null) continue;
                ImageView image = new ImageView(getContext());
                image.setImageBitmap(bitmap);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(Ui.rounded(getContext(), tokens.userBubble, 14));
                image.setClipToOutline(true);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        Ui.dp(getContext(), 150), Ui.dp(getContext(), 116));
                params.leftMargin = Ui.dp(getContext(), 6);
                params.bottomMargin = Ui.dp(getContext(), 6);
                userImages.addView(image, params);
                count++;
            } else if (block.type == cn.lineai.core.model.ContentBlock.Type.FILE) {
                TextView chip = Ui.text(getContext(),
                        new java.io.File(block.refId).getName(), Tokens.FONT_XS,
                        tokens.textSecondary, Typeface.NORMAL);
                chip.setBackground(Ui.rounded(getContext(), tokens.surfaceLight, 10));
                Ui.padding(chip, 10, 6, 10, 6);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.leftMargin = Ui.dp(getContext(), 6);
                params.bottomMargin = Ui.dp(getContext(), 6);
                userImages.addView(chip, params);
                count++;
            }
        }
        userImages.setVisibility(count == 0 ? GONE : VISIBLE);
    }

    private int maxWidth() {
        int available = Math.max(0, getResources().getDisplayMetrics().widthPixels
                - Ui.dp(getContext(), 32));
        return (int) (available * 0.90f);
    }

    private static String signature(Message message, String text, String reasoning) {
        StringBuilder value = new StringBuilder();
        value.append(message.id).append('\u0001').append(text).append('\u0001')
                .append(reasoning).append('\u0001').append(message.status.name());
        for (String path : ImagePaths.of(message)) value.append('\u0001').append(path);
        return value.toString();
    }
}

/** Case-class-free helper so MessageRow does not depend on the protocol module. */
final class ImagePaths {
    static java.util.List<String> of(Message message) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (cn.lineai.core.model.ContentBlock block : message.blocks) {
            if (block.type == cn.lineai.core.model.ContentBlock.Type.IMAGE
                    && !block.refId.isEmpty()) {
                result.add(block.refId);
            }
        }
        return result;
    }
}
