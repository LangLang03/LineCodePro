package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import cn.lineai.core.model.SshConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** SSH server form with save and connection test. */
public final class SshSettingsPage extends SettingsPage implements Refreshable {

    public interface Listener {
        SshConfig load();

        void save(SshConfig config);

        void test(SshConfig config);

        void onOpenTermux();
    }

    private final Listener listener;
    private final FormField host;
    private final FormField port;
    private final FormField username;
    private final FormField password;
    private final FormField privateKey;
    private final FormField passphrase;
    private final LinearLayout testButton;
    private final TextView status;

    public SshSettingsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_ssh_title, tokens, back);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        SshConfig config = listener.load();

        LinearLayout intro = card();
        intro.addView(Ui.bold(context, context.getString(R.string.screen_ssh_section_server),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        TextView introDesc = desc(context.getString(R.string.screen_ssh_server_desc));
        LinearLayout.LayoutParams introDescParams = new LayoutParams(-1, -2);
        introDescParams.topMargin = Ui.dp(context, 4);
        intro.addView(introDesc, introDescParams);
        LinearLayout termux = actionButton(R.string.screen_ssh_termux, Icons.SMARTPHONE, false,
                listener::onOpenTermux);
        LinearLayout.LayoutParams termuxParams = new LayoutParams(-1, Ui.dp(context, 42));
        termuxParams.topMargin = Ui.dp(context, 12);
        intro.addView(termux, termuxParams);
        addCard(intro);

        LinearLayout form = card();
        form.addView(Ui.bold(context, context.getString(R.string.screen_ssh_form_title),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        host = field(R.string.screen_ssh_field_host, config.host, R.string.screen_ssh_hint_host,
                false, false);
        port = field(R.string.screen_ssh_field_port, String.valueOf(config.port),
                R.string.screen_ssh_hint_port, false, false);
        port.input().setInputType(InputType.TYPE_CLASS_NUMBER);
        username = field(R.string.screen_ssh_field_username, config.username,
                R.string.screen_ssh_hint_username, false, false);
        password = field(R.string.screen_ssh_field_password_optional, config.password,
                R.string.screen_ssh_hint_password, true, false);
        privateKey = field(R.string.screen_ssh_field_private_key, config.privateKey,
                R.string.screen_ssh_hint_private_key, false, true);
        passphrase = field(R.string.screen_ssh_field_key_passphrase_optional, config.passphrase,
                R.string.screen_ssh_hint_passphrase, true, false);
        for (FormField field : new FormField[] {host, port, username, password, privateKey, passphrase}) {
            form.addView(field, formParams());
        }

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout save = actionButton(R.string.screen_ssh_save, Icons.SAVE, false, () -> {
            listener.save(read());
            showStatus(context.getString(R.string.screen_ssh_status_saved_title),
                    context.getString(R.string.screen_ssh_status_saved_message), false);
        });
        testButton = actionButton(R.string.screen_ssh_test, Icons.TERMINAL, true, () -> {
            listener.save(read());
            setTesting(true);
            showStatus(context.getString(R.string.screen_ssh_status_testing_title),
                    context.getString(R.string.screen_ssh_status_testing_message), false);
            listener.test(read());
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f);
        saveParams.rightMargin = Ui.dp(context, 8);
        actions.addView(save, saveParams);
        actions.addView(testButton, new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(context, 12);
        form.addView(actions, actionsParams);

        status = Ui.text(context, "", Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        status.setTypeface(Typeface.MONOSPACE);
        status.setVisibility(View.GONE);
        status.setLineSpacing(Ui.dp(context, 3), 1f);
        status.setBackground(Ui.roundedStroke(context, tokens.codeBg, 8, tokens.codeBorder));
        Ui.padding(status, 12, 8, 12, 8);
        LinearLayout.LayoutParams statusParams = new LayoutParams(-1, -2);
        statusParams.topMargin = Ui.dp(context, 8);
        form.addView(status, statusParams);
        addCard(form);
    }

    private FormField field(int label, String value, int hint, boolean secure, boolean multiline) {
        FormField field = new FormField(getContext(), theme, label, value,
                getContext().getString(hint), secure, multiline);
        theme.bind(t -> field.setVisibility(View.VISIBLE));
        return field;
    }

    /**
     * Re-reads the persisted server settings into the form.
     *
     * <p>The one-tap Termux setup writes host/port/username/private key directly to the settings
     * store; this page may already be in the back stack, so re-showing it must show the fresh
     * credentials instead of the stale (usually empty) form.</p>
     */
    @Override public void onShown() {
        SshConfig stored = listener.load();
        if (matches(stored)) {
            return;
        }
        host.input().setText(stored.host);
        port.input().setText(String.valueOf(stored.port));
        username.input().setText(stored.username);
        password.input().setText(stored.password);
        privateKey.input().setText(stored.privateKey);
        passphrase.input().setText(stored.passphrase);
        status.setVisibility(View.GONE);
    }

    private boolean matches(SshConfig stored) {
        return stored.host.equals(host.value())
                && String.valueOf(stored.port).equals(port.value())
                && stored.username.equals(username.value())
                && stored.password.equals(password.value())
                && stored.privateKey.equals(privateKey.value())
                && stored.passphrase.equals(passphrase.value());
    }

    public void setTesting(boolean testing) {
        testButton.setEnabled(!testing);
        testButton.setAlpha(testing ? 0.65f : 1f);
    }

    public void showStatus(CharSequence title, CharSequence message, boolean error) {
        status.setVisibility(View.VISIBLE);
        status.setText(getContext().getString(R.string.status_title_message, title, message));
        Tokens tokens = theme.current();
        status.setTextColor(error ? tokens.danger : tokens.textSecondary);
        status.setBackground(Ui.roundedStroke(getContext(),
                error ? tokens.dangerMuted : tokens.codeBg, 8,
                error ? tokens.danger : tokens.codeBorder));
    }

    private SshConfig read() {
        return new SshConfig(host.value(), parsePort(port.value()), username.value(),
                password.value(), privateKey.value(), passphrase.value());
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            return port > 0 ? port : SshConfig.DEFAULT_PORT;
        } catch (NumberFormatException error) {
            return SshConfig.DEFAULT_PORT;
        }
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

    private LinearLayout actionButton(int label, int icon, boolean primary, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, primary ? tokens.accent : tokens.surfaceLight,
                8, primary ? tokens.accent : tokens.borderLight));
        button.setOnClickListener(v -> action.run());
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(primary ? tokens.textOnColor : tokens.textSecondary);
        glyph.setIconSizeDp(16, 16);
        glyph.setClickable(false);
        button.addView(glyph, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        TextView text = Ui.bold(context, context.getString(label), Tokens.FONT_SM,
                primary ? tokens.textOnColor : tokens.textSecondary);
        LinearLayout.LayoutParams textParams = new LayoutParams(-2, -2);
        textParams.leftMargin = Ui.dp(context, 4);
        button.addView(text, textParams);
        return button;
    }

    private TextView desc(String text) {
        TextView view = Ui.text(getContext(), text, Tokens.FONT_XS, theme.current().textTertiary,
                Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        return view;
    }

    private LinearLayout.LayoutParams formParams() {
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 12);
        return params;
    }
}
