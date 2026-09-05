package cn.lineai.ui.component;
import cn.lineai.ui.theme.FlowLayoutView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;

public final class MCPSettingsScreenView extends ScreenScaffoldView {
    private static final String EXECUTION_LOCAL = "local";
    private static final String EXECUTION_SSH = "ssh";
    private static final String EXECUTION_TERMINAL_PROVIDER = "terminal_provider";

    public interface Listener {
        void onBack();

        void onExecutionModeChanged(String mode);

        void onToolGroupChanged(String id, boolean enabled);

        void onOpenSshSettings();

        void onOpenTermuxIntegration();
    }

    private final Listener listener;
    private final McpSettingsState state;

    public MCPSettingsScreenView(Context context, McpSettingsState state, Listener listener) {
        super(context, context.getString(R.string.screen_mcp_title), listener::onBack, null);
        this.listener = listener;
        this.state = state == null ? new McpSettingsState(EXECUTION_LOCAL, null) : state;
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        addExecutionTarget(content);
        if (EXECUTION_SSH.equals(this.state.getExecutionMode())) {
            addSshConnection(content);
        }
        addToolCards(content);
    }

    private void addExecutionTarget(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, context.getString(R.string.screen_mcp_section_execution)));
        LinearLayout segment = new LinearLayout(context);
        segment.setOrientation(HORIZONTAL);
        segment.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(segment, 3, 3, 3, 3);
        addModeButton(segment, context.getString(R.string.screen_mcp_execution_local), EXECUTION_LOCAL);
        addModeButton(segment, context.getString(R.string.screen_mcp_execution_ssh), EXECUTION_SSH);
        addModeButton(segment, context.getString(R.string.screen_mcp_execution_terminal_provider), EXECUTION_TERMINAL_PROVIDER);
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        segmentParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(segment, segmentParams);
        String executionDesc;
        if (EXECUTION_LOCAL.equals(state.getExecutionMode())) {
            executionDesc = context.getString(R.string.screen_mcp_execution_local_desc);
        } else if (EXECUTION_TERMINAL_PROVIDER.equals(state.getExecutionMode())) {
            executionDesc = context.getString(R.string.screen_mcp_execution_terminal_provider_desc);
        } else {
            executionDesc = context.getString(R.string.screen_mcp_execution_ssh_desc);
        }
        TextView desc = desc(context, executionDesc);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(desc, descParams);
        addCard(content, card);
    }

    private void addSshConnection(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, context.getString(R.string.screen_mcp_section_ssh)));
        TextView desc = desc(context, context.getString(R.string.screen_mcp_ssh_overview));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        LinearLayout actions = new AdaptiveActionsView(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout ssh = actionButton(context, context.getString(R.string.screen_mcp_ssh_settings), IconButtonView.SERVER, true, v -> listener.onOpenSshSettings());
        LinearLayout termux = actionButton(context, context.getString(R.string.screen_mcp_termux_integration), IconButtonView.SMARTPHONE, false, v -> listener.onOpenTermuxIntegration());
        LinearLayout.LayoutParams sshParams = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f);
        sshParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        actions.addView(ssh, sshParams);
        actions.addView(termux, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(actions, actionsParams);
        addCard(content, card);
    }

    private void addToolCards(LinearLayout content) {
        for (McpToolConfig config : state.getConfigs()) {
            if (!config.shouldShowForMode(state.getExecutionMode())) {
                continue;
            }
            addToolCard(content, iconFor(config.getIconKey()), config);
        }
    }

    private static int iconFor(String iconKey) {
        if ("shell".equals(iconKey)) return IconButtonView.TERMINAL;
        if ("web_search".equals(iconKey)) return IconButtonView.SEARCH;
        if ("image_understanding".equals(iconKey)) return IconButtonView.PAINTBRUSH;
        if ("image_generation".equals(iconKey)) return IconButtonView.SPARKLES;
        if ("agent".equals(iconKey)) return IconButtonView.BRAIN;
        if ("todo".equals(iconKey)) return IconButtonView.SCROLL_TEXT;
        return IconButtonView.MCP;
    }

    private void addToolCard(LinearLayout content, int iconType, McpToolConfig config) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(config.isEnabled() ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        icon.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 18));

        header.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        labelParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        header.addView(labels, labelParams);
        labels.addView(LineTheme.textMedium(context, config.getName(), LineTheme.FONT_MD, LineTheme.TEXT));
        TextView descView = desc(context, config.getDescription());
        LinearLayout.LayoutParams descViewParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descViewParams.topMargin = LineTheme.dp(context, 2);
        labels.addView(descView, descViewParams);

        Switch toggle = new Switch(context);
        toggle.setChecked(config.isEnabled());
        tintSwitch(toggle, (button, checked) -> listener.onToolGroupChanged(config.getId(), checked));
        header.addView(toggle, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addCard(content, card);
    }

    private void addModeButton(LinearLayout segment, String label, String mode) {
        Context context = segment.getContext();
        boolean active = mode.equals(state.getExecutionMode());
        TextView button = LineTheme.text(context, label, LineTheme.FONT_SM, active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.rounded(context, active ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 8));
        button.setClickable(true);
        button.setOnClickListener(v -> listener.onExecutionModeChanged(mode));
        segment.addView(button, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    private void tintSwitch(Switch toggle, CompoundButton.OnCheckedChangeListener listener) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT, LineTheme.TEXT_TERTIARY}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT_DIM, LineTheme.SURFACE_LIGHT}));
        toggle.setOnCheckedChangeListener(listener);
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(card, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return card;
    }

    private LinearLayout actionButton(Context context, String label, int iconType, boolean primary, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(LineTheme.roundedStroke(context, primary ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, primary ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setOnClickListener(listener);
        LineTheme.padding(button, LineTheme.SM, 0, LineTheme.SM, 0);
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        icon.setIconSizeDp(15, 15);
        icon.setClickable(false);
        button.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 15), LineTheme.dp(context, 15)));
        TextView text = LineTheme.text(context, label, LineTheme.FONT_XS, primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, 6);
        button.addView(text, textParams);
        return button;
    }

    private TextView title(Context context, String text) {
        return LineTheme.text(context, text, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
    }

    private TextView desc(Context context, String text) {
        TextView view = LineTheme.text(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setLineSpacing(LineTheme.dp(context, 3), 1f);
        return view;
    }

    private void addCard(LinearLayout content, LinearLayout card) {
        Context context = content.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(card, params);
    }
}
