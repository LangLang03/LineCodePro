package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.SshConfig;
import cn.lineai.ssh.SshService;
import cn.lineai.ui.theme.LineTheme;

public final class SshSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onOpenTermuxIntegration();
    }

    private final SshService sshService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final FormTextFieldView hostField;
    private final FormTextFieldView portField;
    private final FormTextFieldView usernameField;
    private final FormTextFieldView passwordField;
    private final FormTextFieldView privateKeyField;
    private final FormTextFieldView passphraseField;
    private final TextView statusView;
    private final LinearLayout testButton;

    public SshSettingsScreenView(Context context, Listener listener) {
        super(context, "SSH 连接", listener::onBack, null);
        sshService = new SshService(context);
        SshConfig config = sshService.getConfig();

        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout intro = card(context);
        intro.addView(title(context, "远程 Linux / 服务器"));
        TextView desc = desc(context, "SSH Shell 可以连接桌面开发机、云服务器、NAS 或 Termux。远程服务器填写真实 Host 和端口；Termux 请走单独的“Termux 对接”页面自动配置。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        intro.addView(desc, descParams);
        LinearLayout openTermux = button(context, "Termux 对接", IconButtonView.SMARTPHONE, false, v -> listener.onOpenTermuxIntegration());
        LinearLayout.LayoutParams termuxParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        termuxParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        intro.addView(openTermux, termuxParams);
        addCard(content, intro);

        LinearLayout form = card(context);
        form.addView(title(context, "连接配置"));
        hostField = new FormTextFieldView(context, "Host", config.getHost(), "服务器 IP 或域名", null, false, false);
        portField = new FormTextFieldView(context, "Port", String.valueOf(config.getPort()), "22 或 8022", null, false, false);
        portField.getInput().setInputType(InputType.TYPE_CLASS_NUMBER);
        usernameField = new FormTextFieldView(context, "Username", config.getUsername(), "用户名", null, false, false);
        passwordField = new FormTextFieldView(context, "Password (可选)", config.getPassword(), "密码登录时填写", null, false, true);
        privateKeyField = new FormTextFieldView(context, "Private key (无密码登录)", config.getPrivateKey(), "-----BEGIN OPENSSH PRIVATE KEY-----", null, true, false);
        passphraseField = new FormTextFieldView(context, "Key passphrase (可选)", config.getPassphrase(), "私钥口令", null, false, true);
        form.addView(hostField, formParams(context));
        form.addView(portField, formParams(context));
        form.addView(usernameField, formParams(context));
        form.addView(passwordField, formParams(context));
        form.addView(privateKeyField, formParams(context));
        form.addView(passphraseField, formParams(context));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout saveButton = button(context, "保存配置", IconButtonView.SAVE, false, v -> {
            sshService.saveConfig(readConfig());
            setStatus("已保存", "SSH 配置已保存。", false);
        });
        testButton = button(context, "测试连接", IconButtonView.TERMINAL, true, v -> testConnection());
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
        setTesting(true);
        setStatus("连接中", "正在测试 SSH 连接...", false);
        SshConfig config = readConfig();
        sshService.saveConfig(config);
        new Thread(() -> {
            try {
                String output = sshService.testConnection(config);
                mainHandler.post(() -> {
                    setTesting(false);
                    setStatus("连接成功", output.trim().length() == 0 ? "连接正常" : output.trim(), false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setTesting(false);
                    setStatus("连接失败", e.getMessage(), true);
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
