package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/** Execution target, SSH shortcuts and per-group tool switches. */
public final class McpSettingsPage extends SettingsPage {

    public interface Listener {
        void onOpenSshSettings();

        void onOpenTermuxIntegration();
    }

    private final McpPreferences preferences;
    private final Listener listener;
    private final LinearLayout cards;
    private String mode;

    public McpSettingsPage(Context context, Tokens tokens, Runnable back, McpPreferences preferences,
                           Listener listener) {
        super(context, R.string.screen_mcp_title, tokens, back);
        this.preferences = preferences;
        this.listener = listener;
        this.mode = preferences.mode();
        Ui.padding(content, 16, 16, 16, 100);
        cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.VERTICAL);
        content.addView(cards, new LayoutParams(-1, -2));
        rebuild();
    }

    private void rebuild() {
        cards.removeAllViews();
        executionCard();
        if (McpPreferences.MODE_SSH.equals(mode)) sshCard();
        for (ToolGroups.Group group : ToolGroups.all()) {
            if (group.visibleIn(mode)) toolCard(group);
        }
    }

    private void executionCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card(context, tokens);
        card.addView(Ui.bold(context, context.getString(R.string.screen_mcp_section_execution),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        LinearLayout segment = new LinearLayout(context);
        segment.setBackground(Ui.rounded(context, tokens.surfaceLight, 8));
        Ui.padding(segment, 3, 3, 3, 3);
        modeButton(segment, R.string.screen_mcp_execution_local, McpPreferences.MODE_LOCAL);
        modeButton(segment, R.string.screen_mcp_execution_ssh, McpPreferences.MODE_SSH);
        modeButton(segment, R.string.screen_mcp_execution_terminal_provider,
                McpPreferences.MODE_TERMINAL_PROVIDER);
        LinearLayout.LayoutParams segmentParams = new LayoutParams(-1, Ui.dp(context, 42));
        segmentParams.topMargin = Ui.dp(context, 8);
        card.addView(segment, segmentParams);
        int description = McpPreferences.MODE_LOCAL.equals(mode)
                ? R.string.screen_mcp_execution_local_desc
                : McpPreferences.MODE_TERMINAL_PROVIDER.equals(mode)
                ? R.string.screen_mcp_execution_terminal_provider_desc
                : R.string.screen_mcp_execution_ssh_desc;
        TextView desc = desc(context, tokens, context.getString(description));
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 8);
        card.addView(desc, descParams);
        addCard(card);
    }

    private void modeButton(LinearLayout segment, int label, String value) {
        Context context = getContext();
        Tokens tokens = theme.current();
        boolean active = value.equals(mode);
        TextView button = Ui.bold(context, context.getString(label), Tokens.FONT_SM,
                active ? tokens.textOnColor : tokens.textSecondary);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.rounded(context, active ? tokens.accent : android.graphics.Color.TRANSPARENT, 8));
        button.setOnClickListener(v -> {
            if (value.equals(mode)) return;
            mode = value;
            preferences.setMode(value);
            rebuild();
        });
        segment.addView(button, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private void sshCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card(context, tokens);
        card.addView(Ui.bold(context, context.getString(R.string.screen_mcp_section_ssh),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        TextView desc = desc(context, tokens, context.getString(R.string.screen_mcp_ssh_overview));
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        card.addView(desc, descParams);
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ssh = actionButton(context, tokens, R.string.screen_mcp_ssh_settings,
                Icons.SERVER, true, listener::onOpenSshSettings);
        LinearLayout termux = actionButton(context, tokens, R.string.screen_mcp_termux_integration,
                Icons.SMARTPHONE, false, listener::onOpenTermuxIntegration);
        LinearLayout.LayoutParams sshParams = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f);
        sshParams.rightMargin = Ui.dp(context, 8);
        actions.addView(ssh, sshParams);
        actions.addView(termux, new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(context, 12);
        card.addView(actions, actionsParams);
        addCard(card);
    }

    private void toolCard(ToolGroups.Group group) {
        Context context = getContext();
        Tokens tokens = theme.current();
        boolean enabled = preferences.enabled(group.id, group.enabledByDefault);
        LinearLayout card = card(context, tokens);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        IconButton icon = new IconButton(context, group.icon);
        icon.setIconColor(enabled ? tokens.accent : tokens.textTertiary);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        row.addView(iconBox, new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LayoutParams(0, -2, 1f);
        labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 12);
        row.addView(labels, labelParams);
        labels.addView(Ui.medium(context, context.getString(group.name), Tokens.FONT_MD, tokens.text),
                new LayoutParams(-1, -2));
        TextView desc = desc(context, tokens, context.getString(group.description));
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(desc, descParams);
        android.widget.Switch toggle = new android.widget.Switch(context);
        tint(toggle, tokens);
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.setEnabled(group.id, checked);
            icon.setIconColor(checked ? tokens.accent : tokens.textTertiary);
        });
        row.addView(toggle, new LayoutParams(-2, -2));
        card.addView(row, new LayoutParams(-1, -2));
        addCard(card);
    }

    private LinearLayout card(Context context, Tokens tokens) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void addCard(View card) {
        Context context = getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(context, 12);
        cards.addView(card, params);
    }

    private LinearLayout actionButton(Context context, Tokens tokens, int label, int icon,
                                      boolean primary, Runnable action) {
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context,
                primary ? tokens.accent : tokens.surfaceLight, 8,
                primary ? tokens.accent : tokens.borderLight));
        button.setOnClickListener(v -> action.run());
        Ui.padding(button, 8, 0, 8, 0);
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(primary ? tokens.textOnColor : tokens.textSecondary);
        glyph.setIconSizeDp(15, 15);
        glyph.setClickable(false);
        button.addView(glyph, new LayoutParams(Ui.dp(context, 15), Ui.dp(context, 15)));
        TextView text = Ui.bold(context, context.getString(label), Tokens.FONT_XS,
                primary ? tokens.textOnColor : tokens.textSecondary);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LayoutParams(-2, -2);
        textParams.leftMargin = Ui.dp(context, 6);
        button.addView(text, textParams);
        return button;
    }

    private static void tint(android.widget.Switch toggle, Tokens tokens) {
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[] {tokens.accentDim, tokens.surfaceLight}));
    }

    private TextView desc(Context context, Tokens tokens, String text) {
        TextView view = Ui.text(context, text, Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(context, 3), 1f);
        return view;
    }
}
