package cn.lineai.feature.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Multi-select action bar: count, copy, share and close.
 *
 * <p>Copy and share build a plain-text transcript of the selected messages in timeline order, so
 * the feature works without a host callback. The bar owns no selection state beyond a copy for the
 * action handlers; {@link ChatScreenView} remains the source of truth.</p>
 */
final class SelectionBar extends LinearLayout {

    private final TextView count;
    private final IconButton copy;
    private final IconButton share;
    private final IconButton close;
    private final List<Message> selected = new ArrayList<>();
    private Tokens tokens;

    SelectionBar(Context context, Tokens tokens, Runnable onClose) {
        super(context);
        this.tokens = tokens;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(this, 16, 0, 8, 0);

        count = Ui.text(context, "", Tokens.FONT_SM, tokens.textSecondary,
                android.graphics.Typeface.NORMAL);
        addView(count, new LayoutParams(0, -2, 1f));

        copy = button(Icons.COPY, R.string.message_action_copy_desc, false);
        copy.setOnClickListener(v -> copySelection());
        addView(copy, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));

        share = button(Icons.SHARE, R.string.message_action_share_desc, true);
        share.setOnClickListener(v -> shareSelection());
        addView(share, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));

        close = button(Icons.CLOSE, R.string.common_close, false);
        close.setOnClickListener(v -> onClose.run());
        addView(close, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
        applyTokens(tokens);
        setActionsEnabled(false);
    }

    void setSelection(List<Message> values) {
        selected.clear();
        if (values != null) {
            selected.addAll(values);
        }
        count.setText(getContext().getString(R.string.chat_selection_count, selected.size()));
        setActionsEnabled(!selected.isEmpty());
    }

    void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        count.setTextColor(current.textSecondary);
        copy.setIconColor(current.textSecondary);
        close.setIconColor(current.textSecondary);
        share.setIconColor(current.textOnColor);
        share.setBackground(Ui.rounded(getContext(), current.accent, 20));
    }

    private IconButton button(int icon, int description, boolean primary) {
        IconButton view = new IconButton(getContext(), icon);
        view.setIconSizeDp(40, 18);
        view.setContentDescription(getContext().getString(description));
        view.setIconColor(primary ? tokens.textOnColor : tokens.textSecondary);
        if (primary) {
            view.setBackground(Ui.rounded(getContext(), tokens.accent, 20));
        }
        return view;
    }

    private void setActionsEnabled(boolean enabled) {
        copy.setEnabled(enabled);
        share.setEnabled(enabled);
        copy.setAlpha(enabled ? 1f : 0.4f);
        share.setAlpha(enabled ? 1f : 0.4f);
    }

    private String transcript() {
        StringBuilder out = new StringBuilder();
        for (Message message : selected) {
            String text = message.plainText().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(text);
        }
        return out.toString();
    }

    private void copySelection() {
        String text = transcript();
        if (text.isEmpty()) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", text));
        }
    }

    private void shareSelection() {
        String text = transcript();
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
            android.util.Log.w("SelectionBar", "share selection failed", error);
        }
    }
}
