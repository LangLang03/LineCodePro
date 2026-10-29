package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Timeline of conversation rows with row-level reconciliation.
 *
 * <p>Rows are keyed by turn (a user bubble or one folded assistant turn). A new snapshot removes
 * vanished rows, inserts/reorders the rest, and only rebinds rows whose signature changed — never
 * a wholesale reset, so disclosure state, markdown scroll and drafts survive streaming and theme
 * changes.</p>
 */
public final class TimelineView extends FrameLayout implements SelectionHost {

    public interface EmptyAction {
        void onAddModel();

        void onOpenWorkspace();
    }

    private final ScrollView scroll;
    private final LinearLayout column;
    private final LinearLayout emptyState;
    private final IconButton jumpToBottom;
    private final Map<String, MessageRow> rows = new LinkedHashMap<>();
    private final DisclosureState disclosure = new DisclosureState();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private EmptyAction emptyAction;
    private Tokens tokens;
    private boolean followTail = true;
    private boolean renderingEmpty;
    private Boolean emptyConfigure;
    private TextView emptyTitle;
    private TextView emptyDescription;
    private TextView emptyActionButton;
    private UiPreferences preferences = UiPreferences.defaults();
    private Consumer<String> links;
    private MessageActions actions;
    private ToolPresentationProvider presentationProvider;
    private cn.lineai.feature.chat.toolcall.ToolDiffLookup diffLookup;
    private boolean multiSelect;
    private List<TurnAssembler.Row> current = new ArrayList<>();

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
        scroll.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                dismissActions();
            }
            return false;
        });
        scroll.setOnClickListener(v -> dismissActions());
        column.setOnClickListener(v -> dismissActions());

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
        jumpToBottom.setOnClickListener(v -> {
            dismissActions();
            scrollToBottom();
        });
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
        rebindAll();
    }

    /** Host actions for the long-press bar; copy and share work without a host. */
    public void messageActions(MessageActions value) {
        actions = value;
        rebindAll();
    }

    /** Installs the optional persisted-diff bridge used by write cards and the review block. */
    public void toolDiffLookup(cn.lineai.feature.chat.toolcall.ToolDiffLookup value) {
        diffLookup = value;
        rebindAll();
    }

    public void preferences(UiPreferences values, Consumer<String> linkHandler) {
        preferences = values == null ? UiPreferences.defaults() : values;
        links = linkHandler;
        rebindAll();
    }

    public void render(ChatState state, Tokens current) {
        if (current != tokens) {
            applyTokens(current);
        }
        List<TurnAssembler.Row> next = TurnAssembler.assemble(state.messages,
                pendingCallIds(state), state.streaming);
        this.current = next;
        renderingEmpty = next.isEmpty();
        emptyState.setVisibility(renderingEmpty ? VISIBLE : GONE);
        scroll.setVisibility(renderingEmpty ? GONE : VISIBLE);
        boolean configure = !state.hasModel;
        if (emptyConfigure == null || emptyConfigure != configure) {
            rebuildEmptyState(configure, tokens);
        }
        if (!renderingEmpty) {
            reconcile(next);
        }
        if (followTail) {
            post(this::scrollToBottom);
        }
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        restyleEmptyState();
        jumpToBottom.setIconColor(current.textOnColor);
        jumpToBottom.setBackground(Ui.roundedStroke(getContext(), current.accent, 22,
                current.accent));
        for (MessageRow row : rows.values()) {
            row.applyTokens(current);
        }
    }

    /** Closes the long-press action bar of every row. */
    public void dismissMessageActions() {
        dismissActions();
    }

    public void setMultiSelectMode(boolean enabled) {
        dismissActions();
        multiSelect = enabled;
        if (!enabled) {
            selectedIds.clear();
        }
        for (Map.Entry<String, MessageRow> entry : rows.entrySet()) {
            TurnAssembler.Row data = dataFor(entry.getKey());
            String id = data == null || data.subject == null ? "" : data.subject.id;
            MessageRow row = entry.getValue();
            row.setMultiSelect(enabled);
            row.setRowSelected(enabled && selectedIds.contains(id));
        }
        notifySelection();
    }

    public boolean isMultiSelectMode() {
        return multiSelect;
    }

    public List<Message> selectedMessages() {
        List<Message> result = new ArrayList<>();
        for (TurnAssembler.Row row : current) {
            if (row.subject != null && selectedIds.contains(row.subject.id)) {
                result.add(row.subject);
            }
        }
        return result;
    }

    @Override
    public boolean multiSelect() {
        return multiSelect;
    }

    @Override
    public boolean isSelected(String messageId) {
        return selectedIds.contains(messageId);
    }

    @Override
    public void dismissActions() {
        for (MessageRow row : rows.values()) {
            row.hideActions();
        }
    }

    @Override
    public void toggleSelection(String messageId, Message message) {
        if (messageId == null || messageId.isEmpty()) {
            return;
        }
        if (!selectedIds.remove(messageId)) {
            selectedIds.add(messageId);
        }
        rowFor(messageId);
        notifySelection();
    }

    private void rowFor(String messageId) {
        for (TurnAssembler.Row row : current) {
            if (row.subject != null && messageId.equals(row.subject.id)) {
                MessageRow view = rows.get(row.key);
                if (view != null) {
                    view.setSelected(selectedIds.contains(messageId));
                }
            }
        }
    }

    private void notifySelection() {
        if (actions != null) {
            actions.onSelectionChanged(selectedMessages());
        }
    }

    private void reconcile(List<TurnAssembler.Row> next) {
        Set<String> seen = new HashSet<>();
        RowEnv env = env();
        for (int index = 0; index < next.size(); index++) {
            TurnAssembler.Row data = next.get(index);
            seen.add(data.key);
            MessageRow row = rows.get(data.key);
            if (row == null) {
                row = MessageRow.create(getContext(), data, tokens);
                rows.put(data.key, row);
            }
            row.setMultiSelect(multiSelect);
            row.setVisibility(data.visible() ? VISIBLE : GONE);
            row.bind(data, tokens, env);
            row.setRowSelected(multiSelect && data.subject != null
                    && selectedIds.contains(data.subject.id));
            if (row.getParent() == null) {
                column.addView(row, Math.min(index, column.getChildCount()));
            } else if (column.indexOfChild(row) != index) {
                column.removeView(row);
                column.addView(row, Math.min(index, column.getChildCount()));
            }
        }
        for (String key : new ArrayList<>(rows.keySet())) {
            if (!seen.contains(key)) {
                MessageRow row = rows.remove(key);
                if (row != null && row.getParent() == column) {
                    column.removeView(row);
                }
            }
        }
    }

    private void rebindAll() {
        for (Map.Entry<String, MessageRow> entry : rows.entrySet()) {
            TurnAssembler.Row data = dataFor(entry.getKey());
            if (data != null) {
                entry.getValue().bind(data, tokens, env());
            }
        }
    }

    private TurnAssembler.Row dataFor(String key) {
        for (TurnAssembler.Row data : current) {
            if (data.key.equals(key)) {
                return data;
            }
        }
        return null;
    }

    private RowEnv env() {
        return new RowEnv(disclosure, presentationProvider, preferences, actions, this,
                diffLookup, links);
    }

    private static Set<String> pendingCallIds(ChatState state) {
        Set<String> ids = new HashSet<>();
        for (ToolApprovalItem item : state.pendingApprovals) {
            if (item.call != null && !item.call.id.isEmpty()) {
                ids.add(item.call.id);
            }
        }
        return ids;
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

        emptyTitle = Ui.text(context, context.getString(R.string.chat_empty_title),
                28, current.text, Typeface.NORMAL);
        emptyState.addView(emptyTitle);

        emptyDescription = Ui.text(context, context.getString(configure
                        ? R.string.message_list_configure_desc : R.string.chat_empty_message),
                15, current.textSecondary, Typeface.NORMAL);
        emptyDescription.setLineSpacing(Ui.dp(context, 6), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = Ui.dp(context, 20);
        emptyState.addView(emptyDescription, descriptionParams);
        emptyActionButton = null;

        if (configure) {
            emptyActionButton = actionButton(context, current,
                    context.getString(R.string.empty_state_add_model), true);
            emptyActionButton.setMinHeight(Ui.dp(context, 48));
            emptyActionButton.setOnClickListener(v -> {
                if (emptyAction != null) {
                    emptyAction.onAddModel();
                }
            });
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            buttonParams.topMargin = Ui.dp(context, 28);
            emptyState.addView(emptyActionButton, buttonParams);
        }
        restyleEmptyState();
    }

    private void restyleEmptyState() {
        if (emptyTitle == null) {
            return;
        }
        emptyTitle.setTextColor(tokens.text);
        emptyDescription.setTextColor(tokens.textSecondary);
        emptyState.setBackgroundColor(tokens.bg);
        if (emptyActionButton != null) {
            emptyActionButton.setTextColor(tokens.textOnColor);
            emptyActionButton.setBackground(Ui.rounded(getContext(), tokens.accent, 22));
        }
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
