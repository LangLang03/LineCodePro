package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.lineai.feature.chat.toolcall.ToolCallCardView;
import cn.lineai.feature.chat.toolcall.ToolCallEntry;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Collapsible "Edited N files / Review" block under the final answer (C++
 * {@code ChangedFilesBlock}, legacy {@code AssistantTurnView.renderFiles()}).
 *
 * <p>Shown only when the turn produced file mutations and ended with an answer. The header counts
 * distinct paths; expanding lists one already-bound tool card per mutation so every change stays
 * individually reviewable.</p>
 */
final class ChangedFilesBlock extends LinearLayout {

    private final View rule;
    private final LinearLayout header;
    private final TextView filesLabel;
    private final TextView review;
    private final IconButton chevron;
    private final LinearLayout files;
    private final List<ToolCallCardView> cards = new ArrayList<>();
    private Tokens tokens;
    private RowEnv env;
    private String rowKey = "";
    private boolean expanded;
    private List<ToolCallEntry> edits = new ArrayList<>();

    ChangedFilesBlock(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        Ui.padding(this, 0, 24, 0, 0);

        rule = Ui.hairline(context, tokens);
        addView(rule, new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 0.5f))));

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(context, 64));
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> toggle());
        IconButton icon = new IconButton(context, Icons.FILE_PEN_LINE);
        icon.setIconColor(tokens.textSecondary);
        icon.setIconSizeDp(26, 18);
        icon.setClickable(false);
        header.addView(icon, new LayoutParams(Ui.dp(context, 26), Ui.dp(context, 32)));
        filesLabel = Ui.text(context, "", 14, tokens.text, Typeface.NORMAL);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(context, 6);
        header.addView(filesLabel, labelParams);
        review = Ui.text(context, context.getString(R.string.chat_review_changes),
                14, tokens.text, Typeface.NORMAL);
        review.setGravity(Gravity.CENTER);
        review.setMinHeight(Ui.dp(context, 48));
        Ui.padding(review, 12, 0, 0, 0);
        review.setClickable(true);
        review.setFocusable(true);
        review.setOnClickListener(v -> expand());
        header.addView(review, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(24, 16);
        chevron.setClickable(false);
        header.addView(chevron, new LayoutParams(Ui.dp(context, 24), Ui.dp(context, 32)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        files = new LinearLayout(context);
        files.setOrientation(VERTICAL);
        LayoutParams filesParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        filesParams.bottomMargin = Ui.dp(context, 12);
        addView(files, filesParams);
        applyTokens(tokens);
    }

    /** Binds the block for one turn; hidden unless the turn has edits and a final answer. */
    void bind(TurnAssembler.Row row, Tokens current, RowEnv rowEnv, String key) {
        tokens = current;
        env = rowEnv;
        rowKey = key;
        edits = edits(row.tools);
        boolean visible = row.answer != null && !edits.isEmpty();
        setVisibility(visible ? VISIBLE : GONE);
        if (!visible) {
            return;
        }
        Set<String> files = new LinkedHashSet<>();
        for (ToolCallEntry entry : edits) {
            files.add(identity(entry));
        }
        filesLabel.setText(getContext().getString(R.string.chat_files_changed, files.size()));
        expanded = env.disclosure.isOpen(key + ":files", false);
        renderCards();
        applyExpanded();
        applyTokens(current);
    }

    void applyTokens(Tokens current) {
        tokens = current;
        if (rule != null) {
            rule.setBackgroundColor(current.border);
        }
        if (filesLabel != null) {
            filesLabel.setTextColor(current.text);
        }
        if (review != null) {
            review.setTextColor(current.text);
        }
        if (chevron != null) {
            chevron.setIconColor(current.textTertiary);
        }
        for (ToolCallCardView card : cards) {
            card.applyTokens(current);
        }
    }

    private void toggle() {
        if (env == null) {
            return;
        }
        env.disclosure.toggle(rowKey + ":files");
        expanded = env.disclosure.isOpen(rowKey + ":files", false);
        applyExpanded();
    }

    private void expand() {
        if (env == null) {
            return;
        }
        expanded = true;
        env.disclosure.setOpen(rowKey + ":files", false, true);
        applyExpanded();
        for (ToolCallCardView card : cards) {
            card.setExpanded(true);
        }
    }

    private void applyExpanded() {
        chevron.setIconType(expanded ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
        files.setVisibility(expanded ? VISIBLE : GONE);
    }

    private void renderCards() {
        while (cards.size() > edits.size()) {
            ToolCallCardView card = cards.remove(cards.size() - 1);
            files.removeView(card);
        }
        while (cards.size() < edits.size()) {
            ToolCallCardView card = new ToolCallCardView(getContext(), tokens);
            card.setProvider(env.provider);
            card.setDiffLookup(env.diffLookup);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Ui.dp(getContext(), 12);
            files.addView(card, params);
            cards.add(card);
        }
        for (int index = 0; index < edits.size(); index++) {
            ToolCallCardView card = cards.get(index);
            card.setProvider(env.provider);
            card.setDiffLookup(env.diffLookup);
            card.bind(edits.get(index), tokens);
        }
    }

    private List<ToolCallEntry> edits(List<ToolCallEntry> tools) {
        List<ToolCallEntry> values = new ArrayList<>();
        for (ToolCallEntry entry : tools) {
            if (mutates(entry)) {
                values.add(entry);
            }
        }
        return values;
    }

    /** File mutations carry a write/edit/delete icon in their own presentation metadata. */
    private boolean mutates(ToolCallEntry entry) {
        ToolPresentation presentation = env == null || env.provider == null
                ? null : env.provider.presentation(entry.name);
        if (presentation == null) {
            return false;
        }
        return presentation.icon == ToolIcon.WRITE
                || presentation.icon == ToolIcon.EDIT
                || presentation.icon == ToolIcon.DELETE;
    }

    /**
     * Stable identity of one mutation: target path when the tool payload names one, else its
     * persisted diff, else the call itself.
     *
     * <p>{@link ToolJson} is the UI-safe wrapper that keeps {@code org.json} on the tool
     * boundary; repeated edits to the same path collapse into one counted file while every
     * mutation still gets its own review card.</p>
     */
    private static String identity(ToolCallEntry entry) {
        ToolJson arguments = ToolJson.tryParse(entry.arguments);
        if (arguments != null) {
            String path = arguments.text("file_path", arguments.text("path", ""));
            if (!path.isEmpty()) {
                return path;
            }
        }
        if (!entry.diffId.isEmpty()) {
            return entry.diffId;
        }
        return entry.callId.isEmpty() ? entry.name : entry.callId;
    }
}
