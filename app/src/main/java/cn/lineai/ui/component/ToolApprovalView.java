package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ToolApproval;
import cn.lineai.tool.ToolNames;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.tool.ui.ToolCallUtils;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import org.json.JSONObject;

/** Bottom execution request; occupies the composer's slot without altering its draft. */
public final class ToolApprovalView extends LinearLayout {
    private final LinearLayout buttons;
    private final TextView title;
    private final TextView reason;
    private final TextView command;
    private final TextView always;
    private final TextView once;
    private final TextView reject;
    private final IconButtonView icon;
    private ToolApproval approval;
    private ToolReviewListener listener;
    private String submittedId = "";

    public ToolApprovalView(Context context) {
        super(context); setOrientation(VERTICAL);
        LineTheme.padding(this, 16, 10, 16, 16);
        LinearLayout panel = new LinearLayout(context); panel.setOrientation(VERTICAL);
        LineTheme.padding(panel, 16, 12, 16, 12);
        panel.setBackground(LineTheme.roundedStroke(context, LineTheme.BG, 20, LineTheme.BORDER));
        panel.setElevation(LineTheme.dp(context, 2));
        addView(panel, new LayoutParams(-1, -2));
        LinearLayout heading = new LinearLayout(context); heading.setGravity(Gravity.CENTER_VERTICAL);
        icon = new IconButtonView(context, IconButtonView.TERMINAL); icon.setIconSizeDp(24, 16);
        icon.setIconColor(LineTheme.TEXT_SECONDARY); icon.setClickable(false); icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heading.addView(icon, new LayoutParams(dp(24), dp(28)));
        title = LineTheme.text(context, "", 12, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LayoutParams titleParams = new LayoutParams(-2, -2); titleParams.leftMargin = dp(4); heading.addView(title, titleParams);
        panel.addView(heading, new LayoutParams(-1, -2));
        LinearLayout details = new LinearLayout(context); details.setOrientation(VERTICAL);
        LineTheme.padding(details, 0, 0, 4, 0);
        reason = LineTheme.text(context, "", 15, LineTheme.TEXT, Typeface.NORMAL); reason.setLineSpacing(dp(5), 1);
        reason.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        LayoutParams reasonParams = new LayoutParams(-1, -2); reasonParams.topMargin = dp(6); details.addView(reason, reasonParams);
        command = LineTheme.text(context, "", 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL); command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true); command.setLineSpacing(dp(5), 1); command.setHorizontallyScrolling(true);
        LayoutParams commandParams = new LayoutParams(-1, -2); commandParams.topMargin = dp(16); commandParams.bottomMargin = dp(10);
        android.widget.HorizontalScrollView commandScroll = new android.widget.HorizontalScrollView(context);
        commandScroll.setFillViewport(true);
        commandScroll.addView(command, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
        details.addView(commandScroll, commandParams);
        BoundedScrollView scroll = new BoundedScrollView(context, 156);
        scroll.addView(details, new android.widget.ScrollView.LayoutParams(-1, -2)); panel.addView(scroll, new LayoutParams(-1, -2));
        buttons = new LinearLayout(context); buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setBaselineAligned(false);
        reject = button(context.getString(R.string.chat_approval_deny), false);
        once = button(context.getString(R.string.chat_approval_allow_once), true);
        always = button(context.getString(R.string.chat_approval_allow_always), false);
        always.setTooltipText(context.getString(R.string.chat_approval_scope));
        always.setContentDescription(context.getString(R.string.chat_approval_allow_always) + ". " + context.getString(R.string.chat_approval_scope));
        reject.setOnClickListener(v -> submit("rejected")); once.setOnClickListener(v -> submit("accepted")); always.setOnClickListener(v -> submit("permanent"));
        buttons.addView(reject, new LayoutParams(0, -2, 1));
        LayoutParams onceParams = new LayoutParams(0, -2, 1); onceParams.leftMargin = dp(6); buttons.addView(once, onceParams);
        LayoutParams alwaysParams = new LayoutParams(0, -2, 1); alwaysParams.leftMargin = dp(6); buttons.addView(always, alwaysParams);
        LayoutParams buttonParams = new LayoutParams(-1, -2); buttonParams.topMargin = dp(10); panel.addView(buttons, buttonParams);
        setVisibility(GONE);
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = View.MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight() - dp(32);
        float needed = 0;
        int count = 0;
        for (int i = 0; i < buttons.getChildCount(); i++) {
            TextView button = (TextView) buttons.getChildAt(i);
            if (button.getVisibility() == GONE) continue;
            needed += button.getPaint().measureText(button.getText().toString()) + button.getPaddingLeft() + button.getPaddingRight();
            count++;
        }
        boolean stacked = needed + dp(Math.max(0, count - 1) * 6) > available;
        buttons.setOrientation(stacked ? VERTICAL : HORIZONTAL);
        for (int i = 0; i < buttons.getChildCount(); i++) {
            View button = buttons.getChildAt(i);
            LayoutParams params = (LayoutParams) button.getLayoutParams();
            int width = stacked ? LayoutParams.MATCH_PARENT : 0;
            float weight = stacked ? 0 : 1;
            int left = !stacked && i > 0 ? dp(6) : 0;
            int top = stacked && i > 0 ? dp(6) : 0;
            if (params.width != width || params.weight != weight || params.leftMargin != left || params.topMargin != top) {
                params.width = width; params.weight = weight; params.leftMargin = left; params.topMargin = top;
                button.setLayoutParams(params);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setToolReviewListener(ToolReviewListener listener) { this.listener = listener; }
    public void bind(ToolApproval next) {
        boolean changed = approval == null || next == null || !approval.getReviewId().equals(next.getReviewId());
        approval = next;
        if (changed) submittedId = "";
        setVisibility(next == null ? GONE : VISIBLE);
        if (next == null) return;
        boolean shell = ToolNames.SHELL_EXECUTE.equals(next.getCall().getName());
        boolean deleting = ToolNames.FILE_DELETE.equals(next.getCall().getName());
        title.setText(shell ? getContext().getString(R.string.chat_approval_terminal)
                : deleting ? getContext().getString(cn.lineai.tool.ui.R.string.common_delete) : next.getCall().getName());
        icon.setIconType(shell ? IconButtonView.TERMINAL : IconButtonView.WRENCH);
        JSONObject input = ToolCallUtils.parseInput(next.getCall());
        String explanation = input.optString("reason", input.optString("description", "")).trim();
        reason.setText(explanation.isEmpty() ? getContext().getString(R.string.chat_approval_reason) : explanation);
        String action = shell ? input.optString("command") : input.optString("file_path", input.optString("path", next.getCall().getArguments()));
        if (deleting && input.optJSONArray("paths") != null) {
            StringBuilder paths = new StringBuilder();
            org.json.JSONArray list = input.optJSONArray("paths");
            for (int i = 0; i < list.length(); i++) {
                if (paths.length() > 0) paths.append('\n');
                paths.append(list.optString(i));
            }
            for (String field : new String[]{"file_path", "path"}) {
                if (!input.optString(field).isEmpty()) paths.append('\n').append(input.optString(field));
            }
            action = paths.toString();
        }
        String cwd = input.optString("cwd", "").trim();
        command.setText(cwd.isEmpty() ? action : cwd + "\n" + action);
        always.setVisibility(next.canAllowPermanently() ? VISIBLE : GONE);
        boolean enabled = !submittedId.equals(next.getReviewId());
        reject.setEnabled(enabled); once.setEnabled(enabled); always.setEnabled(enabled);
    }
    private void submit(String state) {
        if (approval == null || listener == null || submittedId.equals(approval.getReviewId())) return;
        submittedId = approval.getReviewId(); reject.setEnabled(false); once.setEnabled(false); always.setEnabled(false);
        listener.onToolReview(approval.getReviewId(), state, "");
    }
    private TextView button(String label, boolean primary) {
        TextView view = LineTheme.text(getContext(), label, 13, primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(44)); view.setFocusable(true);
        LineTheme.padding(view, 8, 8, 8, 8);
        view.setBackground(primary ? LineTheme.rounded(getContext(), LineTheme.ACCENT, 18)
                : LineTheme.roundedStroke(getContext(), LineTheme.BG, 18, LineTheme.BORDER));
        return view;
    }
    private int dp(int value) { return LineTheme.dp(getContext(), value); }
}
