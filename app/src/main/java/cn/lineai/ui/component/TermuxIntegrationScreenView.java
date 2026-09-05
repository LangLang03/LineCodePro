package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

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
import cn.lineai.model.SshConfig;
import cn.lineai.ssh.TermuxHelper;
import java.util.regex.Pattern;

public final class TermuxIntegrationScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onOpenTermux() throws Exception;
        TermuxHelper.TermuxSetupResult onSetupTermuxSsh(int timeoutMs) throws Exception;
        String onTestConnection(SshConfig config) throws Exception;
    }

    private static final int REQUEST_TERMUX_RUN_COMMAND = 7104;
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "LINEAI_PRIVATE_KEY_BEGIN[\\s\\S]*?LINEAI_PRIVATE_KEY_END"
    );

    private final Listener listener;
    private final TextView statusView;
    private final LinearLayout setupButton;

    public TermuxIntegrationScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_termux_title), listener::onBack, null);
        this.listener = listener;

        LinearLayout content = getContent();
        LineTheme.padding(content, 28, 8, 28, 48);

        LinearLayout intro = card(context);
        intro.addView(title(context, context.getString(R.string.screen_termux_section_use)));
        TextView desc = desc(context, context.getString(R.string.screen_termux_use_desc));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        intro.addView(desc, descParams);
        addCard(content, intro);

        LinearLayout steps = card(context);
        steps.addView(title(context, context.getString(R.string.screen_termux_section_steps)));
        steps.addView(step(context, "1", context.getString(R.string.screen_termux_step_1)));
        steps.addView(step(context, "2", context.getString(R.string.screen_termux_step_2)));
        steps.addView(step(context, "3", context.getString(R.string.screen_termux_step_3)));
        addCard(content, steps);

        LinearLayout commandCard = card(context);
        commandCard.addView(title(context, context.getString(R.string.screen_termux_section_intent)));
        TextView command = LineTheme.text(context, TermuxHelper.TERMUX_ALLOW_EXTERNAL_APPS_COMMAND, LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
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
        actions.addView(title(context, context.getString(R.string.screen_termux_actions_title)));
        GridLikeActions actionGrid = new GridLikeActions(context);
        actionGrid.addAction(button(context, context.getString(R.string.screen_termux_copy_intent), IconButtonView.COPY, false, v -> copyCommand()));
        actionGrid.addAction(button(context, context.getString(R.string.screen_termux_run_command_perm), IconButtonView.SHIELD_CHECK, false, v -> requestRunCommandPermission()));
        actionGrid.addAction(button(context, context.getString(R.string.screen_termux_open_termux), IconButtonView.EXTERNAL_LINK, false, v -> openTermux()));
        setupButton = button(context, context.getString(R.string.screen_termux_auto_ssh), IconButtonView.DOWNLOAD, true, v -> setupOpenSsh());
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
        Context context = getContext();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.screen_termux_clip_label), TermuxHelper.TERMUX_ALLOW_EXTERNAL_APPS_COMMAND));
        }
        setStatus(context.getString(R.string.screen_termux_status_copied_title), context.getString(R.string.screen_termux_status_copied_message), false);
    }

    private void requestRunCommandPermission() {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            setStatus(context.getString(R.string.screen_termux_status_unable_title), context.getString(R.string.screen_termux_status_unable_message), true);
            return;
        }
        ((Activity) context).requestPermissions(new String[] {TermuxHelper.TERMUX_RUN_COMMAND_PERMISSION}, REQUEST_TERMUX_RUN_COMMAND);
        setStatus(context.getString(R.string.screen_termux_status_requested_title), context.getString(R.string.screen_termux_status_requested_message), false);
    }

    private void openTermux() {
        Context context = getContext();
        try {
            listener.onOpenTermux();
            setStatus(context.getString(R.string.screen_termux_status_opened_title), context.getString(R.string.screen_termux_status_opened_message), false);
        } catch (Exception e) {
            setStatus(context.getString(R.string.screen_termux_status_open_failed_title), e.getMessage(), true);
        }
    }

    private void setupOpenSsh() {
        Context context = getContext();
        setSetupRunning(true);
        setStatus(context.getString(R.string.screen_termux_status_setup_title), context.getString(R.string.screen_termux_status_setup_message), false);
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                TermuxHelper.TermuxSetupResult setup = listener.onSetupTermuxSsh(15 * 60 * 1000);
                String testOutput = listener.onTestConnection(setup.getConfig());
                handler.post(() -> {
                    setSetupRunning(false);
                    String doneMessage = context.getString(R.string.screen_termux_status_setup_done_message,
                            valueOrUnknown(setup.getShell()),
                            valueOrUnknown(setup.getRcPath()),
                            redact(testOutput));
                    setStatus(context.getString(R.string.screen_termux_status_setup_done_title), doneMessage, false);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setSetupRunning(false);
                    setStatus(context.getString(R.string.screen_termux_status_setup_failed_title), redact(e.getMessage()), true);
                });
            }
        }, "linecode-termux-setup").start();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.length() == 0 ? "unknown" : value;
    }

    private String redact(String value) {
        return PRIVATE_KEY_PATTERN.matcher(value == null ? "" : value)
                .replaceAll(getContext().getString(R.string.screen_termux_redact_replacement));
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

    private LinearLayout button(Context context, String label, int iconType, boolean primary, View.OnClickListener onClickListener) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(LineTheme.roundedStroke(context, primary ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, primary ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setOnClickListener(onClickListener);
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
        card.setBackground(null);
        LineTheme.padding(card, 0, 16, 0, 24);
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

    private static final class GridLikeActions extends ScreenSurfaceView {
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
