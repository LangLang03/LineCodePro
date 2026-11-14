package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.lineai.core.model.Conversation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.DotMatrixSpinner;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * One selectable conversation row: title, timestamp and either the delete action or — while
 * the conversation's turn is running — a progress spinner in the same slot.
 */
final class DrawerConversationRow extends LinearLayout {

    /** Row actions forwarded to the drawer. */
    interface Callback {
        void onSelect(String conversationId);

        void onDelete(String conversationId);
    }

    private final Tokens tokens;
    private final TextView title;
    private final TextView time;
    private final IconButton trash;
    private final boolean selected;
    private Callback callback;

    DrawerConversationRow(Context context, Tokens tokens, Conversation conversation,
                          boolean selected, boolean running) {
        super(context);
        this.tokens = tokens;
        this.selected = selected;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(true);
        Ui.padding(this, 8, 16, 4, 16);
        setOnClickListener(v -> {
            if (callback != null) callback.onSelect(conversation.id);
        });
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        Ui.padding(labels, 8, 0, 8, 0);
        title = selected
                ? Ui.medium(context, conversation.title, Tokens.FONT_MD, tokens.text)
                : Ui.text(context, conversation.title, Tokens.FONT_MD, tokens.text,
                        Typeface.NORMAL);
        title.setMaxHeight(dp(21));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title, new LayoutParams(-1, -2));
        time = Ui.text(context, formatTime(conversation.updatedAt), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        LayoutParams timeParams = new LayoutParams(-1, -2);
        timeParams.topMargin = dp(6);
        labels.addView(time, timeParams);
        addView(labels, new LayoutParams(0, -2, 1f));
        trash = new IconButton(context, Icons.TRASH_2);
        trash.setIconSizeDp(48, 16);
        trash.setContentDescription(context.getString(R.string.common_delete));
        trash.setOnClickListener(v -> {
            if (callback != null) callback.onDelete(conversation.id);
        });
        if (running) {
            // A running conversation cannot be deleted; the slot shows live progress.
            trash.setVisibility(GONE);
            DotMatrixSpinner spinner = new DotMatrixSpinner(context, 14);
            spinner.setColor(tokens.accent);
            spinner.start();
            LayoutParams spinnerParams = new LayoutParams(dp(14), dp(14));
            spinnerParams.leftMargin = dp(12);
            spinnerParams.rightMargin = dp(12);
            addView(spinner, spinnerParams);
        }
        addView(trash, new LayoutParams(dp(48), dp(48)));
        restyle();
    }

    /** Installs the selection and delete callbacks. */
    void callback(Callback value) {
        callback = value;
    }

    /** Applies the selected or plain card styling. */
    void restyle() {
        setBackground(Ui.roundedStroke(getContext(),
                selected ? tokens.inputBg : tokens.surfaceElevated, 12,
                selected ? tokens.accent : tokens.borderLight));
        title.setTextColor(tokens.text);
        time.setTextColor(tokens.textTertiary);
        trash.setIconColor(tokens.textTertiary);
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    /** Relative timestamp: "M/d HH:mm" in the device locale. */
    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }
}
