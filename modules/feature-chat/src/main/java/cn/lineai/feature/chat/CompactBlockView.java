package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.DotMatrixSpinner;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Context-compaction progress row (C++ {@code CompactProgressBlock}, legacy
 * {@code ContextCompactBlockView}).
 *
 * <p>[archive icon] [label] [spacer] [dot matrix] or [check/close icon], 48dp high with 6dp
 * vertical padding. An empty status means "running", exactly like {@code bind}.</p>
 */
final class CompactBlockView extends LinearLayout {

    private final IconButton icon;
    private final TextView label;
    private final DotMatrixSpinner progress;
    private final IconButton statusIcon;
    private Tokens tokens;
    private State state = State.RUNNING;
    private boolean running;
    private boolean failed;

    CompactBlockView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(context, 48));
        Ui.padding(this, 28, 6, 28, 6);

        icon = new IconButton(context, Icons.ARCHIVE);
        icon.setClickable(false);
        icon.setIconSizeDp(18, 14);
        addView(icon, new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));

        label = Ui.text(context, context.getString(R.string.context_compact_label),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = Ui.dp(context, 6);
        addView(label, labelParams);

        addView(new View(context), new LayoutParams(0, 1, 1f));

        progress = new DotMatrixSpinner(context, 18f);
        progress.start();
        addView(progress, new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));

        statusIcon = new IconButton(context, Icons.CHECK);
        statusIcon.setClickable(false);
        statusIcon.setIconSizeDp(18, 13);
        statusIcon.setVisibility(GONE);
        addView(statusIcon, new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));
        applyTokens(tokens);
    }

    void bind(String value) {
        State next = State.of(value);
        if (next == state) {
            return;
        }
        state = next;
        running = state == State.RUNNING;
        failed = state == State.FAILED;
        progress.setVisibility(running ? VISIBLE : GONE);
        if (running) {
            progress.start();
        } else {
            progress.stop();
        }
        statusIcon.setVisibility(running ? GONE : VISIBLE);
        statusIcon.setIconType(failed ? Icons.CLOSE : Icons.CHECK);
        applyTokens(tokens);
    }

    void applyTokens(Tokens current) {
        tokens = current;
        int tint = failed ? current.danger : current.textTertiary;
        icon.setIconColor(tint);
        label.setTextColor(tint);
        statusIcon.setIconColor(failed ? current.danger : current.textTertiary);
        progress.setColor(current.accent);
    }

    /** Compact-status wire values; the raw strings live only inside this enum body. */
    private enum State {
        RUNNING("running"),
        FAILED("error"),
        COMPLETE("complete"),
        UNKNOWN("");

        private final String wire;

        State(String wire) {
            this.wire = wire;
        }

        static State of(String value) {
            if (value == null || value.isEmpty()) {
                return RUNNING;
            }
            for (State state : values()) {
                if (state.wire.equals(value)) {
                    return state;
                }
            }
            return COMPLETE;
        }
    }
}
