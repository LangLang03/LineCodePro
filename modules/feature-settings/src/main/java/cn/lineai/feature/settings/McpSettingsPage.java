package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * MCP screen: execution target, provider status, and per-group tool switches.
 *
 * <p>Tool groups render as switches backed by {@link McpPreferences}; the page never enumerates
 * individual tools, so runtime behavior stays decoupled from tool names.</p>
 */
public final class McpSettingsPage extends SettingsPage {

    /** Host callbacks for navigation and provider operations. */
    public interface Listener {
        /** Opens the SSH connection settings screen. */
        void onOpenSshSettings();

        /** Opens the Termux integration screen. */
        void onOpenTermuxIntegration();

        /** Opens the terminal providers management screen. */
        void onOpenTerminalProviders();

        /** Notifies the host that the execution target changed so dependent state can refresh. */
        void onExecutionModeChanged();

        /**
         * Returns the current terminal provider connection status.
         *
         * @return provider status; never null
         */
        ProviderStatus terminalProviderStatus();

        /** Starts an asynchronous terminal provider connectivity probe. */
        void onTestTerminalProvider();
    }

    /** Connection lifecycle of the terminal provider execution target. */
    public enum ProviderState {
        NONE, DISCONNECTED, CONNECTED
    }

    /** Immutable snapshot of the terminal provider status shown on the page. */
    public static final class ProviderStatus {
        /** Connection state of the provider. */
        public final ProviderState state;
        /** Display name of the connected provider; empty when unknown. */
        public final String name;

        /**
         * Creates a status snapshot, normalizing null fields.
         *
         * @param state provider state; null becomes {@link ProviderState#NONE}
         * @param name provider display name; null becomes an empty string
         */
        public ProviderStatus(ProviderState state, String name) {
            this.state = state == null ? ProviderState.NONE : state;
            this.name = name == null ? "" : name;
        }
    }

    private final McpPreferences preferences;
    private final Listener listener;
    private final LinearLayout cards;
    private String mode;
    private TextView providerTestTitle;
    private TextView providerTestStatus;

    /**
     * Builds the MCP settings page.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param preferences store-backed MCP switches read and written by this page
     * @param listener host callbacks for navigation and provider operations
     */
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

    /** Rebuilds all cards for the current execution target, showing only applicable groups. */
    private void rebuild() {
        cards.removeAllViews();
        providerTestTitle = null;
        providerTestStatus = null;
        executionCard();
        if (McpPreferences.MODE_SSH.equals(mode)) {
            sshCard();
        } else if (McpPreferences.MODE_TERMINAL_PROVIDER.equals(mode)) {
            terminalProviderCard();
        }
        for (ToolGroups.Group group : ToolGroups.all()) {
            if (group.visibleIn(mode)) toolCard(group);
        }
    }

    /** Adds the segmented control that selects the MCP execution target. */
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

    /** Adds one segment button that persists the new execution target and rebuilds the page. */
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
            listener.onExecutionModeChanged();
        });
        segment.addView(button, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    /** Adds the SSH section with links to SSH settings and Termux integration. */
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
        actionsParams.topMargin = Ui.dp(context, 8);
        card.addView(actions, actionsParams);
        addCard(card);
    }

    /** Adds the terminal provider section with status, manage/test actions, and a probe output area. */
    private void terminalProviderCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card(context, tokens);
        card.addView(Ui.bold(context,
                context.getString(R.string.screen_mcp_section_terminal_provider),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        TextView desc = desc(context, tokens,
                context.getString(R.string.screen_mcp_terminal_provider_overview));
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        card.addView(desc, descParams);

        ProviderStatus status = listener.terminalProviderStatus();
        TextView state = desc(context, tokens, providerStatusText(status));
        state.setTextColor(status.state == ProviderState.CONNECTED
                ? tokens.accent : tokens.textSecondary);
        LinearLayout.LayoutParams stateParams = new LayoutParams(-1, -2);
        stateParams.topMargin = Ui.dp(context, 8);
        card.addView(state, stateParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout manage = actionButton(context, tokens,
                R.string.screen_mcp_terminal_provider_manage, Icons.TERMINAL, true,
                listener::onOpenTerminalProviders);
        LinearLayout test = actionButton(context, tokens,
                R.string.screen_mcp_terminal_provider_test, Icons.SERVER, false,
                this::requestTerminalProviderTest);
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f);
        manageParams.rightMargin = Ui.dp(context, 8);
        actions.addView(manage, manageParams);
        actions.addView(test, new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(context, 10);
        card.addView(actions, actionsParams);

        providerTestTitle = Ui.bold(context, "", Tokens.FONT_XS, tokens.textSecondary);
        providerTestTitle.setVisibility(View.GONE);
        providerTestStatus = Ui.text(context, "", Tokens.FONT_XS, tokens.textSecondary,
                Typeface.NORMAL);
        providerTestStatus.setTypeface(Typeface.MONOSPACE);
        providerTestStatus.setVisibility(View.GONE);
        providerTestStatus.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams titleParams = new LayoutParams(-1, -2);
        titleParams.topMargin = Ui.dp(context, 10);
        card.addView(providerTestTitle, titleParams);
        LinearLayout.LayoutParams testParams = new LayoutParams(-1, -2);
        testParams.topMargin = Ui.dp(context, 4);
        card.addView(providerTestStatus, testParams);
        addCard(card);
    }

    /** Shows the in-progress probe state and asks the host to start the connectivity probe. */
    private void requestTerminalProviderTest() {
        showProviderTestStatus(R.string.screen_mcp_terminal_provider_testing_title,
                getContext().getString(R.string.screen_mcp_terminal_provider_testing_message),
                false);
        listener.onTestTerminalProvider();
    }

    /** Called by the runtime after a provider probe finishes. */
    public void showTerminalProviderResult(CharSequence message, boolean error) {
        showProviderTestStatus(error
                        ? R.string.screen_mcp_terminal_provider_test_failed_title
                        : R.string.screen_mcp_terminal_provider_test_success_title,
                message, error);
    }

    /** Renders the probe result area; ignored when the provider card is not on screen. */
    private void showProviderTestStatus(int title, CharSequence message, boolean error) {
        if (providerTestTitle == null || providerTestStatus == null) {
            return;
        }
        Tokens tokens = theme.current();
        providerTestTitle.setText(title);
        providerTestTitle.setTextColor(error ? tokens.danger : tokens.accent);
        providerTestTitle.setVisibility(View.VISIBLE);
        providerTestStatus.setText(message);
        providerTestStatus.setTextColor(error ? tokens.danger : tokens.textSecondary);
        providerTestStatus.setBackground(Ui.roundedStroke(getContext(),
                error ? tokens.dangerMuted : tokens.codeBg, 8,
                error ? tokens.danger : tokens.codeBorder));
        Ui.padding(providerTestStatus, 12, 8, 12, 8);
        providerTestStatus.setVisibility(View.VISIBLE);
    }

    /** Maps a provider status snapshot onto localized status text. */
    private String providerStatusText(ProviderStatus status) {
        if (status == null || status.state == ProviderState.NONE) {
            return getContext().getString(R.string.screen_mcp_terminal_provider_status_none);
        }
        if (status.state == ProviderState.DISCONNECTED) {
            return getContext().getString(R.string.screen_mcp_terminal_provider_status_disconnected);
        }
        String name = status.name.isEmpty()
                ? getContext().getString(R.string.screen_mcp_terminal_provider_manage)
                : status.name;
        return getContext().getString(R.string.screen_mcp_terminal_provider_status_connected, name);
    }

    /** Adds one tool group card with a switch persisted per execution target. */
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
            icon.setIconColor(checked ? theme.current().accent : theme.current().textTertiary);
        });
        row.addView(toggle, new LayoutParams(-2, -2));
        card.addView(row, new LayoutParams(-1, -2));
        addCard(card);
    }

    /** Creates a rounded card container that re-tints on theme changes. */
    private LinearLayout card(Context context, Tokens tokens) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    /** Appends a card to the page with the standard bottom spacing. */
    private void addCard(View card) {
        Context context = getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(context, 12);
        cards.addView(card, params);
    }

    /** Builds a compact icon-and-label button in primary or secondary styling. */
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

    /** Applies accent-tinted color state lists to a switch thumb and track. */
    private static void tint(android.widget.Switch toggle, Tokens tokens) {
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[] {tokens.accentDim, tokens.surfaceLight}));
    }

    /** Creates the small secondary description text used inside cards. */
    private TextView desc(Context context, Tokens tokens, String text) {
        TextView view = Ui.text(context, text, Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(context, 3), 1f);
        return view;
    }
}
