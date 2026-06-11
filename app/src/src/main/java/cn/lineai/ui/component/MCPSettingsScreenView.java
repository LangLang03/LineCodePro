package cn.lineai.ui.component;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.ui.theme.LineTheme;

public final class MCPSettingsScreenView extends ScreenScaffoldView {
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
        super(context, "工具与执行", listener::onBack, null);
        this.listener = listener;
        this.state = state == null ? new McpSettingsState(ToolSettingsRepository.EXECUTION_LOCAL, null) : state;
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        addExecutionTarget(content);
        if (ToolSettingsRepository.EXECUTION_SSH.equals(this.state.getExecutionMode())) {
            addSshConnection(content);
        }
        addToolCards(content);
    }

    private void addExecutionTarget(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, "执行目标"));
        LinearLayout segment = new LinearLayout(context);
        segment.setOrientation(HORIZONTAL);
        segment.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(segment, 3, 3, 3, 3);
        addModeButton(segment, "本地工作区", ToolSettingsRepository.EXECUTION_LOCAL);
        addModeButton(segment, "SSH Shell", ToolSettingsRepository.EXECUTION_SSH);
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        segmentParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(segment, segmentParams);
        String executionDesc = ToolSettingsRepository.EXECUTION_LOCAL.equals(state.getExecutionMode())
                ? "本地工作区会在当前工作区路径内执行文件读写、文件搜索、Agent、HTTP 服务器、网页搜索、图片理解和图片生成。搜索 API 与图片模型在工具设置中配置。"
                : "SSH Shell 模式会禁用本地文件读写、文件搜索、Agent 和 HTTP 服务器；图片理解通过 SFTP 读取 SSH 图片，网页搜索和图片生成仍由应用侧配置执行。";
        TextView desc = desc(context, executionDesc);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(desc, descParams);
        addCard(content, card);
    }

    private void addSshConnection(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, "SSH 连接"));
        TextView desc = desc(context, "SSH Shell 可以连接远程 Linux 服务器、桌面开发机、NAS，也可以连接手机本机 Termux。Termux 是一个单独选项，不等于只能连接手机。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout ssh = actionButton(context, "SSH 连接设置", IconButtonView.SERVER, true, v -> listener.onOpenSshSettings());
        LinearLayout termux = actionButton(context, "Termux 对接", IconButtonView.SMARTPHONE, false, v -> listener.onOpenTermuxIntegration());
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
            if (!shouldShow(config)) {
                continue;
            }
            addToolCard(content, iconFor(config.getId()), config);
        }
    }

    private boolean shouldShow(McpToolConfig config) {
        if (ToolSettingsRepository.EXECUTION_LOCAL.equals(state.getExecutionMode())) {
            return !"shell".equals(config.getId());
        }
        return "shell".equals(config.getId())
                || "web_search".equals(config.getId())
                || "image_understanding".equals(config.getId())
                || "image_generation".equals(config.getId());
    }

    private int iconFor(String id) {
        if ("file_ops".equals(id) || "http_server".equals(id)) return IconButtonView.MCP;
        if ("shell".equals(id)) return IconButtonView.TERMINAL;
        if ("web_search".equals(id)) return IconButtonView.SEARCH;
        if ("image_understanding".equals(id)) return IconButtonView.PAINTBRUSH;
        if ("image_generation".equals(id)) return IconButtonView.PAINTBRUSH;
        if ("agent".equals(id)) return IconButtonView.BRAIN;
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

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(divider, dividerParams);

        FlowLayoutView toolWrap = new FlowLayoutView(context);
        toolWrap.setSpacingDp(LineTheme.SM, LineTheme.SM);
        for (String tool : config.getTools()) {
            TextView badge = LineTheme.text(context, tool, LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            badge.setTypeface(Typeface.MONOSPACE);
            badge.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 4));
            LineTheme.padding(badge, LineTheme.SM, 2, LineTheme.SM, 2);
            toolWrap.addView(badge);
        }
        LinearLayout.LayoutParams toolWrapParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolWrapParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(toolWrap, toolWrapParams);
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
