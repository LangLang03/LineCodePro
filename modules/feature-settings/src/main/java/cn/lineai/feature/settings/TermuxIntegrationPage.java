package cn.lineai.feature.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Termux integration guide and one-tap OpenSSH setup. */
public final class TermuxIntegrationPage extends SettingsPage {

    public interface Listener {
        void onOpenTermux();

        void onRequestRunCommandPermission();

        void onSetup();
    }

    private final Listener listener;
    private final LinearLayout setupButton;
    private final TextView status;

    public TermuxIntegrationPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_termux_title, tokens, back);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);

        LinearLayout intro = card();
        intro.addView(Ui.bold(context, context.getString(R.string.screen_termux_section_use),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        TextView introDesc = desc(context.getString(R.string.screen_termux_use_desc));
        LinearLayout.LayoutParams introDescParams = new LayoutParams(-1, -2);
        introDescParams.topMargin = Ui.dp(context, 4);
        intro.addView(introDesc, introDescParams);
        addCard(intro);

        LinearLayout steps = card();
        steps.addView(Ui.bold(context, context.getString(R.string.screen_termux_section_steps),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        steps.addView(step("1", context.getString(R.string.screen_termux_step_1)));
        steps.addView(step("2", context.getString(R.string.screen_termux_step_2)));
        steps.addView(step("3", context.getString(R.string.screen_termux_step_3)));
        addCard(steps);

        LinearLayout commandCard = card();
        commandCard.addView(Ui.bold(context, context.getString(R.string.screen_termux_section_intent),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        TextView command = Ui.text(context,
                cn.lineai.core.model.TermuxCommands.ALLOW_EXTERNAL_APPS,
                Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setLineSpacing(Ui.dp(context, 3), 1f);
        command.setBackground(Ui.roundedStroke(context, tokens.codeBg, 8, tokens.codeBorder));
        Ui.padding(command, 12, 12, 12, 12);
        LinearLayout.LayoutParams commandParams = new LayoutParams(-1, -2);
        commandParams.topMargin = Ui.dp(context, 8);
        commandCard.addView(command, commandParams);
        addCard(commandCard);

        LinearLayout actions = card();
        actions.addView(Ui.bold(context, context.getString(R.string.screen_termux_actions_title),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        LinearLayout rowOne = actionRow();
        rowOne.addView(actionButton(R.string.screen_termux_copy_intent, Icons.COPY, false,
                this::copyCommand), actionParams(0));
        rowOne.addView(actionButton(R.string.screen_termux_run_command_perm, Icons.SHIELD_CHECK, false,
                listener::onRequestRunCommandPermission), actionParams(8));
        actions.addView(rowOne, topMarginRow());
        LinearLayout rowTwo = actionRow();
        rowTwo.addView(actionButton(R.string.screen_termux_open_termux, Icons.EXTERNAL_LINK, false,
                listener::onOpenTermux), actionParams(0));
        setupButton = actionButton(R.string.screen_termux_auto_ssh, Icons.DOWNLOAD, true,
                listener::onSetup);
        rowTwo.addView(setupButton, actionParams(8));
        actions.addView(rowTwo, topMarginRow());

        status = Ui.text(context, "", Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        status.setTypeface(Typeface.MONOSPACE);
        status.setVisibility(View.GONE);
        status.setLineSpacing(Ui.dp(context, 3), 1f);
        status.setBackground(Ui.roundedStroke(context, tokens.codeBg, 8, tokens.codeBorder));
        Ui.padding(status, 12, 8, 12, 8);
        LinearLayout.LayoutParams statusParams = new LayoutParams(-1, -2);
        statusParams.topMargin = Ui.dp(context, 8);
        actions.addView(status, statusParams);
        addCard(actions);
    }

    public void setSetupRunning(boolean running) {
        setupButton.setEnabled(!running);
        setupButton.setAlpha(running ? 0.65f : 1f);
    }

    public void showStatus(CharSequence title, CharSequence message, boolean error) {
        status.setVisibility(View.VISIBLE);
        status.setText(getContext().getString(R.string.status_title_message, title, message));
        Tokens tokens = theme.current();
        status.setTextColor(error ? tokens.danger : tokens.textSecondary);
        status.setBackground(Ui.roundedStroke(getContext(), error ? tokens.dangerMuted : tokens.codeBg,
                8, error ? tokens.danger : tokens.codeBorder));
    }

    private void copyCommand() {
        ClipboardManager clipboard = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getContext().getString(R.string.screen_termux_clip_label),
                    cn.lineai.core.model.TermuxCommands.ALLOW_EXTERNAL_APPS));
        }
        showStatus(getContext().getString(R.string.screen_termux_status_copied_title),
                getContext().getString(R.string.screen_termux_status_copied_message), false);
    }

    private LinearLayout step(String number, String text) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(context, 8);
        row.setLayoutParams(params);
        TextView badge = Ui.bold(context, number, Tokens.FONT_XS, tokens.textOnColor);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(context, tokens.accent, 12));
        row.addView(badge, new LayoutParams(Ui.dp(context, 24), Ui.dp(context, 24)));
        TextView label = desc(text);
        LinearLayout.LayoutParams labelParams = new LayoutParams(0, -2, 1f);
        labelParams.leftMargin = Ui.dp(context, 8);
        row.addView(label, labelParams);
        return row;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams actionParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 38), 1f);
        params.leftMargin = Ui.dp(getContext(), leftMargin);
        return params;
    }

    private LinearLayout.LayoutParams topMarginRow() {
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 8);
        return params;
    }

    private LinearLayout actionButton(int label, int icon, boolean primary, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, primary ? tokens.accent : tokens.surfaceLight,
                8, primary ? tokens.accent : tokens.borderLight));
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

    private LinearLayout card() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 12);
        content.addView(card, params);
    }

    private TextView desc(String text) {
        TextView view = Ui.text(getContext(), text, Tokens.FONT_XS, theme.current().textTertiary,
                Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        return view;
    }
}
