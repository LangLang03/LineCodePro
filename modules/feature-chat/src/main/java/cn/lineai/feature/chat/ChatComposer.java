package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Message input: rounded panel, model/reasoning chips and a single send/stop action. */
public final class ChatComposer extends LinearLayout {

    public interface Listener {
        void onSend(String text, java.util.List<String> imagePaths,
                    java.util.List<String> filePaths);

        void onStop();

        void onAttach();

        void onAttachFile();

        void onModel();

        void onReasoning();

        void onMode();

        void onChatMode(String mode);
    }

    private final EditText input;
    private final IconButton attach;
    private final IconButton send;
    private final LinearLayout modeChip;
    private final TextView modeText;
    private final LinearLayout modelChip;
    private final TextView modelText;
    private final LinearLayout reasoningChip;
    private final TextView reasoningText;
    private final LinearLayout panel;
    private Listener listener;
    private Tokens tokens;
    private boolean streaming;
    private String modelLabel = "";
    private boolean enterSends = true;
    private final java.util.ArrayDeque<Pending> pending = new java.util.ArrayDeque<>();
    private final java.util.List<String> attachments = new java.util.ArrayList<>();
    private final java.util.List<String> files = new java.util.ArrayList<>();
    private final LinearLayout attachmentRow;

    private static final class Pending {
        final String text;
        final java.util.List<String> images;
        final java.util.List<String> files;

        Pending(String text, java.util.List<String> images, java.util.List<String> files) {
            this.text = text;
            this.images = new java.util.ArrayList<>(images);
            this.files = new java.util.ArrayList<>(files);
        }
    }

    public ChatComposer(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        setBackgroundColor(tokens.bg);
        Ui.padding(this, 16, 14, 16, 16);

        panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setBackground(Ui.roundedStroke(context, tokens.inputBg, 24, tokens.borderLight));
        Ui.padding(panel, 10, 10, 10, 8);
        addView(panel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        input = new EditText(context);
        input.setTextColor(tokens.text);
        input.setHintTextColor(tokens.textTertiary);
        input.setHint(R.string.composer_hint_default);
        input.setTextSize(14);
        input.setSingleLine(false);
        input.setMinHeight(Ui.dp(context, 40));
        input.setMaxHeight(Ui.dp(context, 132));
        input.setMinLines(1);
        input.setMaxLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setIncludeFontPadding(false);
        Ui.padding(input, 3, 10, 3, 10);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (enterSends && (actionId == EditorInfo.IME_ACTION_SEND || plainEnter(event))) {
                submit();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((view, code, event) -> {
            if (enterSends && code == android.view.KeyEvent.KEYCODE_ENTER && plainEnter(event)) {
                submit();
                return true;
            }
            return false;
        });
        panel.addView(input, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        attachmentRow = new LinearLayout(context);
        attachmentRow.setOrientation(LinearLayout.HORIZONTAL);
        attachmentRow.setVisibility(GONE);
        panel.addView(attachmentRow, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout controlRow = new LinearLayout(context);
        controlRow.setOrientation(HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);

        attach = new IconButton(context, Icons.PLUS);
        attach.setIconColor(tokens.textSecondary);
        attach.setIconSizeDp(44, 20);
        attach.setBackgroundColor(Color.TRANSPARENT);
        attach.setContentDescription(context.getString(R.string.composer_image_button_desc));
        attach.setOnClickListener(v -> {
            if (!streaming && listener != null) {
                listener.onAttach();
            }
        });
        attach.setOnLongClickListener(v -> {
            if (!streaming && listener != null) listener.onAttachFile();
            return true;
        });
        controlRow.addView(attach, new LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));

        LinearLayout pillGroup = new LinearLayout(context);
        pillGroup.setOrientation(HORIZONTAL);
        pillGroup.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pillGroupParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        pillGroupParams.leftMargin = Ui.dp(context, 6);

        modeChip = new LinearLayout(context);
        modeChip.setOrientation(LinearLayout.HORIZONTAL);
        modeChip.setGravity(Gravity.CENTER_VERTICAL);
        modeChip.setClickable(true);
        modeChip.setFocusable(true);
        modeChip.setBackground(Ui.rounded(context, tokens.surfaceLight, 16));
        Ui.padding(modeChip, 10, 0, 8, 0);
        modeChip.setOnClickListener(v -> {
            if (listener != null) listener.onMode();
        });
        IconButton modeIcon = new IconButton(context, Icons.ZAP);
        modeIcon.setIconColor(tokens.textTertiary);
        modeIcon.setIconSizeDp(14, 14);
        modeIcon.setClickable(false);
        modeChip.addView(modeIcon, new LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14)));
        modeText = Ui.medium(context, "", 12, tokens.textSecondary);
        modeText.setSingleLine(true);
        LinearLayout.LayoutParams modeTextParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        modeTextParams.leftMargin = Ui.dp(context, 5);
        modeChip.addView(modeText, modeTextParams);
        LinearLayout.LayoutParams modeChipParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, 32));
        modeChipParams.rightMargin = Ui.dp(context, 6);
        pillGroup.addView(modeChip, modeChipParams);

        modelChip = new LinearLayout(context);
        modelChip.setOrientation(HORIZONTAL);
        modelChip.setGravity(Gravity.CENTER_VERTICAL);
        modelChip.setClickable(true);
        modelChip.setFocusable(true);
        modelChip.setBackground(Ui.rounded(context, tokens.surfaceLight, 16));
        Ui.padding(modelChip, 10, 0, 8, 0);
        modelChip.setOnClickListener(v -> {
            if (listener != null) {
                listener.onModel();
            }
        });
        modelText = Ui.medium(context, "", 12, tokens.textSecondary);
        modelText.setSingleLine(true);
        modelText.setMaxWidth(Ui.dp(context, 128));
        modelText.setEllipsize(TextUtils.TruncateAt.END);
        modelChip.addView(modelText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        IconButton modelChevron = new IconButton(context, Icons.CHEVRON_DOWN);
        modelChevron.setIconColor(tokens.textTertiary);
        modelChevron.setIconSizeDp(12, 12);
        modelChevron.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(Ui.dp(context, 12), Ui.dp(context, 12));
        chevronParams.leftMargin = Ui.dp(context, 5);
        modelChip.addView(modelChevron, chevronParams);
        pillGroup.addView(modelChip, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 32)));

        reasoningChip = new LinearLayout(context);
        reasoningChip.setOrientation(HORIZONTAL);
        reasoningChip.setGravity(Gravity.CENTER_VERTICAL);
        reasoningChip.setClickable(true);
        reasoningChip.setFocusable(true);
        reasoningChip.setBackground(Ui.rounded(context, tokens.surfaceLight, 16));
        Ui.padding(reasoningChip, 10, 0, 8, 0);
        reasoningChip.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReasoning();
            }
        });
        IconButton brain = new IconButton(context, Icons.BRAIN);
        brain.setIconColor(tokens.textTertiary);
        brain.setIconSizeDp(14, 14);
        brain.setClickable(false);
        reasoningChip.addView(brain, new LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14)));
        reasoningText = Ui.medium(context, "", 12, tokens.textSecondary);
        reasoningText.setSingleLine(true);
        reasoningText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams reasoningParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        reasoningParams.leftMargin = Ui.dp(context, 5);
        reasoningChip.addView(reasoningText, reasoningParams);
        IconButton reasoningChevron = new IconButton(context, Icons.CHEVRON_DOWN);
        reasoningChevron.setIconColor(tokens.textTertiary);
        reasoningChevron.setIconSizeDp(12, 12);
        reasoningChevron.setClickable(false);
        LinearLayout.LayoutParams reasoningChevronParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 12), Ui.dp(context, 12));
        reasoningChevronParams.leftMargin = Ui.dp(context, 5);
        reasoningChip.addView(reasoningChevron, reasoningChevronParams);
        LinearLayout.LayoutParams reasoningChipParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, 32));
        reasoningChipParams.leftMargin = Ui.dp(context, 6);
        pillGroup.addView(reasoningChip, reasoningChipParams);

        controlRow.addView(pillGroup, pillGroupParams);

        send = new IconButton(context, Icons.ARROW_UP);
        send.setIconSizeDp(44, 20);
        send.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (streaming) {
                if (hasContent()) {
                    submit();
                } else if (listener != null) {
                    listener.onStop();
                }
                return;
            }
            submit();
        });
        LinearLayout.LayoutParams sendParams = new LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44));
        sendParams.leftMargin = Ui.dp(context, 6);
        controlRow.addView(send, sendParams);

        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        controlParams.topMargin = Ui.dp(context, 2);
        panel.addView(controlRow, controlParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSend();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updateSend();
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void addAttachment(String path) {
        if (path == null || path.isEmpty() || attachments.contains(path)) return;
        attachments.add(path);
        renderAttachments();
        updateSend();
    }

    public void addFileAttachment(String path) {
        if (path == null || path.isEmpty() || files.contains(path)) return;
        files.add(path);
        renderAttachments();
        updateSend();
    }

    public void clearAttachments() {
        attachments.clear();
        files.clear();
        renderAttachments();
        updateSend();
    }

    private void renderAttachments() {
        attachmentRow.removeAllViews();
        attachmentRow.setVisibility(attachments.isEmpty() && files.isEmpty() ? GONE : VISIBLE);
        for (String path : new java.util.ArrayList<>(attachments)) {
            LinearLayout chip = new LinearLayout(getContext());
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(Ui.rounded(getContext(), tokens.surfaceLight, 12));
            Ui.padding(chip, 8, 4, 4, 4);
            IconButton icon = new IconButton(getContext(), Icons.IMAGE);
            icon.setIconColor(tokens.textSecondary);
            icon.setIconSizeDp(16, 14);
            icon.setClickable(false);
            chip.addView(icon, new LinearLayout.LayoutParams(Ui.dp(getContext(), 16), Ui.dp(getContext(), 16)));
            TextView name = Ui.text(getContext(), new java.io.File(path).getName(),
                    Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            name.setMaxWidth(Ui.dp(getContext(), 120));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            nameParams.leftMargin = Ui.dp(getContext(), 4);
            chip.addView(name, nameParams);
            IconButton remove = new IconButton(getContext(), Icons.CLOSE);
            remove.setIconColor(tokens.textTertiary);
            remove.setIconSizeDp(20, 12);
            remove.setContentDescription(getContext().getString(R.string.common_delete));
            remove.setOnClickListener(v -> {
                attachments.remove(path);
                renderAttachments();
                updateSend();
            });
            chip.addView(remove, new LinearLayout.LayoutParams(Ui.dp(getContext(), 20), Ui.dp(getContext(), 20)));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = Ui.dp(getContext(), 6);
            chipParams.bottomMargin = Ui.dp(getContext(), 6);
            attachmentRow.addView(chip, chipParams);
        }

        for (String path : new java.util.ArrayList<>(files)) {
            LinearLayout chip = new LinearLayout(getContext());
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(Ui.rounded(getContext(), tokens.surfaceLight, 12));
            Ui.padding(chip, 8, 4, 4, 4);
            IconButton icon = new IconButton(getContext(), Icons.FILE_TEXT);
            icon.setIconColor(tokens.textSecondary);
            icon.setIconSizeDp(16, 14);
            icon.setClickable(false);
            chip.addView(icon, new LinearLayout.LayoutParams(Ui.dp(getContext(), 16), Ui.dp(getContext(), 16)));
            TextView name = Ui.text(getContext(), new java.io.File(path).getName(),
                    Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            name.setMaxWidth(Ui.dp(getContext(), 120));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            nameParams.leftMargin = Ui.dp(getContext(), 4);
            chip.addView(name, nameParams);
            IconButton remove = new IconButton(getContext(), Icons.CLOSE);
            remove.setIconColor(tokens.textTertiary);
            remove.setIconSizeDp(20, 12);
            remove.setContentDescription(getContext().getString(R.string.common_delete));
            remove.setOnClickListener(v -> {
                files.remove(path);
                renderAttachments();
                updateSend();
            });
            chip.addView(remove, new LinearLayout.LayoutParams(Ui.dp(getContext(), 20), Ui.dp(getContext(), 20)));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = Ui.dp(getContext(), 6);
            chipParams.bottomMargin = Ui.dp(getContext(), 6);
            attachmentRow.addView(chip, chipParams);
        }
    }

    public void render(ChatState state, Tokens current) {
        applyTokens(current);
        boolean finished = streaming && !state.streaming;
        streaming = state.streaming;
        if (finished && !pending.isEmpty()) post(this::sendPending);
        modelLabel = state.modelLabel.isEmpty()
                ? getContext().getString(R.string.slash_command_model_title) : state.modelLabel;
        modelText.setText(modelLabel);
        modelChip.setContentDescription(modelLabel);
        modeText.setText(modeLabel(state.chatMode));
        modeChip.setContentDescription(modeLabel(state.chatMode));
        input.setHint(state.hasModel
                ? R.string.composer_hint_default : R.string.composer_hint_no_model);
        updateSend();
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        panel.setBackground(Ui.roundedStroke(getContext(), current.inputBg, 24, current.borderLight));
        input.setTextColor(current.text);
        input.setHintTextColor(current.textTertiary);
        modeChip.setBackground(Ui.rounded(getContext(), current.surfaceLight, 16));
        modeText.setTextColor(current.textSecondary);
        modelChip.setBackground(Ui.rounded(getContext(), current.surfaceLight, 16));
        reasoningChip.setBackground(Ui.rounded(getContext(), current.surfaceLight, 16));
        modelText.setTextColor(current.textSecondary);
        reasoningText.setTextColor(current.textSecondary);
        send.setIconColor(hasContent() || streaming ? current.textOnColor : current.textSecondary);
        send.setBackground(Ui.rounded(getContext(), hasContent() || streaming
                ? current.accent : Color.TRANSPARENT, 22));
        updateSend();
    }

    public void setDraft(String text) {
        input.setText(text == null ? "" : text);
        input.setSelection(input.getText().length());
    }

    public String draft() {
        return input.getText().toString();
    }

    public boolean hasContent() {
        return input.getText().toString().trim().length() > 0
                || !attachments.isEmpty() || !files.isEmpty();
    }

    public void preferences(cn.lineai.core.model.UiPreferences values) {
        enterSends = values.enterSends();
        input.setImeOptions(enterSends ? EditorInfo.IME_ACTION_SEND : EditorInfo.IME_ACTION_NONE);
        reasoningText.setText(reasoningLabel(values.get(cn.lineai.core.model.UiPreferences.Field.REASONING)));
    }

    private String reasoningLabel(String effort) {
        if ("off".equals(effort)) return getContext().getString(R.string.screen_llm_thinking_off_label);
        if ("auto".equals(effort)) return getContext().getString(R.string.screen_llm_thinking_auto_label);
        if ("low".equals(effort)) return getContext().getString(R.string.screen_llm_thinking_low_label);
        if ("high".equals(effort)) return getContext().getString(R.string.screen_llm_thinking_high_label);
        if ("max".equals(effort)) return getContext().getString(R.string.screen_llm_thinking_max_label);
        return getContext().getString(R.string.screen_llm_thinking_medium_label);
    }

    private static boolean plainEnter(android.view.KeyEvent event) {
        return event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && event.hasNoModifiers();
    }

    private void submit() {
        String text = input.getText().toString().trim();
        if (listener == null) return;
        String mode = modeCommand(text);
        if (mode != null) {
            listener.onChatMode(mode);
            input.setText("");
            return;
        }
        if ("/model".equals(text)) {
            listener.onModel();
            input.setText("");
            return;
        }
        if ("/reasoning".equals(text)) {
            listener.onReasoning();
            input.setText("");
            return;
        }
        if (text.isEmpty() && attachments.isEmpty() && files.isEmpty()) return;
        Pending message = new Pending(text, attachments, files);
        if (streaming) pending.addLast(message);
        else listener.onSend(message.text, message.images, message.files);
        input.setText("");
        attachments.clear();
        files.clear();
        renderAttachments();
        updateSend();
    }

    private static String modeCommand(String text) {
        if ("/chat".equals(text)) return cn.lineai.core.model.ChatMode.CHAT;
        if ("/plan".equals(text)) return cn.lineai.core.model.ChatMode.PLAN;
        if ("/agent".equals(text)) return cn.lineai.core.model.ChatMode.AGENT;
        if ("/control".equals(text)) return cn.lineai.core.model.ChatMode.CONTROL;
        return null;
    }

    private String modeLabel(String mode) {
        String normalized = cn.lineai.core.model.ChatMode.normalize(mode);
        if (cn.lineai.core.model.ChatMode.CHAT.equals(normalized)) {
            return getContext().getString(R.string.chat_mode_chat);
        }
        if (cn.lineai.core.model.ChatMode.PLAN.equals(normalized)) {
            return getContext().getString(R.string.chat_mode_plan);
        }
        if (cn.lineai.core.model.ChatMode.CONTROL.equals(normalized)) {
            return getContext().getString(R.string.chat_mode_control);
        }
        return getContext().getString(R.string.chat_mode_agent);
    }

    private void sendPending() {
        if (!streaming && listener != null && !pending.isEmpty()) {
            Pending next = pending.removeFirst();
            listener.onSend(next.text, next.images, next.files);
        }
    }

    public android.os.Bundle save() {
        android.os.Bundle state = new android.os.Bundle();
        state.putString("draft", draft());
        state.putInt("selection", input.getSelectionStart());
        java.util.ArrayList<android.os.Bundle> queue = new java.util.ArrayList<>();
        for (Pending item : pending) {
            android.os.Bundle entry = new android.os.Bundle();
            entry.putString("text", item.text);
            entry.putStringArrayList("images", new java.util.ArrayList<>(item.images));
            entry.putStringArrayList("files", new java.util.ArrayList<>(item.files));
            queue.add(entry);
        }
        state.putParcelableArrayList("queue", queue);
        state.putStringArrayList("attachments", new java.util.ArrayList<>(attachments));
        state.putStringArrayList("files", new java.util.ArrayList<>(files));
        state.putBoolean("generationActive", streaming);
        return state;
    }

    public void restore(android.os.Bundle state) {
        if (state == null) return;
        setDraft(state.getString("draft", ""));
        input.setSelection(Math.max(0, Math.min(input.length(), state.getInt("selection", input.length()))));
        pending.clear();
        java.util.ArrayList<android.os.Bundle> queue = state.getParcelableArrayList("queue");
        if (queue != null) {
            for (android.os.Bundle entry : queue) {
                java.util.ArrayList<String> images = entry.getStringArrayList("images");
                java.util.ArrayList<String> savedFiles = entry.getStringArrayList("files");
                pending.addLast(new Pending(entry.getString("text", ""),
                        images == null ? java.util.Collections.<String>emptyList() : images,
                        savedFiles == null ? java.util.Collections.<String>emptyList() : savedFiles));
            }
        }
        attachments.clear();
        java.util.ArrayList<String> saved = state.getStringArrayList("attachments");
        if (saved != null) attachments.addAll(saved);
        java.util.ArrayList<String> savedFiles = state.getStringArrayList("files");
        if (savedFiles != null) files.addAll(savedFiles);
        renderAttachments();
        streaming = state.getBoolean("generationActive", false);
    }

    private void updateSend() {
        boolean idleStop = streaming && !hasContent();
        boolean active = streaming || hasContent();
        send.setIconType(idleStop ? Icons.STOP : Icons.ARROW_UP);
        send.setIconSizeDp(44, idleStop ? 17 : 20);
        send.setIconColor(active ? tokens.textOnColor : tokens.textSecondary);
        send.setBackground(Ui.rounded(getContext(),
                active ? tokens.accent : Color.TRANSPARENT, 22));
        send.setContentDescription(getContext().getString(
                idleStop ? R.string.chat_stop : R.string.chat_send));
        send.setEnabled(active);
    }
}
