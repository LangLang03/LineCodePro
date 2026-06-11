package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import cn.lineai.model.ChatMode;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.InputSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComposerView extends LinearLayout {
    public interface Listener {
        void onSend(String text, List<InputAttachment> attachments);

        void onAttachClick();

        void onModeChanged(String mode);

        void onStop();

        void onModelQuickSwitch(String modelId);

        void onModelManageClick();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearLayout modelSelectorButton;
    private final TextView modelText;
    private final IconButtonView modelChevron;
    private final TextView contextText;
    private final LinearLayout modeSelectorButton;
    private final TextView modeSelectorText;
    private final IconButtonView modeSelectorChevron;
    private final HorizontalScrollView attachmentScroll;
    private final LinearLayout attachmentList;
    private final IconButtonView attachButton;
    private final EditText input;
    private final IconButtonView sendButton;
    private final ArrayList<InputAttachment> attachments = new ArrayList<>();
    private PopupWindow modePopup;
    private PopupWindow modelPopup;
    private boolean streaming;
    private String chatMode = ChatMode.DEFAULT;
    private String enterKeyBehavior = InputSettings.ENTER_SEND;
    private String selectedModelId = "";
    private List<ModelConfig> availableModels = Collections.emptyList();
    private Listener listener;

    public ComposerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setWillNotDraw(false);
        LineTheme.padding(this, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.LG);

        attachmentScroll = new HorizontalScrollView(context);
        attachmentScroll.setHorizontalScrollBarEnabled(false);
        attachmentScroll.setVisibility(GONE);
        attachmentList = new LinearLayout(context);
        attachmentList.setOrientation(HORIZONTAL);
        attachmentScroll.addView(attachmentList, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams attachmentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        attachmentParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(attachmentScroll, attachmentParams);

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

        metaRow.addView(modelSelectorButton, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        contextText = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        contextParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        metaRow.addView(contextText, contextParams);

        android.view.View divider = new android.view.View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

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

        input = new EditText(context);
        input.setTextColor(LineTheme.TEXT);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setHint("输入消息...");
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
            }
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(input, inputParams);

        sendButton = new IconButtonView(context, IconButtonView.ARROW_UP);
        sendButton.setIconSizeDp(40, 22);
        sendButton.setOnClickListener(v -> {
            if (streaming) {
                if (listener != null) {
                    listener.onStop();
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
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updateSendButton();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
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
        if (streaming && modelPopup != null) {
            modelPopup.dismiss();
        }
        input.setEnabled(!streaming);
        attachButton.setEnabled(!streaming);
        attachButton.setAlpha(streaming ? 0.62f : 1f);
        input.setHint(state.hasConfiguredModel() ? "输入消息..." : "请先到设置 → 模型管理配置模型");
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
            sendButton.setIconType(IconButtonView.STOP);
            sendButton.setIconColor(LineTheme.TEXT_ON_COLOR);
            sendButton.setIconSizeDp(40, 18);
            sendButton.setBackground(LineTheme.rounded(getContext(), LineTheme.DANGER, 20));
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
        return input.getText().toString().trim().length() > 0 || !attachments.isEmpty();
    }

    private boolean submitCurrentInput() {
        if (streaming || !canSend()) {
            return true;
        }
        if (listener != null) {
            listener.onSend(input.getText().toString(), getAttachments());
        }
        input.setText("");
        clearAttachments();
        return true;
    }

    private boolean isPlainEnterDown(KeyEvent event) {
        return event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && !event.isShiftPressed();
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
        remove.setContentDescription("移除附件");
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

    private void showModelPopup(View anchor) {
        if (streaming) return;
        if (modelPopup != null && modelPopup.isShowing()) { modelPopup.dismiss(); return; }
        Context ctx = getContext();
        int popupWidth = LineTheme.dp(ctx, 240);
        int rowHeight = LineTheme.dp(ctx, 38);
        int separatorHeight = LineTheme.dp(ctx, 1);
        int manageRowHeight = LineTheme.dp(ctx, 36);
        int modelCount = availableModels.size();
        int visibleRows = Math.min(modelCount, 8);
        int popupHeight = rowHeight * visibleRows + separatorHeight + manageRowHeight + LineTheme.dp(ctx, 6);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(ctx, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        if (modelCount > 8) {
            android.widget.ScrollView sw = new android.widget.ScrollView(ctx);
            LinearLayout sc = new LinearLayout(ctx);
            sc.setOrientation(VERTICAL);
            for (int i = 0; i < modelCount; i++) {
                ModelConfig m = availableModels.get(i);
                sc.addView(modelOptionRow(ctx, m, m.getId().equals(selectedModelId)),
                        new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
            }
            sw.addView(sc, new android.widget.ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            content.addView(sw, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight * visibleRows));
        } else {
            for (int i = 0; i < modelCount; i++) {
                ModelConfig m = availableModels.get(i);
                content.addView(modelOptionRow(ctx, m, m.getId().equals(selectedModelId)),
                        new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
            }
        }
        View divider = new View(ctx);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        content.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, separatorHeight));
        TextView manageItem = LineTheme.textMedium(ctx, "\u2699 \u7ba1\u7406\u6a21\u578b...", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY);
        manageItem.setGravity(Gravity.CENTER_VERTICAL);
        manageItem.setSingleLine(true);
        manageItem.setPadding(LineTheme.dp(ctx, LineTheme.MD), 0, LineTheme.dp(ctx, LineTheme.MD), 0);
        manageItem.setClickable(true);
        manageItem.setOnClickListener(v -> {
            if (modelPopup != null) modelPopup.dismiss();
            post(() -> {
                if (listener != null) listener.onModelManageClick();
            });
        });
        content.addView(manageItem, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, manageRowHeight));
        modelPopup = new PopupWindow(content, popupWidth, popupHeight, true);
        modelPopup.setOutsideTouchable(true);
        modelPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int centeredX = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int popupX = Math.max(LineTheme.dp(ctx, LineTheme.SM), Math.min(centeredX, screenWidth - popupWidth - LineTheme.dp(ctx, LineTheme.SM)));
        modelPopup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, Math.max(0, location[1] - popupHeight - LineTheme.dp(ctx, 8)));
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
        if (modePopup != null && modePopup.isShowing()) {
            modePopup.dismiss();
            return;
        }
        Context context = getContext();
        int popupWidth = LineTheme.dp(context, 112);
        int rowHeight = LineTheme.dp(context, 38);
        int popupHeight = rowHeight * 3 + LineTheme.dp(context, 6);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        content.addView(modeOption(context, "Chat", ChatMode.CHAT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Plan", ChatMode.PLAN), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Agent", ChatMode.AGENT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
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
        return "Agent";
    }
}
