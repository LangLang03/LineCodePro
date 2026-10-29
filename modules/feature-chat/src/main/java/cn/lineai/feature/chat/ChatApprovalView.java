package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Bottom execution request; occupies the composer's slot without altering its draft. */
public final class ChatApprovalView extends LinearLayout {

    public interface Listener {
        void onDeny();

        void onAllowOnce();

        void onAllowAlways();
    }

    private final LinearLayout panel;
    private final IconButton icon;
    private final TextView title;
    private final TextView reason;
    private final TextView action;
    private final TextView deny;
    private final TextView once;
    private final TextView always;
    private Listener listener;
    private Tokens tokens;

    public ChatApprovalView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        Ui.padding(this, 16, 10, 16, 16);

        panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        Ui.padding(panel, 16, 12, 16, 12);
        panel.setElevation(Ui.dp(context, 2));
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        icon = new IconButton(context, Icons.WRENCH);
        icon.setIconSizeDp(24, 16);
        icon.setClickable(false);
        heading.addView(icon, new LayoutParams(Ui.dp(context, 24), Ui.dp(context, 28)));
        title = Ui.text(context, "", Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        LayoutParams titleParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = Ui.dp(context, 4);
        heading.addView(title, titleParams);
        panel.addView(heading, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        reason = Ui.text(context, "", Tokens.FONT_MD, tokens.text, Typeface.NORMAL);
        reason.setLineSpacing(Ui.dp(context, 5), 1f);
        reason.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        LayoutParams reasonParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        reasonParams.topMargin = Ui.dp(context, 6);
        panel.addView(reason, reasonParams);

        action = Ui.text(context, "", Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        action.setTypeface(Typeface.MONOSPACE);
        action.setTextIsSelectable(true);
        action.setLineSpacing(Ui.dp(context, 5), 1f);
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(action, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams actionParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Ui.dp(context, 16);
        actionParams.bottomMargin = Ui.dp(context, 10);
        panel.addView(scroll, actionParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        deny = button(context.getString(R.string.chat_approval_deny), false);
        once = button(context.getString(R.string.chat_approval_allow_once), true);
        always = button(context.getString(R.string.chat_approval_allow_always), false);
        always.setContentDescription(context.getString(R.string.chat_approval_always_desc,
                context.getString(R.string.chat_approval_allow_always),
                context.getString(R.string.chat_approval_scope)));
        deny.setOnClickListener(v -> submitDeny());
        once.setOnClickListener(v -> submitOnce());
        always.setOnClickListener(v -> submitAlways());
        buttons.addView(deny, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LayoutParams onceParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        onceParams.leftMargin = Ui.dp(context, 6);
        buttons.addView(once, onceParams);
        LayoutParams alwaysParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        alwaysParams.leftMargin = Ui.dp(context, 6);
        buttons.addView(always, alwaysParams);
        LayoutParams buttonsParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = Ui.dp(context, 10);
        panel.addView(buttons, buttonsParams);

        setVisibility(GONE);
        applyTokens(tokens);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void render(List<ToolApprovalItem> items, Tokens current) {
        applyTokens(current);
        if (items == null || items.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        ToolApprovalItem first = items.get(0);
        ToolPresentation presentation = first.presentation;
        title.setText(items.size() > 1
                ? getContext().getString(R.string.chat_approval_batch,
                        presentation.title, items.size() - 1)
                : presentation.title);
        icon.setIconType(iconFor(presentation.icon));
        reason.setText(presentation.reason.isEmpty()
                ? getContext().getString(R.string.chat_approval_reason) : presentation.reason);
        action.setText(presentation.action.isEmpty()
                ? first.call.arguments : presentation.action);
        boolean enabled = listener != null;
        deny.setEnabled(enabled);
        once.setEnabled(enabled);
        always.setEnabled(enabled);
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        panel.setBackground(Ui.roundedStroke(getContext(), current.bg, 20, current.border));
        icon.setIconColor(current.textSecondary);
        title.setTextColor(current.textSecondary);
        reason.setTextColor(current.text);
        action.setTextColor(current.textSecondary);
        styleButton(deny, false);
        styleButton(once, true);
        styleButton(always, false);
    }

    private void submitDeny() {
        if (listener != null) listener.onDeny();
    }

    private void submitOnce() {
        if (listener != null) listener.onAllowOnce();
    }

    private void submitAlways() {
        if (listener != null) listener.onAllowAlways();
    }

    private static int iconFor(ToolIcon iconType) {
        switch (iconType) {
            case READ:
                return Icons.FILE_TEXT;
            case WRITE:
                return Icons.FILE_PLUS;
            case EDIT:
                return Icons.FILE_PEN_LINE;
            case DELETE:
                return Icons.TRASH_2;
            case LIST:
                return Icons.FOLDER;
            case SEARCH:
                return Icons.SEARCH;
            case TERMINAL:
                return Icons.TERMINAL;
            case MCP:
                return Icons.MCP;
            case IMAGE:
                return Icons.IMAGE;
            case SPARKLES:
                return Icons.SPARKLES;
            case TODO:
                return Icons.SCROLL_TEXT;
            case AGENT:
                return Icons.BRAIN;
            case MEMORY:
                return Icons.BOOK_OPEN;
            case PHONE:
                return Icons.SMARTPHONE;
            case DIFF:
                return Icons.GIT_COMPARE;
            case PIPELINE:
                return Icons.GIT_BRANCH;
            case GENERIC:
            default:
                return Icons.WRENCH;
        }
    }

    private TextView button(String label, boolean primary) {
        TextView view = Ui.text(getContext(), label, Tokens.FONT_SM, tokens.text, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(Ui.dp(getContext(), 44));
        view.setFocusable(true);
        Ui.padding(view, 8, 8, 8, 8);
        return view;
    }

    private void styleButton(TextView view, boolean primary) {
        view.setTextColor(primary ? tokens.textOnColor : tokens.text);
        view.setBackground(primary
                ? Ui.rounded(getContext(), tokens.accent, Tokens.RADIUS_BUBBLE)
                : Ui.roundedStroke(getContext(), tokens.bg, Tokens.RADIUS_BUBBLE, tokens.border));
    }
}
