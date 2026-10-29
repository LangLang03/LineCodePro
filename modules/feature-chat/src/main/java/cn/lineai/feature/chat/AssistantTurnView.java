package cn.lineai.feature.chat;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.model.Message;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/**
 * One folded assistant turn: process disclosure, always-visible final answer, live working
 * status and the changed-files review block (C++ {@code MessageBubble} + {@code AssistantTimeline}).
 */
final class AssistantTurnView extends LinearLayout {

    private final ProcessPanel process;
    private final TextView error;
    private final MarkdownLite answer;
    private final WorkingStatusView working;
    private final ChangedFilesBlock changes;
    private final MessageActionBar actions;
    private Tokens tokens;
    private RowEnv env;
    private Message subject;
    private String lastAnswer = "";
    private boolean live;
    private boolean actionsVisible;

    AssistantTurnView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        Ui.padding(this, 16, 0, 16, 32);

        process = new ProcessPanel(context, tokens);
        process.setVisibility(GONE);
        addView(process, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        answer = new MarkdownLite(context, tokens);
        addView(answer, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        error = Ui.text(context, "", Tokens.FONT_MD, tokens.text,
                android.graphics.Typeface.NORMAL);
        error.setLineSpacing(Ui.dp(context, 7), 1f);
        error.setVisibility(GONE);
        LayoutParams errorParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = Ui.dp(context, 6);
        addView(error, errorParams);

        working = new WorkingStatusView(context, tokens);
        working.setVisibility(GONE);
        LayoutParams workingParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        workingParams.topMargin = Ui.dp(context, 2);
        addView(working, workingParams);

        changes = new ChangedFilesBlock(context, tokens);
        changes.setVisibility(GONE);
        addView(changes, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        actions = new MessageActionBar(context, tokens, false);
        actions.setVisibility(GONE);
        actions.setOnDismiss(() -> actionsVisible = false);
        addView(actions, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        answer.setOnLongClickListener(v -> {
            if (env == null) {
                return false;
            }
            if (env.selection != null && env.selection.multiSelect() && subject != null) {
                env.selection.toggleSelection(subject.id, subject);
                return true;
            }
            if (live) {
                return false;
            }
            // Long-press toggles this bar; every other open bar closes first.
            boolean wasOpen = actionsVisible;
            if (env.selection != null) {
                env.selection.dismissActions();
            }
            actionsVisible = !wasOpen;
            actions.setVisibility(actionsVisible ? VISIBLE : GONE);
            return true;
        });
    }

    void bind(TurnAssembler.Row row, Tokens current, RowEnv rowEnv) {
        tokens = current;
        env = rowEnv;
        subject = row.subject;
        live = row.live;
        boolean hasProcess = row.hasProcess();
        process.setVisibility(hasProcess ? VISIBLE : GONE);
        if (hasProcess) {
            process.bind(row, current, rowEnv, row.key);
        }
        error.setText(row.error);
        error.setVisibility(row.error.isEmpty() ? GONE : VISIBLE);
        Message answerMessage = row.answer;
        String text = answerMessage == null ? "" : answerMessage.plainText();
        answer.setVisibility(text.isEmpty() ? GONE : VISIBLE);
        if (!text.isEmpty()) {
            answer.setCodeWrapEnabled(rowEnv.preferences.codeWrap());
            answer.setLinkHandler(rowEnv.links);
            answer.render(text, current);
        }
        if (!text.equals(lastAnswer)) {
            lastAnswer = text;
            actionsVisible = false;
            actions.setVisibility(GONE);
        }
        boolean thinking = answerMessage == null && hasProcess;
        boolean showWorking = live && !hasProcess;
        working.setVisibility(showWorking ? VISIBLE : GONE);
        if (showWorking) {
            working.bind(thinking);
            working.start();
        } else {
            working.stop();
        }
        actions.bind(answerMessage);
        actions.setActions(rowEnv.actions, answerMessage);
        actions.setVisibility(actionsVisible ? VISIBLE : GONE);
        changes.bind(row, current, rowEnv, row.key);
        applyTokens(current);
    }

    void applyTokens(Tokens current) {
        tokens = current;
        process.applyTokens(current);
        error.setTextColor(current.text);
        answer.render(lastAnswer, current);
        working.applyTokens(current);
        changes.applyTokens(current);
        actions.applyTokens(current);
    }

    void hideActions() {
        actionsVisible = false;
        actions.setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        working.stop();
        super.onDetachedFromWindow();
    }
}
