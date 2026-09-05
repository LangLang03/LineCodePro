package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import cn.lineai.R;
import cn.lineai.model.SshConfig;

public final class SshSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onOpenTermuxIntegration();
        SshConfig onLoadConfig();
        void onSaveConfig(SshConfig config);
        String onTestConnection(SshConfig config) throws Exception;
    }

    private final Listener listener;
    private final FormTextFieldView hostField;
    private final FormTextFieldView portField;
    private final FormTextFieldView usernameField;
    private final FormTextFieldView passwordField;
    private final FormTextFieldView privateKeyField;
    private final FormTextFieldView passphraseField;
    private final TextView statusView;
    private final LinearLayout testButton;

    public SshSettingsScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_ssh_title), listener::onBack, null);
        this.listener = listener;
        SshConfig config = listener.onLoadConfig();

        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout intro = card(context);
        intro.addView(title(context, context.getString(R.string.screen_ssh_section_server)));
        TextView desc = desc(context, context.getString(R.string.screen_ssh_server_desc));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        intro.addView(desc, descParams);
        LinearLayout openTermux = button(context, context.getString(R.string.screen_ssh_termux), IconButtonView.SMARTPHONE, false, v -> listener.onOpenTermuxIntegration());
        LinearLayout.LayoutParams termuxParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        termuxParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        intro.addView(openTermux, termuxParams);
        addCard(content, intro);

        LinearLayout form = card(context);
        form.addView(title(context, context.getString(R.string.screen_ssh_form_title)));
        hostField = new FormTextFieldView(context, context.getString(R.string.screen_ssh_field_host), config.getHost(), context.getString(R.string.screen_ssh_hint_host), null, false, false);
        portField = new FormTextFieldView(context, context.getString(R.string.screen_ssh_field_port), String.valueOf(config.getPort()), context.getString(R.string.screen_ssh_hint_port), null, false, false);
        portField.getInput().setInputType(InputType.TYPE_CLASS_NUMBER);
        usernameField = new FormTextFieldView(context, context.getString(R.string.screen_ssh_field_username), config.getUsername(), context.getString(R.string.screen_ssh_hint_username), null, false, false);
        passwordField = new FormTextFieldView(context, context.getString(R.string.screen_ssh_field_password_optional), config.getPassword(), context.getString(R.string.screen_ssh_hint_password), null, false, true);
        privateKeyField = new FormTextFieldView(context, context.getString(R.string.screen_ssh_field_private_key), config.getPrivateKey(), context.getString(R.string.screen_ssh_hint_private_key), null, true, false);
        passphraseField = new FormTextFieldView(context, context.getString(R.string.screen_ssh_field_key_passphrase_optional), config.getPassphrase(), context.getString(R.string.screen_ssh_hint_passphrase), null, false, true);
        form.addView(hostField, formParams(context));
        form.addView(portField, formParams(context));
        form.addView(usernameField, formParams(context));
        form.addView(passwordField, formParams(context));
        DisclosureSectionView keys = new DisclosureSectionView(context, context.getString(R.string.screen_ssh_field_private_key), !config.getPrivateKey().isEmpty());
        keys.getBody().addView(privateKeyField, formParams(context));
        keys.getBody().addView(passphraseField, formParams(context));
        form.addView(keys, formParams(context));

        LinearLayout actions = new AdaptiveActionsView(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout saveButton = button(context, context.getString(R.string.screen_ssh_save), IconButtonView.SAVE, false, v -> {
            listener.onSaveConfig(readConfig());
            setStatus(context.getString(R.string.screen_ssh_status_saved_title), context.getString(R.string.screen_ssh_status_saved_message), false);
        });
        testButton = button(context, context.getString(R.string.screen_ssh_test), IconButtonView.TERMINAL, true, v -> testConnection());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f);
        saveParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        actions.addView(saveButton, saveParams);
        actions.addView(testButton, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        form.addView(actions, actionsParams);

        statusView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setVisibility(GONE);
        statusView.setLineSpacing(LineTheme.dp(context, 3), 1f);
        statusView.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(statusView, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        form.addView(statusView, statusParams);

        addCard(content, form);
    }

    private void testConnection() {
        Context context = getContext();
        setTesting(true);
        setStatus(context.getString(R.string.screen_ssh_status_testing_title), context.getString(R.string.screen_ssh_status_testing_message), false);
        SshConfig config = readConfig();
        listener.onSaveConfig(config);
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String output = listener.onTestConnection(config);
                handler.post(() -> {
                    setTesting(false);
                    setStatus(context.getString(R.string.screen_ssh_status_success_title), output.trim().length() == 0 ? context.getString(R.string.screen_ssh_status_success_message) : output.trim(), false);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setTesting(false);
                    setStatus(context.getString(R.string.screen_ssh_status_failed_title), describeException(e), true);
                });
            }
        }, "linecode-ssh-test").start();
    }

    private SshConfig readConfig() {
        return new SshConfig(
                hostField.getInput().getText().toString(),
                parsePort(portField.getInput().getText().toString()),
                usernameField.getInput().getText().toString(),
                passwordField.getInput().getText().toString(),
                privateKeyField.getInput().getText().toString(),
                passphraseField.getInput().getText().toString()
        );
    }

    private int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            return port > 0 ? port : SshConfig.DEFAULT_PORT;
        } catch (Exception ignored) {
            return SshConfig.DEFAULT_PORT;
        }
    }

    private void setTesting(boolean testing) {
        testButton.setEnabled(!testing);
        testButton.setAlpha(testing ? 0.65f : 1f);
    }

    private void setStatus(String title, String message, boolean error) {
        statusView.setVisibility(VISIBLE);
        statusView.setText(getResources().getString(
                R.string.status_title_message,
                title == null ? "" : title,
                message == null ? "" : message
        ));
        statusView.setTextColor(error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        statusView.setBackground(LineTheme.roundedStroke(
                getContext(),
                error ? LineTheme.DANGER_MUTED : LineTheme.CODE_BG,
                8,
                error ? LineTheme.DANGER : LineTheme.CODE_BORDER
        ));
    }

    private String describeException(Exception error) {
        if (error == null) {
            return getString(R.string.common_unknown_error);
        }
        String message = error.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message.trim();
        }
        String name = error.getClass().getSimpleName();
        return name.length() == 0 ? getString(R.string.common_unknown_error) : name;
    }

    private LinearLayout button(Context context, String label, int iconType, boolean primary, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(LineTheme.roundedStroke(context, primary ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, primary ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setOnClickListener(listener);
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        icon.setIconSizeDp(16, 16);
        icon.setClickable(false);
        button.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        TextView text = LineTheme.text(context, label, LineTheme.FONT_SM, primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.XS);
        button.addView(text, textParams);
        return button;
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(card, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return card;
    }

    private TextView title(Context context, String text) {
        return LineTheme.text(context, text, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
    }

    private TextView desc(Context context, String text) {
        TextView view = LineTheme.text(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setLineSpacing(LineTheme.dp(context, 3), 1f);
        return view;
    }

    private LinearLayout.LayoutParams formParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.MD);
        return params;
    }

    private void addCard(LinearLayout content, LinearLayout card) {
        Context context = content.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(card, params);
    }
}
