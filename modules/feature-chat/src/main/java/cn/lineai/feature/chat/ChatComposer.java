package cn.lineai.feature.chat;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;
/**
 * Message composer mirroring C++ {@code chat_composer.cpp}: outer padding 14/16/16/16, a 24dp
 * radius panel (padding 10/10/8/10, 1dp border), a 32dp model pill, a 44dp attach + send/stop
 * action and 34dp attachment chips (radius 17). A plain tap on "+" opens the shared composer
 * action menu (thinking / mode / files), a long press opens the system image picker.
 */
public final class ChatComposer extends LinearLayout {
    /** Composer outcomes forwarded to the screen. */
    public interface Listener {
        /** Sends the trimmed prompt with image and file attachment paths. */
        void onSend(String text, List<String> imagePaths, List<String> filePaths);
        /** Stops the running generation. */
        void onStop();
        /** Menu row "files": open the workspace attachment tree. */
        void onAttachFiles();
        /** Plus long press: open the system image picker. */
        void onAttachImage();

        /** Video picker, only offered when the model declares video input support. */
        void onVideoPicker();
        /** Opens the composer action menu (thinking, mode, files). */
        void onMode();
        /** Opens the model picker. */
        void onModel();
        /** Opens the reasoning picker. */
        void onReasoning();
        /** Applies a "/chat" style chat-mode command typed in the input. */
        void onChatMode(String mode);
    }
    /** A queued send while a generation is streaming; flushed on the next render. */
    private static final class Pending {
        final String text;
        final List<String> images;
        final List<String> files;
        /** Defensive-copies the attachment lists at enqueue time. */
        Pending(String text, List<String> images, List<String> files) {
            this.text = text;
            this.images = new ArrayList<>(images);
            this.files = new ArrayList<>(files);
        }
    }
    private final EditText input;
    private final LinearLayout panel;
    private final ScrollView attachmentScroll;
    private final LinearLayout attachmentRow;
    private final IconButton attach;
    private final IconButton send;
    private final LinearLayout modelPill;
    private final TextView modelText;
    private final IconButton modelChevron;
    private final List<AttachmentChip> chips = new ArrayList<>();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final List<String> attachments = new ArrayList<>();
    private final List<String> files = new ArrayList<>();
    private Listener listener;
    private ChatCapabilities capabilities = new ChatCapabilities(true, false, false);
    private Tokens tokens;
    private boolean streaming;
    private String modelLabel = "";
    private boolean modelSwitchable = true;
    private boolean busyElsewhere;
    private boolean enterSends = true;
    /** Builds attachment strip, input panel, model pill and the attach/send controls. */
    public ChatComposer(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        setBackgroundColor(tokens.bg);
        Ui.padding(this, 16, 14, 16, 16);
        attachmentScroll = new ScrollView(context);
        attachmentScroll.setHorizontalScrollBarEnabled(false);
        attachmentScroll.setVerticalScrollBarEnabled(false);
        attachmentRow = new LinearLayout(context);
        attachmentRow.setOrientation(HORIZONTAL);
        attachmentRow.setGravity(Gravity.CENTER_VERTICAL);
        attachmentScroll.addView(attachmentRow, new ScrollView.LayoutParams(-2, -2));
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dpx(42));
        chipsParams.bottomMargin = dpx(8);
        attachmentScroll.setVisibility(GONE);
        addView(attachmentScroll, chipsParams);
        panel = new LinearLayout(context);
        // Agent views grey the whole input card; model switching is root-only.
        panel.setOrientation(VERTICAL);
        Ui.padding(panel, 10, 10, 10, 8);
        addView(panel, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        input = new EditText(context);
        input.setTextColor(tokens.text);
        input.setHintTextColor(tokens.textTertiary);
        input.setHint(R.string.composer_hint_default);
        input.setTextSize(14);
        input.setSingleLine(false);
        input.setMinHeight(dpx(40));
        input.setMaxHeight(dpx(132));
        input.setMinLines(1);
        input.setMaxLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(0);
        input.setIncludeFontPadding(false);
        Ui.padding(input, 3, 6, 3, 6);
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
        panel.addView(input, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout controlRow = new LinearLayout(context);
        controlRow.setOrientation(HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);

        attach = new IconButton(context, Icons.PLUS);
        attach.setIconColor(tokens.textSecondary);
        attach.setIconSizeDp(44, 20);
        attach.setBackgroundColor(0);
        attach.setContentDescription(context.getString(R.string.chat_context_more));
        attach.setOnClickListener(v -> {
            if (!streaming && listener != null) {
                ComposerActionMenu.show(getContext(), tokens, listener, capabilities);
            }
        });
        attach.setOnLongClickListener(v -> {
            if (!streaming && listener != null) {
                listener.onAttachImage();
            }
            return true;
        });
        controlRow.addView(attach, new LinearLayout.LayoutParams(
                dpx(44), dpx(44)));
        LinearLayout pillGroup = new LinearLayout(context);
        pillGroup.setOrientation(HORIZONTAL);
        pillGroup.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pillGroupParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        pillGroupParams.leftMargin = dpx(6);
        pillGroupParams.rightMargin = dpx(6);

        modelPill = pill(context);
        modelText = Ui.medium(context, "", 12, tokens.textSecondary);
        modelText.setSingleLine(true);
        modelText.setEllipsize(TextUtils.TruncateAt.END);
        modelText.setMaxWidth(dpx(128));
        modelText.setMaxHeight(dpx(16));
        modelPill.addView(modelText, new LinearLayout.LayoutParams(-2, -2));
        modelChevron = glyph(context, Icons.CHEVRON_DOWN, 12, tokens.textTertiary);
        LinearLayout.LayoutParams modelGlyphParams = new LinearLayout.LayoutParams(
                dpx(12), dpx(12));
        modelGlyphParams.leftMargin = dpx(5);
        modelPill.addView(modelChevron, modelGlyphParams);
        modelPill.setOnClickListener(v -> {
            if (listener != null && modelSwitchable) {
                listener.onModel();
            }
        });
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dpx(32));
        modelParams.rightMargin = dpx(6);
        pillGroup.addView(modelPill, modelParams);

        controlRow.addView(pillGroup, pillGroupParams);

        send = new IconButton(context, Icons.ARROW_UP);
        send.setIconSizeDp(44, 20);
        send.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (streaming && !hasContent()) {
                if (listener != null) {
                    listener.onStop();
                }
                return;
            }
            submit();
        });
        controlRow.addView(send, new LinearLayout.LayoutParams(
                dpx(44), dpx(44)));
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        controlParams.topMargin = dpx(2);
        panel.addView(controlRow, controlParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateSend();
            }
        });
        restyle();
        updateSend();
    }
    /** dp conversion shorthand. */
    private int dpx(float value) {
        return Ui.dp(getContext(), value);
    }

    /** Clickable 32dp pill container for the model label. */
    private LinearLayout pill(Context context) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setClickable(true);
        pill.setFocusable(true);
        Ui.padding(pill, 10, 0, 8, 0);
        return pill;
    }
    /** Non-clickable glyph button. */
    private static IconButton glyph(Context context, int icon, int sizeDp, int color) {
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(color);
        glyph.setIconSizeDp(sizeDp, sizeDp);
        glyph.setClickable(false);
        return glyph;
    }

    /** Installs the composer callbacks. */
    public void setListener(Listener value) {
        listener = value;
    }

    /** Updates the capability menu (image/video rows and the reasoning lock). */
    public void capabilities(ChatCapabilities value) {
        capabilities = value == null ? new ChatCapabilities(true, false, false) : value;
    }
    // ------------------------------------------------------------- attachments

    /** Adds an image attachment; duplicates are ignored. */
    public void addAttachment(String path) {
        if (path == null || path.isEmpty() || attachments.contains(path)) return;
        attachments.add(path);
        renderAttachments();
        updateSend();
    }
    /** Adds a file attachment; duplicates are ignored. */
    public void addFileAttachment(String path) {
        if (path == null || path.isEmpty() || files.contains(path)) return;
        files.add(path);
        renderAttachments();
        updateSend();
    }

    /** Adds or removes one attachment; used by the workspace attachment tree. */
    public void toggleFileAttachment(String path) {
        if (path == null || path.isEmpty()) return;
        if (!files.remove(path)) {
            files.add(path);
        }
        renderAttachments();
        updateSend();
    }
    /** Copy of the currently selected file paths. */
    public List<String> fileAttachmentPaths() {
        return new ArrayList<>(files);
    }

    /** Removes every image and file attachment. */
    public void clearAttachments() {
        attachments.clear();
        files.clear();
        renderAttachments();
        updateSend();
    }
    /** Rebuilds the chip strip; the scroll strip is hidden while there are no attachments. */
    private void renderAttachments() {
        attachmentRow.removeAllViews();
        chips.clear();
        for (String path : attachments) {
            addChip(path, true);
        }
        for (String path : files) {
            addChip(path, false);
        }
        attachmentScroll.setVisibility(chips.isEmpty() ? GONE : VISIBLE);
    }

    /** Appends one 34dp attachment chip. */
    private void addChip(String path, boolean image) {
        AttachmentChip chip = new AttachmentChip(path, image);
        chips.add(chip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dpx(34));
        params.rightMargin = dpx(8);
        attachmentRow.addView(chip, params);
    }
    /** One rounded chip: file name plus a remove button that edits the source lists directly. */
    private final class AttachmentChip extends LinearLayout {
        private final TextView name;
        private final IconButton remove;
        private final boolean image;

        /** Builds the chip and wires the remove button. */
        AttachmentChip(String path, boolean image) {
            super(ChatComposer.this.getContext());
            this.image = image;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            Ui.padding(this, 12, 0, 8, 0);
            name = Ui.medium(getContext(), new File(path).getName(), Tokens.FONT_SM,
                    tokens.textSecondary);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxWidth(dpx(170));
            name.setMaxHeight(dpx(18));
            addView(name, new LinearLayout.LayoutParams(-2, -2));
            remove = new IconButton(getContext(), Icons.CLOSE);
            remove.setIconColor(tokens.textTertiary);
            remove.setIconSizeDp(18, 12);
            remove.setContentDescription(getContext().getString(R.string.common_delete));
            remove.setOnClickListener(v -> {
                if (image) {
                    attachments.remove(path);
                } else {
                    files.remove(path);
                }
                renderAttachments();
                updateSend();
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    dpx(18), dpx(18));
            removeParams.leftMargin = dpx(8);
            addView(remove, removeParams);
            restyle();
        }
        /** Applies the current tokens to chip background and glyphs. */
        void restyle() {
            setBackground(Ui.roundedStroke(getContext(), tokens.inputBg, 17, tokens.border));
            name.setTextColor(tokens.textSecondary);
            remove.setIconColor(tokens.textTertiary);
        }
    }

    // ----------------------------------------------------------------- rendering
    /** Renders streaming state (send becomes stop), model pill, hint and the pending queue. */
    public void render(ChatState state, Tokens current) {
        applyTokens(current);
        boolean finished = streaming && !state.streaming;
        streaming = state.streaming;
        if (finished && !pending.isEmpty()) post(this::sendPending);
        modelLabel = state.modelLabel.isEmpty()
                ? getContext().getString(R.string.slash_command_model_title)
                : pillModelName(state.modelLabel);
        modelText.setText(modelLabel);
        modelPill.setContentDescription(state.modelLabel.isEmpty()
                ? modelLabel : state.modelLabel);
        busyElsewhere = state.busyElsewhere;
        boolean agentView = !state.agentId.isEmpty();
        boolean transientAgent = state.transientAgent;
        boolean locked = agentView && transientAgent;
        modelSwitchable = !agentView;
        input.setEnabled(!locked);
        input.setHint(locked ? R.string.composer_hint_transient
                : busyElsewhere ? R.string.composer_hint_busy_elsewhere
                : state.hasModel ? R.string.composer_hint_default
                : R.string.composer_hint_no_model);
        input.setAlpha(locked ? 0.45f : 1f);
        modelPill.setEnabled(!agentView);
        modelPill.setAlpha(agentView ? 0.45f : 1f);
        panel.setAlpha(locked ? 0.62f : 1f);
        updateSend();
    }

    /** Restyles in place; attachment chips are restyled instead of rebuilt. */
    public void applyTokens(Tokens current) {
        tokens = current;
        restyle();
    }
    /** Restyles panel, input, pills, glyphs and chips. */
    private void restyle() {
        setBackgroundColor(tokens.bg);
        panel.setBackground(Ui.roundedStroke(getContext(), tokens.inputBg, 24, tokens.border));
        input.setTextColor(tokens.text);
        input.setHintTextColor(tokens.textTertiary);
        attach.setIconColor(tokens.textSecondary);
        modelPill.setBackground(Ui.rounded(getContext(), tokens.surfaceLight, 16));
        modelText.setTextColor(tokens.textSecondary);
        modelChevron.setIconColor(tokens.textTertiary);
        for (AttachmentChip chip : chips) {
            chip.restyle();
        }
        updateSend();
    }

    /** Replaces the draft text and moves the cursor to the end. */
    public void setDraft(String text) {
        input.setText(text == null ? "" : text);
        input.setSelection(input.getText().length());
    }
    /** Current draft text. */
    public String draft() {
        return input.getText().toString();
    }

    /** True when text or any attachment is present; drives the send/stop affordance. */
    public boolean hasContent() {
        return input.getText().toString().trim().length() > 0
                || !attachments.isEmpty() || !files.isEmpty();
    }
    /** Applies the enter-sends preference to the IME action. */
    public void preferences(cn.lineai.core.model.UiPreferences values) {
        enterSends = values.enterSends();
        input.setImeOptions(enterSends ? EditorInfo.IME_ACTION_SEND : EditorInfo.IME_ACTION_NONE);
    }

    /** C++ PillModelName: the provider-qualified identifier keeps only its last segment. */
    private static String pillModelName(String name) {
        int separator = name == null ? -1 : name.lastIndexOf('/');
        if (separator < 0 || separator + 1 >= name.length()) {
            return name == null ? "" : name;
        }
        return name.substring(separator + 1).replace('\r', ' ').replace('\n', ' ');
    }
    /** True for a single plain Enter press without modifiers. */
    private static boolean plainEnter(android.view.KeyEvent event) {
        return event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && event.hasNoModifiers();
    }

    /** Sends or queues the draft; intercepts the /chat /model /reasoning slash commands. */
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
        if (streaming) {
            pending.addLast(message);
        } else {
            listener.onSend(message.text, message.images, message.files);
        }
        input.setText("");
        attachments.clear();
        files.clear();
        renderAttachments();
        updateSend();
    }
    /** Chat-mode constant for a "/chat" style command, or null when text is not one. */
    private static String modeCommand(String text) {
        if ("/chat".equals(text)) return cn.lineai.core.model.ChatMode.CHAT;
        if ("/plan".equals(text)) return cn.lineai.core.model.ChatMode.PLAN;
        if ("/agent".equals(text)) return cn.lineai.core.model.ChatMode.AGENT;
        if ("/control".equals(text)) return cn.lineai.core.model.ChatMode.CONTROL;
        return null;
    }

    /** Sends the next queued message once the streaming generation has finished. */
    private void sendPending() {
        if (!streaming && listener != null && !pending.isEmpty()) {
            Pending next = pending.removeFirst();
            listener.onSend(next.text, next.images, next.files);
        }
    }
    /** Serializes draft, cursor, pending queue and attachments for configuration changes. */
    public android.os.Bundle save() {
        android.os.Bundle state = new android.os.Bundle();
        state.putString("draft", draft());
        state.putInt("selection", input.getSelectionStart());
        ArrayList<android.os.Bundle> queue = new ArrayList<>();
        for (Pending item : pending) {
            android.os.Bundle entry = new android.os.Bundle();
            entry.putString("text", item.text);
            entry.putStringArrayList("images", new ArrayList<>(item.images));
            entry.putStringArrayList("files", new ArrayList<>(item.files));
            queue.add(entry);
        }
        state.putParcelableArrayList("queue", queue);
        state.putStringArrayList("attachments", new ArrayList<>(attachments));
        state.putStringArrayList("files", new ArrayList<>(files));
        state.putBoolean("generationActive", streaming);
        return state;
    }
    /** Restores everything {@link #save} wrote; a null bundle leaves the composer untouched. */
    public void restore(android.os.Bundle state) {
        if (state == null) return;
        setDraft(state.getString("draft", ""));
        input.setSelection(Math.max(0, Math.min(input.length(),
                state.getInt("selection", input.length()))));
        pending.clear();
        ArrayList<android.os.Bundle> queue = state.getParcelableArrayList("queue");
        if (queue != null) {
            for (android.os.Bundle entry : queue) {
                ArrayList<String> images = entry.getStringArrayList("images");
                ArrayList<String> savedFiles = entry.getStringArrayList("files");
                pending.addLast(new Pending(entry.getString("text", ""),
                        images == null ? Collections.<String>emptyList() : images,
                        savedFiles == null ? Collections.<String>emptyList() : savedFiles));
            }
        }
        attachments.clear();
        ArrayList<String> saved = state.getStringArrayList("attachments");
        if (saved != null) attachments.addAll(saved);
        files.clear();
        ArrayList<String> savedFiles = state.getStringArrayList("files");
        if (savedFiles != null) files.addAll(savedFiles);
        renderAttachments();
        streaming = state.getBoolean("generationActive", false);
    }
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // C++ gives the model pill a smaller budget on a compact viewport.
        modelText.setMaxWidth(dpx(width < dpx(400) ? 96 : 128));
    }

    /** Syncs the send/stop button and the attach button with streaming and content state. */
    private void updateSend() {
        boolean canSend = !busyElsewhere && (streaming || hasContent());
        boolean idleStop = streaming && !hasContent();
        send.setIconType(idleStop ? Icons.STOP : Icons.ARROW_UP);
        send.setIconSizeDp(44, idleStop ? 17 : 20);
        send.setIconColor(canSend ? tokens.textOnColor : tokens.textSecondary);
        send.setBackground(Ui.rounded(getContext(), canSend ? tokens.accent : 0, 22));
        send.setContentDescription(getContext().getString(
                idleStop ? R.string.chat_stop : R.string.chat_send));
        send.setEnabled(canSend);
        attach.setEnabled(!streaming);
        attach.setAlpha(streaming ? 0.62f : 1f);
    }
}
