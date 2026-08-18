package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMode;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.InputSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.mvp.QuoteController;
import cn.lineai.ui.util.SlashCommandCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComposerView extends LinearLayout implements QuoteController.QuotePreview {
    public interface Listener {
        void onSend(String text, List<InputAttachment> attachments);

        void onSendWithImage(String text, List<InputAttachment> attachments,
                             String imageBase64, String imageMimeType, String imageName);

        void onAttachClick();

        void onImagePickerClick();

        void onModeChanged(String mode);

        void onStop();

        void onModelQuickSwitch(String modelId);

        void onModelManageClick();

        void onAiReasoningEffortChanged(String effort);

        int onQueryModelCount(String baseUrl) throws Exception;
    }

    /**
     * 关闭引用预览时的回调，便于 QuoteController 清理自身状态。
     */
    public interface QuoteDismissListener {
        void onQuoteDismissed();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearLayout quotePreviewLayout;
    private TextView quotePreviewText;
    private IconButtonView quoteCloseButton;
    private QuoteDismissListener quoteDismissListener;
    private LinearLayout modelSelectorButton;
    private TextView modelText;
    private IconButtonView modelChevron;
    private TextView contextText;
    private LinearLayout modeSelectorButton;
    private TextView modeSelectorText;
    private IconButtonView modeSelectorChevron;
    private HorizontalScrollView attachmentScroll;
    private LinearLayout attachmentList;
    private IconButtonView attachButton;
    private IconButtonView imageButton;
    private LinearLayout imagePreviewLayout;
    private ImageView imagePreviewView;
    private IconButtonView imagePreviewClose;
    private EditText input;
    private IconButtonView sendButton;
    private final ArrayList<InputAttachment> attachments = new ArrayList<>();
    private Uri pendingImageUri;
    private String pendingImageBase64 = "";
    private String pendingImageMimeType = "";
    private String pendingImageName = "";
    private PopupWindow modePopup;
    private PopupWindow modelPopup;
    private SlashCommandPopup slashPopup;
    private String lastSlashSignature = null;
    private boolean streaming;
    private String chatMode = ChatMode.DEFAULT;
    private String enterKeyBehavior = InputSettings.ENTER_SEND;
    private String selectedModelId = "";
    private List<ModelConfig> availableModels = Collections.emptyList();
    private Listener listener;
    private String quoteText = null;
    private LinearLayout quoteBlock;
    private LinearLayout pendingContainer; // 垂直堆叠容器
    private final List<QueuedItem> pendingQueue = new ArrayList<>();
    private boolean wasStreaming = false;

    private static final class QueuedItem {
        final String text;
        final List<InputAttachment> attachments;
        QueuedItem(String text, List<InputAttachment> attachments) {
            this.text = text;
            this.attachments = attachments;
        }
    }

    public ComposerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setWillNotDraw(false);
        LineTheme.padding(this, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.LG);

        buildQuotePreview();

        attachmentScroll = new HorizontalScrollView(context);
        attachmentScroll.setHorizontalScrollBarEnabled(false);
        attachmentScroll.setVisibility(GONE);
        attachmentList = new LinearLayout(context);
        attachmentList.setOrientation(HORIZONTAL);
        attachmentScroll.addView(attachmentList, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams attachmentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        attachmentParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(attachmentScroll, attachmentParams);

        buildImagePreview();

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setMinimumHeight(LineTheme.dp(context, 148));
        panel.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 22, LineTheme.BORDER));
        addView(panel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(metaRow, LineTheme.LG, 0, LineTheme.LG, 0);
        panel.addView(metaRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34)));

        modelSelectorButton = new LinearLayout(context);
        modelSelectorButton.setOrientation(HORIZONTAL);
        modelSelectorButton.setGravity(Gravity.CENTER_VERTICAL);
        modelSelectorButton.setClickable(true);
        modelSelectorButton.setFocusable(true);
        modelSelectorButton.setOnClickListener(v -> showModelPopup(modelSelectorButton));
        modelSelectorButton.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 14));
        LineTheme.padding(modelSelectorButton, LineTheme.SM, 0, LineTheme.SM, 0);

        modelText = LineTheme.textMedium(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY);
        modelText.setSingleLine(true);
        modelText.setMaxWidth(LineTheme.dp(context, 180));
        modelText.setEllipsize(TextUtils.TruncateAt.END);
        modelSelectorButton.addView(modelText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        modelChevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        modelChevron.setIconColor(LineTheme.TEXT_SECONDARY);
        modelChevron.setIconSizeDp(16, 12);
        modelChevron.setClickable(false);
        LinearLayout.LayoutParams modelChevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16));
        modelChevronParams.leftMargin = LineTheme.dp(context, 2);
        modelSelectorButton.addView(modelChevron, modelChevronParams);

        metaRow.addView(modelSelectorButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        android.view.View metaSpacer = new android.view.View(context);
        metaRow.addView(metaSpacer, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        contextText = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        metaRow.addView(contextText, contextParams);

        android.view.View divider = new android.view.View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        // Quote block (hidden by default)
        quoteBlock = new LinearLayout(context);
        quoteBlock.setOrientation(HORIZONTAL);
        quoteBlock.setGravity(Gravity.CENTER_VERTICAL);
        quoteBlock.setBackgroundColor(0xFF1E1E2E);
        LineTheme.padding(quoteBlock, LineTheme.MD, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        quoteBlock.setVisibility(GONE);
        android.view.View quoteBar = new android.view.View(context);
        quoteBar.setBackgroundColor(LineTheme.ACCENT);
        quoteBlock.addView(quoteBar, new LinearLayout.LayoutParams(LineTheme.dp(context, 3), LayoutParams.MATCH_PARENT));
        TextView quotePreview = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.ITALIC);
        quotePreview.setSingleLine(true);
        quotePreview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams qtp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        qtp.leftMargin = LineTheme.dp(context, LineTheme.SM);
        quoteBlock.addView(quotePreview, qtp);
        IconButtonView quoteClose = new IconButtonView(context, IconButtonView.CLOSE);
        quoteClose.setIconColor(LineTheme.TEXT_TERTIARY);
        quoteClose.setIconSizeDp(24, 14);
        quoteClose.setOnClickListener(v -> clearQuote());
        quoteBlock.addView(quoteClose, new LinearLayout.LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 24)));
        panel.addView(quoteBlock, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Pending queue container (vertical stack, each queued message is a row)
        pendingContainer = new LinearLayout(context);
        pendingContainer.setOrientation(VERTICAL);
        pendingContainer.setVisibility(GONE);
        panel.addView(pendingContainer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(HORIZONTAL);
        inputRow.setGravity(Gravity.TOP);
        LineTheme.padding(inputRow, LineTheme.SM, LineTheme.SM, LineTheme.SM, 0);
        panel.addView(inputRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        attachButton = new IconButtonView(context, IconButtonView.PLUS);
        attachButton.setIconColor(LineTheme.TEXT_SECONDARY);
        attachButton.setIconSizeDp(40, 22);
        attachButton.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 20));
        attachButton.setOnClickListener(v -> {
            if (!streaming && listener != null) {
                listener.onAttachClick();
            }
        });

        imageButton = new IconButtonView(context, IconButtonView.IMAGE);
        imageButton.setIconColor(LineTheme.TEXT_SECONDARY);
        imageButton.setIconSizeDp(40, 22);
        imageButton.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 20));
        imageButton.setContentDescription(context.getString(R.string.composer_image_button_desc));
        imageButton.setOnClickListener(v -> {
            if (!streaming && listener != null) {
                listener.onImagePickerClick();
            }
        });

        input = new EditText(context);
        input.setTextColor(LineTheme.TEXT);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setHint(context.getString(R.string.composer_hint_default));
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(6);
        input.setMinHeight(LineTheme.dp(context, 68));
        input.setMaxHeight(LineTheme.dp(context, 152));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setIncludeFontPadding(false);
        input.setPadding(LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (!InputSettings.ENTER_SEND.equals(enterKeyBehavior)) {
                return false;
            }
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                return submitCurrentInput();
            }
            if (isPlainEnterDown(event)) {
                return submitCurrentInput();
            }
            return false;
        });
        input.setOnKeyListener((view, keyCode, event) -> {
            if (!InputSettings.ENTER_SEND.equals(enterKeyBehavior)) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER && isPlainEnterDown(event)) {
                return submitCurrentInput();
            }
            return false;
        });
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                if (modePopup != null && modePopup.isShowing()) {
                    modePopup.dismiss();
                }
                if (modelPopup != null && modelPopup.isShowing()) {
                    modelPopup.dismiss();
                }
            } else {
                dismissSlashPopup();
            }
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(input, inputParams);

        sendButton = new IconButtonView(context, IconButtonView.ARROW_UP);
        sendButton.setIconSizeDp(40, 22);
        sendButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (streaming) {
                if (canSend()) {
                    // 输入框有内容：追加排队（不打断AI）
                    queueCurrentInput();
                } else {
                    // 输入框为空：停止AI（render()检测到停止后自动发送队列）
                    if (listener != null) listener.onStop();
                }
                return;
            }
            submitCurrentInput();
        });

        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setOrientation(HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(modeRow, LineTheme.SM, 0, LineTheme.SM, LineTheme.SM);
        panel.addView(modeRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams attachParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        attachParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(attachButton, attachParams);

        LinearLayout.LayoutParams imageButtonParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        imageButtonParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(imageButton, imageButtonParams);

        modeSelectorButton = new LinearLayout(context);
        modeSelectorButton.setOrientation(HORIZONTAL);
        modeSelectorButton.setGravity(Gravity.CENTER_VERTICAL);
        modeSelectorButton.setClickable(true);
        modeSelectorButton.setFocusable(true);
        modeSelectorButton.setOnClickListener(v -> showModePopup(modeSelectorButton));
        LineTheme.padding(modeSelectorButton, LineTheme.SM, 0, LineTheme.XS, 0);
        modeSelectorText = LineTheme.textMedium(context, modeLabel(chatMode), LineTheme.FONT_XS, LineTheme.TEXT);
        modeSelectorText.setGravity(Gravity.CENTER_VERTICAL);
        modeSelectorText.setSingleLine(true);
        modeSelectorButton.addView(modeSelectorText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        modeSelectorChevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        modeSelectorChevron.setIconColor(LineTheme.TEXT_SECONDARY);
        modeSelectorChevron.setIconSizeDp(18, 13);
        modeSelectorChevron.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        chevronParams.leftMargin = LineTheme.dp(context, 1);
        modeSelectorButton.addView(modeSelectorChevron, chevronParams);
        modeRow.addView(modeSelectorButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34)));

        View spacer = new View(context);
        modeRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        sendParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(sendButton, sendParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton();
                updateSlashPopup();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        slashPopup = new SlashCommandPopup(context);
        updateSendButton();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setQuoteDismissListener(QuoteDismissListener listener) {
        this.quoteDismissListener = listener;
    }

    private void buildQuotePreview() {
        Context context = getContext();
        quotePreviewLayout = new LinearLayout(context);
        quotePreviewLayout.setOrientation(HORIZONTAL);
        quotePreviewLayout.setGravity(Gravity.CENTER_VERTICAL);
        quotePreviewLayout.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(quotePreviewLayout, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        View quoteBar = new View(context);
        quoteBar.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 2));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 3), LayoutParams.MATCH_PARENT);
        barParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        quotePreviewLayout.addView(quoteBar, barParams);

        quotePreviewText = LineTheme.text(context, "", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        quotePreviewText.setMaxLines(2);
        quotePreviewText.setEllipsize(TextUtils.TruncateAt.END);
        quotePreviewLayout.addView(quotePreviewText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        quoteCloseButton = new IconButtonView(context, IconButtonView.CLOSE);
        quoteCloseButton.setIconColor(LineTheme.TEXT_TERTIARY);
        quoteCloseButton.setIconSizeDp(28, 16);
        quoteCloseButton.setOnClickListener(v -> {
            hideQuote();
            if (quoteDismissListener != null) {
                quoteDismissListener.onQuoteDismissed();
            }
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 28), LineTheme.dp(context, 28));
        closeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        quotePreviewLayout.addView(quoteCloseButton, closeParams);

        quotePreviewLayout.setVisibility(GONE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(quotePreviewLayout, params);
    }

    private void buildImagePreview() {
        Context context = getContext();
        imagePreviewLayout = new LinearLayout(context);
        imagePreviewLayout.setOrientation(HORIZONTAL);
        imagePreviewLayout.setGravity(Gravity.CENTER_VERTICAL);
        imagePreviewLayout.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(imagePreviewLayout, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        int thumbSize = LineTheme.dp(context, 56);
        imagePreviewView = new ImageView(context);
        imagePreviewView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePreviewView.setBackgroundColor(LineTheme.SURFACE_LIGHT);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
        imagePreviewLayout.addView(imagePreviewView, thumbParams);

        TextView imageLabel = LineTheme.text(context, "", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        imageLabel.setSingleLine(true);
        imageLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        imageLabel.setMaxWidth(LineTheme.dp(context, 220));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        imagePreviewLayout.addView(imageLabel, labelParams);
        imagePreviewView.setTag(imageLabel);

        imagePreviewClose = new IconButtonView(context, IconButtonView.CLOSE);
        imagePreviewClose.setContentDescription(context.getString(R.string.composer_image_remove_desc));
        imagePreviewClose.setIconColor(LineTheme.TEXT_TERTIARY);
        imagePreviewClose.setIconSizeDp(28, 16);
        imagePreviewClose.setOnClickListener(v -> clearImage());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 28), LineTheme.dp(context, 28));
        closeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        imagePreviewLayout.addView(imagePreviewClose, closeParams);

        imagePreviewLayout.setVisibility(GONE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(imagePreviewLayout, params);
    }

    /**
     * 选择图片后调用，显示缩略图预览并暂存 base64 数据。
     */
    public void onImagePicked(Uri uri, String base64, String mimeType, String displayName) {
        pendingImageUri = uri;
        pendingImageBase64 = base64 == null ? "" : base64;
        pendingImageMimeType = mimeType == null ? "" : mimeType;
        pendingImageName = displayName == null ? "" : displayName;
        if (uri != null) {
            try {
                imagePreviewView.setImageURI(uri);
            } catch (Exception ignored) {
                imagePreviewView.setImageDrawable(null);
            }
        } else {
            imagePreviewView.setImageDrawable(null);
        }
        TextView label = (TextView) imagePreviewView.getTag();
        if (label != null) {
            label.setText(pendingImageName.length() > 0 ? pendingImageName : "image");
        }
        imagePreviewLayout.setVisibility(VISIBLE);
        updateSendButton();
    }

    /**
     * 清除当前选中的图片。
     */
    public void clearImage() {
        pendingImageUri = null;
        pendingImageBase64 = "";
        pendingImageMimeType = "";
        pendingImageName = "";
        imagePreviewView.setImageDrawable(null);
        imagePreviewLayout.setVisibility(GONE);
        updateSendButton();
    }

    public boolean hasPendingImage() {
        return pendingImageBase64.length() > 0;
    }

    @Override
    public void showQuote(String previewText) {
        if (quotePreviewLayout == null) {
            return;
        }
        quotePreviewText.setText(previewText == null ? "" : previewText);
        quotePreviewLayout.setVisibility(VISIBLE);
    }

    @Override
    public void hideQuote() {
        if (quotePreviewLayout == null) {
            return;
        }
        quotePreviewLayout.setVisibility(GONE);
    }

    public void setDraft(String text) {
        setDraft(text, Collections.emptyList());
    }

    public void setDraft(String text, List<InputAttachment> nextAttachments) {
        String value = text == null ? "" : text;
        input.setText(value);
        attachments.clear();
        if (nextAttachments != null) {
            attachments.addAll(nextAttachments);
        }
        renderAttachments();
        input.setSelection(input.getText().length());
        input.requestFocus();
        updateSendButton();
    }

    public List<InputAttachment> getAttachments() {
        return Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public List<String> selectedAttachmentPaths(String source) {
        String normalizedSource = InputAttachment.SOURCE_SSH.equals(source)
                ? InputAttachment.SOURCE_SSH
                : InputAttachment.SOURCE_LOCAL;
        ArrayList<String> paths = new ArrayList<>();
        for (InputAttachment attachment : attachments) {
            if (attachment.getSource().equals(normalizedSource)) {
                paths.add(attachment.getPath());
            }
        }
        return paths;
    }

    public void toggleAttachment(InputAttachment attachment) {
        if (attachment == null || attachment.getPath().length() == 0) {
            return;
        }
        for (int i = 0; i < attachments.size(); i++) {
            InputAttachment existing = attachments.get(i);
            if (existing.matches(attachment.getPath(), attachment.getSource())) {
                attachments.remove(i);
                renderAttachments();
                updateSendButton();
                return;
            }
        }
        attachments.add(attachment);
        renderAttachments();
        updateSendButton();
    }

    public void render(ChatUiState state) {
        boolean wasStreamingBefore = streaming;
        streaming = state.isStreaming();
        modelText.setText(state.getModelLabel());
        selectedModelId = state.getSelectedModelId();
        availableModels = state.getAvailableModels();
        contextText.setText(state.getContextLabel());
        contextText.setTextColor(state.getContextPercent() >= 80 ? LineTheme.WARNING : LineTheme.TEXT_TERTIARY);
        chatMode = state.getChatMode();
        updateEnterKeyBehavior(state.getEnterKeyBehavior());
        if (streaming && modePopup != null) {
            modePopup.dismiss();
        }
        // Auto-send queued message when streaming finishes
        if (wasStreamingBefore && !streaming && !pendingQueue.isEmpty()) {
            post(() -> sendPending());
        }
        if (streaming && modelPopup != null) {
            modelPopup.dismiss();
        }
        if (streaming) {
            dismissSlashPopup();
        } else {
            updateSlashPopup();
        }
        input.setEnabled(true); // Allow typing while AI is streaming
        attachButton.setEnabled(!streaming);
        attachButton.setAlpha(streaming ? 0.62f : 1f);
        imageButton.setEnabled(!streaming);
        imageButton.setAlpha(streaming ? 0.62f : 1f);
        input.setHint(state.hasConfiguredModel()
                ? getContext().getString(R.string.composer_hint_default)
                : getContext().getString(R.string.composer_hint_no_model));
        updateModeButtons();
        updateModelSelector();
        updateSendButton();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        borderPaint.setColor(LineTheme.BORDER);
        borderPaint.setStrokeWidth(1f);
        canvas.drawLine(0, 0, getWidth(), 0, borderPaint);
    }

    private void updateSendButton() {
        boolean hasContent = canSend();
        if (streaming) {
            if (!pendingQueue.isEmpty() && !hasContent) {
                // 有队列 + 输入框空：红色停止按钮（按=停止AI并发送队列）
                sendButton.setIconType(IconButtonView.STOP);
                sendButton.setIconColor(LineTheme.TEXT_ON_COLOR);
                sendButton.setIconSizeDp(40, 18);
                sendButton.setBackground(LineTheme.rounded(getContext(), 0xFFFF8800, 20));
            } else if (hasContent) {
                // 有内容：橙色箭头（按=追加排队）
                sendButton.setIconType(IconButtonView.ARROW_UP);
                sendButton.setIconColor(LineTheme.TEXT_ON_COLOR);
                sendButton.setIconSizeDp(40, 22);
                sendButton.setBackground(LineTheme.rounded(getContext(), 0xFFFFAA33, 20));
            } else {
                // 无内容无队列：红色停止
                sendButton.setIconType(IconButtonView.STOP);
                sendButton.setIconColor(LineTheme.TEXT_ON_COLOR);
                sendButton.setIconSizeDp(40, 18);
                sendButton.setBackground(LineTheme.rounded(getContext(), LineTheme.DANGER, 20));
            }
        } else {
            sendButton.setIconType(IconButtonView.ARROW_UP);
            sendButton.setIconColor(hasContent ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
            sendButton.setIconSizeDp(40, 22);
            sendButton.setBackground(LineTheme.rounded(getContext(), hasContent ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 20));
        }
        sendButton.setEnabled(streaming || hasContent);
        sendButton.setAlpha(sendButton.isEnabled() ? 1f : 0.72f);
    }

    private boolean canSend() {
        return input.getText().toString().trim().length() > 0
                || !attachments.isEmpty()
                || hasPendingImage();
    }

    private boolean submitCurrentInput() {
        if (!canSend()) {
            return true;
        }
        if (streaming) {
            // Enter键在streaming时：追加排队
            queueCurrentInput();
            return true;
        }
        String text = input.getText().toString();
        // Prepend quote if present
        if (quoteText != null && quoteText.length() > 0) {
            String quoted = "> " + quoteText.replace("\n", "\n> ") + "\n\n";
            text = quoted + text;
            clearQuote();
        }
        SlashCommandCatalog.Parsed parsed = SlashCommandCatalog.parse(text);
        if (parsed != null) {
            if (listener == null) {
                input.setText("");
                clearAttachments();
                clearImage();
                dismissSlashPopup();
                return true;
            }
            if (parsed.kind == SlashCommandCatalog.Kind.MODE) {
                listener.onModeChanged(parsed.mode);
            } else if (parsed.kind == SlashCommandCatalog.Kind.MODEL) {
                listener.onModelQuickSwitch(parsed.modelId);
                if (parsed.reasoningEffort != null) {
                    listener.onAiReasoningEffortChanged(parsed.reasoningEffort);
                }
            }
            input.setText("");
            clearAttachments();
            clearImage();
            dismissSlashPopup();
            return true;
        }
        if (listener != null) {
            if (hasPendingImage()) {
                listener.onSendWithImage(text, getAttachments(),
                        pendingImageBase64, pendingImageMimeType, pendingImageName);
            } else {
                listener.onSend(text, getAttachments());
            }
        }
        input.setText("");
        clearAttachments();
        clearImage();
        return true;
    }

    private boolean isPlainEnterDown(KeyEvent event) {
        return event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && !event.isShiftPressed();
    }

    public void setQuoteText(String text) {
        quoteText = text;
        if (text != null && text.length() > 0) {
            TextView preview = (TextView) quoteBlock.getChildAt(1);
            preview.setText(text.length() > 80 ? text.substring(0, 80) + "..." : text);
            quoteBlock.setVisibility(VISIBLE);
            input.requestFocus();
        } else {
            quoteBlock.setVisibility(GONE);
        }
    }

    public void clearQuote() {
        quoteText = null;
        quoteBlock.setVisibility(GONE);
    }

    private void queueCurrentInput() {
        String text = input.getText().toString();
        if (quoteText != null && quoteText.length() > 0) {
            String quoted = "> " + quoteText.replace("\n", "\n> ") + "\n\n";
            text = quoted + text;
            clearQuote();
        }
        pendingQueue.add(new QueuedItem(text, new ArrayList<>(attachments)));
        input.setText("");
        clearAttachments();
        // Update pending block
        updatePendingBlock();
        updateSendButton();
    }

    private void updatePendingBlock() {
        pendingContainer.removeAllViews();
        if (pendingQueue.isEmpty()) {
            pendingContainer.setVisibility(GONE);
            return;
        }
        Context ctx = getContext();
        // 显示最多4条，多了折叠
        int showCount = Math.min(pendingQueue.size(), 4);
        for (int i = 0; i < showCount; i++) {
            final int index = i;
            QueuedItem item = pendingQueue.get(i);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF252536);
            LineTheme.padding(row, LineTheme.MD, 4, LineTheme.SM, 4);
            // 左侧橙色竖条
            android.view.View bar = new android.view.View(ctx);
            bar.setBackgroundColor(0xFFFFAA33);
            row.addView(bar, new LinearLayout.LayoutParams(LineTheme.dp(ctx, 3), LineTheme.dp(ctx, 20)));
            // 序号 + 预览文字
            String preview = (i + 1) + ". " + (item.text.length() > 30 ? item.text.substring(0, 30) + "..." : item.text);
            TextView tv = LineTheme.text(ctx, preview, LineTheme.FONT_XS, 0xFFFFAA33, Typeface.NORMAL);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            tvp.leftMargin = LineTheme.dp(ctx, LineTheme.SM);
            row.addView(tv, tvp);
            // 单条删除按钮
            IconButtonView close = new IconButtonView(ctx, IconButtonView.CLOSE);
            close.setIconColor(LineTheme.TEXT_TERTIARY);
            close.setIconSizeDp(20, 12);
            close.setOnClickListener(v -> {
                if (index < pendingQueue.size()) {
                    pendingQueue.remove(index);
                    updatePendingBlock();
                    updateSendButton();
                }
            });
            row.addView(close, new LinearLayout.LayoutParams(LineTheme.dp(ctx, 20), LineTheme.dp(ctx, 20)));
            pendingContainer.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        // 超过4条时显示折叠提示
        if (pendingQueue.size() > 4) {
            TextView more = LineTheme.text(ctx, getContext().getString(R.string.common_more_queued, pendingQueue.size() - 4), LineTheme.FONT_XS, 0xFFCC8800, Typeface.ITALIC);
            LineTheme.padding(more, LineTheme.MD, 2, 0, 4);
            pendingContainer.addView(more, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        pendingContainer.setVisibility(VISIBLE);
    }

    private void sendPending() {
        if (pendingQueue.isEmpty()) return;
        QueuedItem item = pendingQueue.remove(0);
        updatePendingBlock();
        if (listener != null) {
            listener.onSend(item.text, item.attachments != null ? item.attachments : Collections.emptyList());
        }
    }

    private void clearPending() {
        pendingQueue.clear();
        pendingContainer.removeAllViews();
        pendingContainer.setVisibility(GONE);
        updateSendButton();
    }

    private void updateEnterKeyBehavior(String behavior) {
        enterKeyBehavior = InputSettings.normalizeEnterKeyBehavior(behavior);
        input.setImeOptions(InputSettings.ENTER_SEND.equals(enterKeyBehavior)
                ? EditorInfo.IME_ACTION_SEND
                : EditorInfo.IME_ACTION_NONE);
    }

    private void clearAttachments() {
        attachments.clear();
        renderAttachments();
        updateSendButton();
    }

    private void renderAttachments() {
        attachmentList.removeAllViews();
        if (attachments.isEmpty()) {
            attachmentScroll.setVisibility(GONE);
            return;
        }
        attachmentScroll.setVisibility(VISIBLE);
        for (InputAttachment attachment : attachments) {
            attachmentList.addView(attachmentChip(attachment));
        }
    }

    private View attachmentChip(InputAttachment attachment) {
        Context context = getContext();
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 17, LineTheme.BORDER));
        LineTheme.padding(chip, LineTheme.MD, 0, LineTheme.SM, 0);

        TextView name = LineTheme.textMedium(context, attachment.getName(), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setMaxWidth(LineTheme.dp(context, 170));
        chip.addView(name, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        IconButtonView remove = new IconButtonView(context, IconButtonView.CLOSE);
        remove.setContentDescription(getContext().getString(R.string.composer_attachment_remove_desc));
        remove.setIconColor(LineTheme.TEXT_TERTIARY);
        remove.setIconSizeDp(18, 12);
        remove.setOnClickListener(v -> {
            attachments.remove(attachment);
            renderAttachments();
            updateSendButton();
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        removeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        chip.addView(remove, removeParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34));
        params.rightMargin = LineTheme.dp(context, LineTheme.SM);
        chip.setLayoutParams(params);
        return chip;
    }

    private void updateSlashPopup() {
        if (slashPopup == null) {
            return;
        }
        if (streaming) {
            dismissSlashPopup();
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString();
        SlashState state = resolveSlashState(text);
        if (state == null) {
            dismissSlashPopup();
            return;
        }
        String signature = state.signature + "|" + chatMode;
        if (signature.equals(lastSlashSignature) && slashPopup.isShowing()) {
            return;
        }
        lastSlashSignature = signature;
        List<SlashCommandPopup.Row> rows = buildSlashRows(state);
        if (rows.isEmpty()) {
            dismissSlashPopup();
            return;
        }
        slashPopup.setSelectedIndex(state.selectedIndex);
        slashPopup.show(state.title, rows);
        slashPopup.showAtAnchor(this);
    }

    public void dismissSlashPopup() {
        if (slashPopup != null) {
            slashPopup.dismiss();
        }
        lastSlashSignature = null;
    }

    /**
     * 把当前输入文本解析为 slash popup 状态。返回 null 表示不应展示 popup。
     * 三种状态：主命令（MAIN）、模型 id（MODEL_ID）、思考等级（REASONING）。
     */
    private SlashState resolveSlashState(String text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        if (text.charAt(0) != '/') {
            return null;
        }
        String[] tokens = text.split("\\s+", -1);
        String head = tokens[0].toLowerCase();
        if ("/model".equals(head)) {
            if (tokens.length < 2 || tokens[1].length() == 0) {
                return modelIdState("");
            }
            String modelId = tokens[1];
            if (!containsModelId(modelId)) {
                return mainState("");
            }
            String reasonQuery = tokens.length >= 3 ? tokens[2] : "";
            return reasoningState(modelId, reasonQuery);
        }
        return mainState(head.substring(1));
    }

    private boolean containsModelId(String id) {
        for (ModelConfig model : availableModels) {
            if (model != null && model.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private SlashState mainState(String query) {
        List<SlashCommandCatalog.Definition> defs = SlashCommandCatalog.filterMain(query);
        int selected = -1;
        for (int i = 0; i < defs.size(); i++) {
            SlashCommandCatalog.Definition def = defs.get(i);
            if (def.kind == SlashCommandCatalog.Kind.MODE && def.token.substring(1).equalsIgnoreCase(chatMode)) {
                selected = i;
                break;
            }
        }
        return new SlashState(SlashState.Kind.MAIN,
                getContext().getString(R.string.slash_command_main_title),
                defs, query, selected, null,
                Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    private SlashState modelIdState(String query) {
        List<String> ids = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase();
        for (ModelConfig model : availableModels) {
            if (model != null) {
                if (needle.length() > 0 && !modelMatchesQuery(model, needle)) {
                    continue;
                }
                ids.add(model.getId());
            }
        }
        List<String> filtered = SlashCommandCatalog.filterModelIds(ids, query);
        int selected = -1;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).equals(selectedModelId)) {
                selected = i;
                break;
            }
        }
        return new SlashState(SlashState.Kind.MODEL_ID,
                getContext().getString(R.string.slash_command_model_title),
                Collections.<SlashCommandCatalog.Definition>emptyList(), query, selected, null,
                filtered, Collections.<String>emptyList());
    }

    private SlashState reasoningState(String modelId, String query) {
        List<String> levels = SlashCommandCatalog.filterReasoningLevels(query);
        return new SlashState(SlashState.Kind.REASONING,
                getContext().getString(R.string.slash_command_reasoning_title, modelId),
                Collections.<SlashCommandCatalog.Definition>emptyList(), query, -1, modelId,
                Collections.<String>emptyList(), levels);
    }

    private List<SlashCommandPopup.Row> buildSlashRows(SlashState state) {
        List<SlashCommandPopup.Row> rows = new ArrayList<>();
        if (state.kind == SlashState.Kind.MAIN) {
            for (SlashCommandCatalog.Definition def : state.definitions) {
                String label = def.token;
                String description = mainDescription(def.token);
                Runnable action = () -> applyMainSelection(def);
                rows.add(new SlashCommandPopup.Row(label, description, action));
            }
        } else if (state.kind == SlashState.Kind.MODEL_ID) {
            for (String id : state.modelIds) {
                String label = modelDisplayName(id);
                String description = modelDisplayDetail(id);
                Runnable action = () -> applyModelIdSelection(id);
                rows.add(new SlashCommandPopup.Row(label, description, action));
            }
        } else {
            for (String level : state.levels) {
                String description = reasoningDescription(level);
                Runnable action = () -> applyReasoningSelection(state.modelId, level);
                rows.add(new SlashCommandPopup.Row(level, description, action));
            }
        }
        return rows;
    }

    private ModelConfig findModel(String id) {
        for (ModelConfig model : availableModels) {
            if (model != null && model.getId().equals(id)) {
                return model;
            }
        }
        return null;
    }

    private String modelDisplayName(String id) {
        ModelConfig model = findModel(id);
        if (model == null) return id;
        String name = model.getName();
        if (name.length() > 0 && !name.equals(id)) return name;
        String apiId = model.getModelId();
        if (apiId.length() > 0) return apiId;
        return model.getProviderLabel();
    }

    private String modelDisplayDetail(String id) {
        ModelConfig model = findModel(id);
        if (model == null) return "";
        String label = model.getProviderLabel();
        String apiId = model.getModelId();
        if (apiId.length() > 0 && !apiId.equals(modelDisplayName(id))) {
            return label + " · " + apiId;
        }
        return label;
    }

    private static boolean modelMatchesQuery(ModelConfig model, String needle) {
        if (model.getName().toLowerCase().contains(needle)) return true;
        if (model.getModelId().toLowerCase().contains(needle)) return true;
        if (model.getId().toLowerCase().contains(needle)) return true;
        if (model.getProviderLabel().toLowerCase().contains(needle)) return true;
        return false;
    }

    private String mainDescription(String token) {
        Context ctx = getContext();
        if ("/chat".equals(token)) {
            return ctx.getString(R.string.slash_command_chat_desc);
        }
        if ("/plan".equals(token)) {
            return ctx.getString(R.string.slash_command_plan_desc);
        }
        if ("/agent".equals(token)) {
            return ctx.getString(R.string.slash_command_agent_desc);
        }
        if ("/control".equals(token)) {
            return ctx.getString(R.string.slash_command_control_desc);
        }
        if ("/model".equals(token)) {
            return ctx.getString(R.string.slash_command_model_desc);
        }
        return "";
    }

    private String reasoningDescription(String level) {
        Context ctx = getContext();
        if (AiBehaviorSettings.REASONING_OFF.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_off_desc);
        }
        if (AiBehaviorSettings.REASONING_LOW.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_low_desc);
        }
        if (AiBehaviorSettings.REASONING_MEDIUM.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_medium_desc);
        }
        if (AiBehaviorSettings.REASONING_HIGH.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_high_desc);
        }
        if (AiBehaviorSettings.REASONING_MAX.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_max_desc);
        }
        return level;
    }

    private void applyMainSelection(SlashCommandCatalog.Definition def) {
        String replacement;
        if (def.kind == SlashCommandCatalog.Kind.MODEL) {
            replacement = "/model ";
        } else {
            replacement = def.token + " ";
        }
        input.setText(replacement);
        input.setSelection(replacement.length());
        lastSlashSignature = null;
    }

    private void applyModelIdSelection(String modelId) {
        String replacement = "/model " + modelId + " ";
        input.setText(replacement);
        input.setSelection(replacement.length());
        lastSlashSignature = null;
    }

    private void applyReasoningSelection(String modelId, String level) {
        String replacement = "/model " + modelId + " " + level;
        input.setText(replacement);
        input.setSelection(replacement.length());
        lastSlashSignature = null;
    }

    private static final class SlashState {
        enum Kind { MAIN, MODEL_ID, REASONING }
        final Kind kind;
        final String title;
        final List<SlashCommandCatalog.Definition> definitions;
        final List<String> modelIds;
        final List<String> levels;
        final String query;
        final int selectedIndex;
        final String modelId;
        final String signature;

        SlashState(Kind kind, String title,
                   List<SlashCommandCatalog.Definition> definitions,
                   String query, int selectedIndex, String modelId,
                   List<String> modelIds, List<String> levels) {
            this.kind = kind;
            this.title = title;
            this.definitions = definitions == null ? Collections.<SlashCommandCatalog.Definition>emptyList() : definitions;
            this.modelIds = modelIds == null ? Collections.<String>emptyList() : modelIds;
            this.levels = levels == null ? Collections.<String>emptyList() : levels;
            this.query = query == null ? "" : query;
            this.selectedIndex = selectedIndex;
            this.modelId = modelId == null ? "" : modelId;
            this.signature = kind.name() + ":" + this.query + ":" + this.modelId + ":"
                    + this.definitions.size() + ":" + this.modelIds.size() + ":" + this.levels.size();
        }
    }

    private void updateModeButtons() {
        modeSelectorText.setText(modeLabel(chatMode));
        modeSelectorText.setTextColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT);
        modeSelectorChevron.setIconColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT_SECONDARY);
        modeSelectorButton.setBackground(LineTheme.rounded(getContext(), LineTheme.INPUT_BG, 17));
        modeSelectorButton.setEnabled(!streaming);
        modeSelectorButton.setAlpha(streaming ? 0.62f : 1f);
    }

    private void updateModelSelector() {
        modelSelectorButton.setEnabled(!streaming);
        modelSelectorButton.setAlpha(streaming ? 0.62f : 1f);
        modelChevron.setIconColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT_SECONDARY);
    }

    private PopupWindow modelSubPopup;
    
    private void showModelPopup(View anchor) {
        if (streaming) return;
        dismissSlashPopup();
        input.clearFocus();
        if (modelPopup != null && modelPopup.isShowing()) { modelPopup.dismiss(); return; }
        Context ctx = getContext();
        int rowHeight = LineTheme.dp(ctx, 40);
        int manageRowHeight = LineTheme.dp(ctx, 36);
        int popupWidth = LineTheme.dp(ctx, 140);
    
        // Deduplicate sources by providerLabel
        java.util.LinkedHashMap<String, String> sources = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, ModelConfig> sourceFirstModel = new java.util.LinkedHashMap<>();
        for (ModelConfig m : availableModels) {
            String key = m.getProviderLabel().length() > 0 ? m.getProviderLabel() : "Other";
            if (!sources.containsKey(key)) {
                sources.put(key, m.getBaseUrl());
                sourceFirstModel.put(key, m);
            }
        }
    
        if (sources.isEmpty()) {
            // No models configured, just open manage
            if (listener != null) listener.onModelManageClick();
            return;
        }
    
        // Build source list popup
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(ctx, LineTheme.INPUT_BG, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 4, 4, 4, 4);
    
        java.util.List<String> sourceNames = new java.util.ArrayList<>(sources.keySet());
        for (String sName : sourceNames) {
            // Find current model for this source
            String currentModelName = "";
            for (ModelConfig m : availableModels) {
                String pk = m.getProviderLabel().length() > 0 ? m.getProviderLabel() : "Other";
                if (pk.equals(sName) && m.getId().equals(selectedModelId)) {
                    currentModelName = m.getName().length() > 0 ? m.getName() : m.getModelId();
                    break;
                }
            }
    
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            boolean isActive = currentModelName.length() > 0;
            row.setBackground(LineTheme.rounded(ctx, isActive ? 0xFF1A2A1A : android.graphics.Color.TRANSPARENT, 8));
            LineTheme.padding(row, LineTheme.SM, 0, LineTheme.SM, 0);
            row.setClickable(true);
    
            TextView nameView = LineTheme.textMedium(ctx, sName, LineTheme.FONT_SM, isActive ? LineTheme.ACCENT : LineTheme.TEXT);
            nameView.setSingleLine(true);
            row.addView(nameView, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    
            // Small arrow indicating submenu
            TextView arrow = LineTheme.text(ctx, "\u203A", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            row.addView(arrow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    
            final String sourceName = sName;
            row.setOnClickListener(v -> showModelSubMenu(v, sourceName, sources.get(sourceName)));
            content.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        }
    
        // Manage button
        View div = new View(ctx);
        div.setBackgroundColor(LineTheme.BORDER_LIGHT);
        content.addView(div, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
        TextView manageItem = LineTheme.textMedium(ctx, "\u2699 \u7ba1\u7406\u6a21\u578b...", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY);
        manageItem.setGravity(Gravity.CENTER_VERTICAL);
        manageItem.setPadding(LineTheme.dp(ctx, LineTheme.SM), 0, 0, 0);
        manageItem.setClickable(true);
        manageItem.setOnClickListener(v -> {
            if (modelPopup != null) modelPopup.dismiss();
            post(() -> { if (listener != null) listener.onModelManageClick(); });
        });
        content.addView(manageItem, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, manageRowHeight));
    
        int popupHeight = rowHeight * sourceNames.size() + manageRowHeight + LineTheme.dp(ctx, 12);
        modelPopup = new PopupWindow(content, popupWidth, popupHeight, true);
        modelPopup.setOutsideTouchable(true);
        modelPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        modelPopup.setOnDismissListener(() -> { if (modelSubPopup != null && modelSubPopup.isShowing()) modelSubPopup.dismiss(); });
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int centeredX = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int popupX = Math.max(LineTheme.dp(ctx, LineTheme.SM), Math.min(centeredX, screenWidth - popupWidth - LineTheme.dp(ctx, LineTheme.SM)));
        modelPopup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, Math.max(0, location[1] - popupHeight - LineTheme.dp(ctx, 8)));
    }
    
    private void showModelSubMenu(View sourceRow, String sourceName, String baseUrl) {
        if (modelSubPopup != null && modelSubPopup.isShowing()) modelSubPopup.dismiss();
        Context ctx = getContext();
        int rowHeight = LineTheme.dp(ctx, 36);
        int subWidth = LineTheme.dp(ctx, 160);
    
        // Collect models for this source
        java.util.List<ModelConfig> models = new java.util.ArrayList<>();
        for (ModelConfig m : availableModels) {
            String pk = m.getProviderLabel().length() > 0 ? m.getProviderLabel() : "Other";
            if (pk.equals(sourceName)) models.add(m);
        }
    
        LinearLayout sub = new LinearLayout(ctx);
        sub.setOrientation(VERTICAL);
        sub.setBackground(LineTheme.roundedStroke(ctx, LineTheme.INPUT_BG, 10, LineTheme.BORDER_LIGHT));
        LineTheme.padding(sub, 4, 4, 4, 4);
    
        // Query button
        TextView queryBtn = LineTheme.textMedium(ctx, ctx.getString(R.string.composer_model_submenu_query_button), LineTheme.FONT_XS, LineTheme.ACCENT);
        queryBtn.setGravity(Gravity.CENTER);
        queryBtn.setBackground(LineTheme.roundedStroke(ctx, LineTheme.SURFACE_LIGHT, 6, LineTheme.ACCENT));
        LineTheme.padding(queryBtn, 0, 3, 0, 3);
        queryBtn.setClickable(true);
        queryBtn.setOnClickListener(v -> {
            queryBtn.setText(R.string.screen_model_add_query_button_loading);
            queryModelCount(baseUrl, queryBtn, ctx);
        });
        sub.addView(queryBtn, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(ctx, 28)));
    
        // Model items
        for (ModelConfig m : models) {
            boolean sel = m.getId().equals(selectedModelId);
            TextView item = LineTheme.textMedium(ctx, m.getName().length() > 0 ? m.getName() : m.getModelId(), LineTheme.FONT_SM, sel ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackground(LineTheme.rounded(ctx, sel ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 8));
            LineTheme.padding(item, LineTheme.SM, 0, LineTheme.SM, 0);
            item.setClickable(true);
            final String mid = m.getId();
            item.setOnClickListener(v2 -> {
                if (modelSubPopup != null) modelSubPopup.dismiss();
                if (modelPopup != null) modelPopup.dismiss();
                post(() -> { if (listener != null && !mid.equals(selectedModelId)) listener.onModelQuickSwitch(mid); });
            });
            sub.addView(item, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        }
    
        int subHeight = LineTheme.dp(ctx, 28) + rowHeight * Math.min(models.size(), 8) + LineTheme.dp(ctx, 8);
        if (subHeight > LineTheme.dp(ctx, 320)) subHeight = LineTheme.dp(ctx, 320);
        modelSubPopup = new PopupWindow(sub, subWidth, subHeight, false);
        modelSubPopup.setOutsideTouchable(true);
        modelSubPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    
        // Position to the right of the source row
        int[] loc = new int[2];
        sourceRow.getLocationOnScreen(loc);
        int subX = loc[0] + sourceRow.getWidth() + LineTheme.dp(ctx, 4);
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        if (subX + subWidth > screenW - LineTheme.dp(ctx, 8)) {
            subX = loc[0] - subWidth - LineTheme.dp(ctx, 4);
        }
        modelSubPopup.showAtLocation(this, Gravity.NO_GRAVITY, subX, loc[1]);
    }

    private void queryModelCount(String baseUrl, TextView queryBtn, Context ctx) {
        new Thread(() -> {
            try {
                int count = listener != null ? listener.onQueryModelCount(baseUrl) : 0;
                post(() -> {
                    queryBtn.setText(ctx.getString(R.string.composer_model_submenu_count_label, count));
                    if (modelSubPopup != null) modelSubPopup.dismiss();
                    if (modelPopup != null) modelPopup.dismiss();
                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.composer_model_submenu_query_done_toast, count), android.widget.Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                post(() -> queryBtn.setText(R.string.toast_query_failed));
            }
        }, "linecode-model-query").start();
    }

    private LinearLayout modelOptionRow(Context ctx, ModelConfig model, boolean selected) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LineTheme.dp(ctx, LineTheme.MD), 0, LineTheme.dp(ctx, LineTheme.MD), 0);
        row.setBackground(LineTheme.rounded(ctx, selected ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 11));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (modelPopup != null) modelPopup.dismiss();
            final String mid = model.getId();
            post(() -> {
                if (!mid.equals(selectedModelId) && listener != null) {
                    listener.onModelQuickSwitch(mid);
                }
            });
        });
        View dot = new View(ctx);
        dot.setBackground(LineTheme.rounded(ctx, selected ? LineTheme.TEXT_ON_COLOR : LineTheme.BORDER, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(LineTheme.dp(ctx, 7), LineTheme.dp(ctx, 7));
        dotParams.rightMargin = LineTheme.dp(ctx, LineTheme.SM);
        row.addView(dot, dotParams);
        String displayName = model.getName().length() > 0 ? model.getName() : model.getModelId();
        TextView name = LineTheme.textMedium(ctx, displayName, LineTheme.FONT_SM, selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView provider = LineTheme.text(ctx, model.getProviderLabel(), LineTheme.FONT_XS, selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pp.leftMargin = LineTheme.dp(ctx, LineTheme.SM);
        row.addView(provider, pp);
        return row;
    }

    private void showModePopup(View anchor) {
        if (streaming) {
            return;
        }
        dismissSlashPopup();
        if (modePopup != null && modePopup.isShowing()) {
            modePopup.dismiss();
            return;
        }
        Context context = getContext();
        int popupWidth = LineTheme.dp(context, 112);
        int rowHeight = LineTheme.dp(context, 38);
        int popupHeight = rowHeight * 4 + LineTheme.dp(context, 6);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        content.addView(modeOption(context, "Chat", ChatMode.CHAT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Plan", ChatMode.PLAN), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Agent", ChatMode.AGENT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "\u63a7\u5236", ChatMode.CONTROL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        modePopup = new PopupWindow(content, popupWidth, popupHeight, true);
        modePopup.setOutsideTouchable(true);
        modePopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int centeredX = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int popupX = Math.max(LineTheme.dp(context, LineTheme.SM),
                Math.min(centeredX, screenWidth - popupWidth - LineTheme.dp(context, LineTheme.SM)));
        modePopup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, Math.max(0, location[1] - popupHeight - LineTheme.dp(context, 8)));
    }

    private TextView modeOption(Context context, String label, String mode) {
        boolean selected = mode.equals(chatMode);
        TextView item = LineTheme.textMedium(context, label, LineTheme.FONT_SM,
                selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setPadding(LineTheme.dp(context, LineTheme.MD), 0, LineTheme.dp(context, LineTheme.MD), 0);
        item.setBackground(LineTheme.rounded(context, selected ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 11));
        item.setClickable(true);
        item.setOnClickListener(v -> {
            if (modePopup != null) {
                modePopup.dismiss();
            }
            if (!mode.equals(chatMode) && listener != null) {
                listener.onModeChanged(mode);
            }
        });
        return item;
    }

    private String modeLabel(String mode) {
        if (ChatMode.CHAT.equals(mode)) {
            return "Chat";
        }
        if (ChatMode.PLAN.equals(mode)) {
            return "Plan";
        }
        if (ChatMode.CONTROL.equals(mode)) {
            return "\u63a7\u5236";
        }
        return "Agent";
    }
}
