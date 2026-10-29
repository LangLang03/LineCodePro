package cn.lineai.feature.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;

import cn.lineai.core.model.Message;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Long-press action row under a bubble (legacy {@code MessageActionBarView}, C++ {@code
 * MessageActionBar}).
 *
 * <p>Copy and share are self-contained; quote, retry and multi-select are forwarded to the screen
 * through {@link MessageActions}. Buttons are 40x44dp with a 16dp glyph, exactly like the legacy
 * bar, and the AI bar is leading-aligned while the user bar trails.</p>
 */
final class MessageActionBar extends LinearLayout {

    private final boolean user;
    private final IconButton copy;
    private final IconButton quote;
    private final IconButton share;
    private final IconButton multiSelect;
    private final IconButton retry;
    private MessageActions actions;
    private Message message;
    private Runnable onDismiss;

    MessageActionBar(Context context, Tokens tokens, boolean user) {
        super(context);
        this.user = user;
        setOrientation(HORIZONTAL);
        setGravity(user ? Gravity.END : Gravity.START);
        setMinimumHeight(Ui.dp(context, 44));
        Ui.padding(this, 0, 3, 4, 0);

        copy = button(Icons.COPY, context.getString(R.string.message_action_copy_desc));
        addView(copy, params(context));
        quote = button(Icons.QUOTE, context.getString(R.string.message_action_quote_desc));
        addView(quote, params(context));
        share = button(Icons.SHARE, context.getString(R.string.message_action_share_desc));
        addView(share, params(context));
        multiSelect = button(Icons.CHECK_SQUARE,
                context.getString(R.string.message_action_multi_select_desc));
        addView(multiSelect, params(context));
        retry = button(Icons.ROTATE_CCW, context.getString(R.string.message_action_recall_desc));
        retry.setVisibility(GONE);
        addView(retry, params(context));

        copy.setOnClickListener(v -> {
            copyText();
            dismiss();
        });
        quote.setOnClickListener(v -> {
            if (actions != null && message != null) {
                actions.onQuote(message);
            }
            dismiss();
        });
        share.setOnClickListener(v -> {
            shareText();
            dismiss();
        });
        multiSelect.setOnClickListener(v -> {
            if (actions != null && message != null) {
                actions.onMultiSelect(message);
            }
            dismiss();
        });
        retry.setOnClickListener(v -> {
            if (actions != null && message != null) {
                actions.onRetry(message);
            }
            dismiss();
        });
        applyTokens(tokens);
        applyActionVisibility();
    }

    /** Owner hook so the row clears its own "bar open" flag whenever an action dismisses it. */
    void setOnDismiss(Runnable value) {
        onDismiss = value;
    }

    private void dismiss() {
        setVisibility(GONE);
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    void setActions(MessageActions value, Message current) {
        actions = value;
        message = current;
        applyActionVisibility();
    }

    void bind(Message current) {
        message = current;
    }

    private void applyActionVisibility() {
        quote.setVisibility(actions != null && actions.canQuote() ? VISIBLE : GONE);
        multiSelect.setVisibility(actions != null && actions.canMultiSelect()
                ? VISIBLE : GONE);
        retry.setVisibility(user && actions != null && actions.canRetry()
                ? VISIBLE : GONE);
    }

    void applyTokens(Tokens current) {
        copy.setIconColor(current.textTertiary);
        quote.setIconColor(current.textTertiary);
        share.setIconColor(current.textTertiary);
        multiSelect.setIconColor(current.textTertiary);
        retry.setIconColor(current.textTertiary);
    }

    private void copyText() {
        String text = message == null ? "" : message.plainText();
        if (text.isEmpty()) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", text));
        }
    }

    private void shareText() {
        String text = message == null ? "" : message.plainText();
        if (text.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            android.util.Log.w("MessageActionBar", "share failed", error);
        }
    }

    private IconButton button(int icon, String description) {
        IconButton button = new IconButton(getContext(), icon);
        button.setIconSizeDp(44, 16);
        button.setContentDescription(description);
        return button;
    }

    private LinearLayout.LayoutParams params(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(context, 40), Ui.dp(context, 44));
        params.rightMargin = 0;
        return params;
    }
}
