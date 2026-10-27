package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.lineai.core.model.Message;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Timelime of messages with row-level reconciliation.
 *
 * <p>Rows are keyed by message id. A new snapshot removes vanished rows, inserts/reorders the
 * rest, and rebinds only rows whose signature changed — never a wholesale adapter reset.</p>
 */
public final class TimelineView extends FrameLayout {

    public interface EmptyAction {
        void onAddModel();

        void onOpenWorkspace();
    }

    private final ScrollView scroll;
    private final LinearLayout column;
    private final LinearLayout emptyState;
    private final IconButton jumpToBottom;
    private final Map<String, MessageRow> rows = new LinkedHashMap<>();
    private EmptyAction emptyAction;
    private Tokens tokens;
    private boolean followTail = true;
    private boolean renderingEmpty;
    private Boolean emptyConfigure;
    private cn.lineai.core.model.UiPreferences preferences = cn.lineai.core.model.UiPreferences.defaults();
    private java.util.function.Consumer<String> links;
    private ToolPresentationProvider presentationProvider;

    public TimelineView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setBackgroundColor(tokens.bg);

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8));
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updateJumpButton());

        emptyState = new LinearLayout(context);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setVisibility(GONE);
        addView(emptyState, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        jumpToBottom = new IconButton(context, Icons.CHEVRON_DOWN);
        jumpToBottom.setIconColor(tokens.textOnColor);
        jumpToBottom.setIconSizeDp(44, 20);
        jumpToBottom.setContentDescription(context.getString(R.string.message_list_scroll_to_bottom_desc));
        jumpToBottom.setBackground(Ui.roundedStroke(context, tokens.accent, 22, tokens.accent));
        jumpToBottom.setElevation(Ui.dp(context, 8));
        jumpToBottom.setVisibility(GONE);
        jumpToBottom.setOnClickListener(v -> scrollToBottom());
        LayoutParams buttonParams = new LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44),
                Gravity.END | Gravity.BOTTOM);
        buttonParams.rightMargin = Ui.dp(context, 16);
        buttonParams.bottomMargin = Ui.dp(context, 16);
        addView(jumpToBottom, buttonParams);
    }

    public void setEmptyAction(EmptyAction action) {
        emptyAction = action;
    }

    public void toolPresentations(ToolPresentationProvider provider) {
        presentationProvider = provider;
        for (MessageRow row : rows.values()) {
            row.presentationProvider(provider);
        }
    }

    public void preferences(cn.lineai.core.model.UiPreferences values,
                            java.util.function.Consumer<String> linkHandler) {
        preferences = values;
        links = linkHandler;
        for (MessageRow row : rows.values()) row.preferences(values, links);
    }

    public void render(ChatState state, Tokens current) {
        applyTokens(current);
        List<Message> messages = state.messages;
        renderingEmpty = messages.isEmpty();
        emptyState.setVisibility(renderingEmpty ? VISIBLE : GONE);
        scroll.setVisibility(renderingEmpty ? GONE : VISIBLE);
        boolean configure = !state.hasModel;
        if (emptyConfigure == null || emptyConfigure != configure) {
            rebuildEmptyState(configure, current);
        }
        if (!renderingEmpty) {
            reconcile(messages, current);
        }
        if (followTail) {
            post(this::scrollToBottom);
        }
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        emptyState.setBackgroundColor(current.bg);
        jumpToBottom.setIconColor(current.textOnColor);
        jumpToBottom.setBackground(Ui.roundedStroke(getContext(), current.accent, 22, current.accent));
        for (MessageRow row : rows.values()) {
            row.applyTokens(current);
        }
        emptyConfigure = null;
    }

    private void reconcile(List<Message> messages, Tokens current) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            seen.add(message.id);
            MessageRow row = rows.get(message.id);
            if (row == null) {
                row = MessageRow.create(getContext(), message, current);
                row.presentationProvider(presentationProvider);
                rows.put(message.id, row);
                row.preferences(preferences, links);
            }
            row.bind(message, current);
            if (row.getParent() == null) {
                column.addView(row, Math.min(i, column.getChildCount()));
            } else if (column.indexOfChild(row) != i) {
                column.removeView(row);
                column.addView(row, Math.min(i, column.getChildCount()));
            }
        }
        for (String id : new java.util.ArrayList<>(rows.keySet())) {
            if (!seen.contains(id)) {
                MessageRow row = rows.remove(id);
                if (row != null && row.getParent() == column) {
                    column.removeView(row);
                }
            }
        }
    }

    private void scrollToBottom() {
        followTail = true;
        scroll.post(() -> {
            scroll.fullScroll(View.FOCUS_DOWN);
            updateJumpButton();
        });
    }

    private void updateJumpButton() {
        boolean canScroll = column.getHeight() > scroll.getHeight() + Ui.dp(getContext(), 4);
        boolean atBottom = scroll.getScrollY() + scroll.getHeight()
                >= column.getHeight() - Ui.dp(getContext(), 4);
        followTail = atBottom;
        jumpToBottom.setVisibility(canScroll && !atBottom ? VISIBLE : GONE);
        if (canScroll && !atBottom) {
            jumpToBottom.bringToFront();
        }
    }

    private void rebuildEmptyState(boolean configure, Tokens current) {
        emptyConfigure = configure;
        Context context = getContext();
        emptyState.removeAllViews();
        emptyState.setGravity(Gravity.START);
        emptyState.setBackgroundColor(current.bg);
        Ui.padding(emptyState, 28, 104, 28, 64);

        TextView title = Ui.text(context, context.getString(R.string.chat_empty_title),
                28, current.text, Typeface.NORMAL);
        emptyState.addView(title);

        TextView description = Ui.text(context, context.getString(configure
                        ? R.string.message_list_configure_desc : R.string.chat_empty_message),
                15, current.textSecondary, Typeface.NORMAL);
        description.setLineSpacing(Ui.dp(context, 6), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = Ui.dp(context, 20);
        emptyState.addView(description, descriptionParams);

        if (configure) {
            TextView addModel = actionButton(context, current,
                    context.getString(R.string.empty_state_add_model), true);
            addModel.setMinHeight(Ui.dp(context, 48));
            addModel.setOnClickListener(v -> {
                if (emptyAction != null) {
                    emptyAction.onAddModel();
                }
            });
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            buttonParams.topMargin = Ui.dp(context, 28);
            emptyState.addView(addModel, buttonParams);
            return;
        }

        // With a model configured the legacy screen shows guidance only: no action buttons.
    }

    private TextView actionButton(Context context, Tokens tokens, String label, boolean primary) {
        TextView button = Ui.text(context, label, Tokens.FONT_MD,
                primary ? tokens.textOnColor : tokens.text, Typeface.NORMAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(primary
                ? Ui.rounded(context, tokens.accent, 22)
                : Ui.roundedStroke(context, tokens.surfaceLight, 22, tokens.borderLight));
        Ui.padding(button, 16, 8, 16, 8);
        return button;
    }
}
