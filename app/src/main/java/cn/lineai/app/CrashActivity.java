package cn.lineai.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.runtime.CrashHandler;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Shown after an uncaught exception; the stack trace is also written to the error log. */
public final class CrashActivity extends Activity {

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String path = getIntent().getStringExtra("crash_file");
        String detail = read(path);
        Tokens tokens = Tokens.of(ThemePalette.dark());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        Ui.padding(root, 24, 64, 24, 32);
        setContentView(root);

        root.addView(Ui.bold(this, getString(R.string.crash_title), 22, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = Ui.text(this, getString(R.string.crash_subtitle), 13,
                tokens.textSecondary, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = Ui.dp(this, 8);
        root.addView(subtitle, subtitleParams);

        ScrollView scroll = new ScrollView(this);
        TextView stack = Ui.text(this, detail, 12, tokens.textSecondary, Typeface.NORMAL);
        stack.setTypeface(Typeface.MONOSPACE);
        stack.setTextIsSelectable(true);
        Ui.padding(stack, 12, 12, 12, 12);
        stack.setBackground(Ui.rounded(this, tokens.codeBg, 10));
        scroll.addView(stack, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = Ui.dp(this, 16);
        root.addView(scroll, scrollParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(button(R.string.common_copy, () -> copy(detail)),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        TextView restart = button(R.string.crash_restart, () -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 48), 1f);
        restartParams.leftMargin = Ui.dp(this, 8);
        actions.addView(restart, restartParams);
        TextView close = button(R.string.common_close, this::finishAffinity);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 48), 1f);
        closeParams.leftMargin = Ui.dp(this, 8);
        actions.addView(close, closeParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(this, 16);
        root.addView(actions, actionsParams);

        savePending(detail);
        if (path != null) new File(path).delete();
    }

    private TextView button(int text, Runnable action) {
        Tokens tokens = Tokens.of(ThemePalette.dark());
        TextView view = Ui.medium(this, getString(text), 14, tokens.text);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(Ui.rounded(this, tokens.surfaceElevated, 12));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void copy(String detail) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_title), detail));
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show();
    }

    private void savePending(String detail) {
        try {
            File file = CrashHandler.pendingFile(this);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(file.toPath(), detail.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            android.util.Log.e("LineCodeCrash", "cannot save crash log", error);
        }
    }

    private static String read(String path) {
        if (path == null) return "";
        try {
            return new String(Files.readAllBytes(new File(path).toPath()),
                    StandardCharsets.UTF_8);
        } catch (Exception error) {
            return error.toString();
        }
    }
}
