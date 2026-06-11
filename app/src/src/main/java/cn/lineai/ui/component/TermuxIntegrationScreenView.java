package cn.lineai.ui.component;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.ssh.SshService;
import cn.lineai.ui.theme.LineTheme;
import java.util.regex.Pattern;

public final class TermuxIntegrationScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    private static final int REQUEST_TERMUX_RUN_COMMAND = 7104;
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "LINEAI_PRIVATE_KEY_BEGIN[\\s\\S]*?LINEAI_PRIVATE_KEY_END"
    );

    private final SshService sshService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextView statusView;
    private final LinearLayout setupButton;

    public TermuxIntegrationScreenView(Context context, Listener listener) {
        super(context, "Termux 对接", listener::onBack, null);
        sshService = new SshService(context);

        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout intro = card(context);
        intro.addView(title(context, "把 Termux 作为 SSH Shell"));
        TextView desc = desc(context, "Termux 是手机本机 Linux 环境。这里会通过 Termux RUN_COMMAND 自动安装 openssh、生成 LineCode 专用密钥、写入 authorized_keys 并启动 sshd。远程 Linux 服务器请回到 SSH 连接页填写服务器地址。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        intro.addView(desc, descParams);
        addCard(content, intro);

        LinearLayout steps = card(context);
        steps.addView(title(context, "对接步骤"));
        steps.addView(step(context, "1", "复制授权指令，到 Termux 粘贴执行。"));
        steps.addView(step(context, "2", "授权 LineCode 的 Termux RUN_COMMAND 权限。"));
        steps.addView(step(context, "3", "点击自动配置 OpenSSH，完成后会保存 SSH 配置。"));
        addCard(content, steps);

        LinearLayout commandCard = card(context);
        commandCard.addView(title(context, "Termux intent 授权指令"));
        TextView command = LineTheme.text(context, SshService.TERMUX_ALLOW_EXTERNAL_APPS_COMMAND, LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setLineSpacing(LineTheme.dp(context, 3), 1f);
        command.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(command, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        commandParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        commandCard.addView(command, commandParams);
        addCard(content, commandCard);

        LinearLayout actions = card(context);
        actions.addView(title(context, "操作"));
        GridLikeActions actionGrid = new GridLikeActions(context);
        actionGrid.addAction(button(context, "复制授权指令", IconButtonView.COPY, false, v -> copyCommand()));
        actionGrid.addAction(button(context, "RUN_COMMAND 权限", IconButtonView.SHIELD_CHECK, false, v -> requestRunCommandPermission()));
        actionGrid.addAction(button(context, "打开 Termux", IconButtonView.EXTERNAL_LINK, false, v -> openTermux()));
        setupButton = button(context, "自动配置 OpenSSH", IconButtonView.DOWNLOAD, true, v -> setupOpenSsh());
        actionGrid.addAction(setupButton);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        actions.addView(actionGrid, gridParams);

        statusView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setVisibility(GONE);
        statusView.setLineSpacing(LineTheme.dp(context, 3), 1f);
        statusView.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(statusView, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        actions.addView(statusView, statusParams);
        addCard(content, actions);
    }

    private void copyCommand() {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Termux allow external apps", SshService.TERMUX_ALLOW_EXTERNAL_APPS_COMMAND));
        }
        setStatus("已复制", "打开 Termux 粘贴执行授权指令，然后回来继续授权 RUN_COMMAND。", false);
    }

    private void requestRunCommandPermission() {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            setStatus("无法授权", "当前 Context 不是 Activity，请从应用主界面打开此页面。", true);
            return;
        }
        ((Activity) context).requestPermissions(new String[] {SshService.TERMUX_RUN_COMMAND_PERMISSION}, REQUEST_TERMUX_RUN_COMMAND);
        setStatus("已请求授权", "如果系统没有弹窗，请到应用权限里允许“Run commands in Termux environment”。", false);
    }

    private void openTermux() {
        try {
            sshService.openTermux();
            setStatus("已打开 Termux", "请确认已执行授权指令，并保持 Termux 可运行。", false);
        } catch (Exception e) {
            setStatus("打开失败", e.getMessage(), true);
        }
    }

    private void setupOpenSsh() {
        setSetupRunning(true);
        setStatus("配置中", "正在通过 Termux RUN_COMMAND 安装 openssh、生成密钥并启动 sshd，首次运行可能需要较长时间。", false);
        new Thread(() -> {
            try {
                SshService.TermuxSetupResult setup = sshService.setupTermuxOpenSsh(15 * 60 * 1000);
                String testOutput = sshService.testConnection(setup.getConfig());
                mainHandler.post(() -> {
                    setSetupRunning(false);
                    setStatus("Termux OpenSSH 已配置", "shell: " + valueOrUnknown(setup.getShell())
                            + "\nrc: " + valueOrUnknown(setup.getRcPath())
                            + "\n" + redact(testOutput), false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setSetupRunning(false);
                    setStatus("配置失败", redact(e.getMessage()), true);
                });
            }
        }, "linecode-termux-setup").start();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.length() == 0 ? "unknown" : value;
    }

    private String redact(String value) {
        return PRIVATE_KEY_PATTERN.matcher(value == null ? "" : value)
                .replaceAll("LINEAI_PRIVATE_KEY=[已保存到 SSH Private key]");
    }

    private void setSetupRunning(boolean running) {
        setupButton.setEnabled(!running);
        setupButton.setAlpha(running ? 0.65f : 1f);
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

    private LinearLayout step(Context context, String number, String text) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.SM);
        row.setLayoutParams(params);
        TextView badge = LineTheme.text(context, number, LineTheme.FONT_XS, LineTheme.TEXT_ON_COLOR, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 12));
        row.addView(badge, new LinearLayout.LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 24)));
        TextView label = desc(context, text);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(label, labelParams);
        return row;
    }

    private LinearLayout button(Context context, String label, int iconType, boolean primary, View.OnClickListener listener) {
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

    private void addCard(LinearLayout content, LinearLayout card) {
        Context context = content.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(card, params);
    }

    private static final class GridLikeActions extends LinearLayout {
        GridLikeActions(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        void addAction(View action) {
            Context context = getContext();
            LinearLayout row;
            if (getChildCount() == 0 || ((LinearLayout) getChildAt(getChildCount() - 1)).getChildCount() >= 2) {
                row = new LinearLayout(context);
                row.setOrientation(HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                if (getChildCount() > 0) {
                    rowParams.topMargin = LineTheme.dp(context, LineTheme.SM);
                }
                addView(row, rowParams);
            } else {
                row = (LinearLayout) getChildAt(getChildCount() - 1);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 38), 1f);
            if (row.getChildCount() > 0) {
                params.leftMargin = LineTheme.dp(context, LineTheme.SM);
            }
            row.addView(action, params);
        }
    }
}
