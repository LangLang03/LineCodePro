package cn.lineai.shell;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.SshConfig;

/**
 * Termux RunCommand bridge: launches the Termux app and drives the bundled OpenSSH setup
 * script through the {@code RUN_COMMAND} service, collecting the generated SSH credentials
 * from the script output.
 */
public final class TermuxClient {

    /** Termux permission guarding the {@code RUN_COMMAND} service; requested by settings. */
    public static final String TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    /** Command that flips Termux's {@code allow-external-apps} property. */
    public static final String TERMUX_ALLOW_EXTERNAL_APPS_COMMAND =
            cn.lineai.core.model.TermuxCommands.ALLOW_EXTERNAL_APPS;

    /** Outcome callbacks; both are invoked on the scheduler's main thread. */
    public interface Callback {
        /** Invoked after the setup script succeeded and its output parsed cleanly. */
        void onSuccess(SetupResult result);

        /** Invoked with a user-presentable message when any setup step fails. */
        void onError(String message);
    }

    /** Parsed result of one successful setup run. */
    public static final class SetupResult {
        /** SSH configuration built from the parsed username, host, port and private key. */
        public final SshConfig config;
        /** Full (already redacted) script output, kept for the settings detail view. */
        public final String output;
        /** Default shell reported by the script; informational only. */
        public final String shell;
        /** Path of the rc file the script wrote, feeding the tips screen. */
        public final String rcPath;

        /** Built by {@link #parse}; not part of the public surface. */
        SetupResult(SshConfig config, String output, String shell, String rcPath) {
            this.config = config;
            this.output = output;
            this.shell = shell;
            this.rcPath = rcPath;
        }
    }

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String TERMUX_ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN";
    private static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    private static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER";
    private static final String EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    private static final String EXTRA_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION";
    private static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";
    private static final String RESULT_BUNDLE = "result";
    private static final String RESULT_STDOUT = "stdout";
    private static final String RESULT_STDERR = "stderr";
    private static final String RESULT_EXIT_CODE = "exitCode";
    private static final String RESULT_ERR = "err";
    private static final String RESULT_ERRMSG = "errmsg";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String TERMUX_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final String SETUP_SCRIPT = "termux_setup.sh";
    private static final long SETUP_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "LINEAI_PRIVATE_KEY_BEGIN\\n([\\s\\S]*?)\\nLINEAI_PRIVATE_KEY_END");

    private final Context context;

    /**
     * Creates a bridge holding the application context.
     *
     * @param context any context; only the application context is retained
     */
    public TermuxClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Brings the Termux main activity to the foreground.
     *
     * @throws IllegalStateException when Termux is not installed
     */
    public void openTermux() {
        ensureInstalled();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launch == null) {
            launch = new Intent().setClassName(TERMUX_PACKAGE, "com.termux.app.TermuxActivity");
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
    }

    /**
     * Checks whether the Termux {@code RUN_COMMAND} permission is granted.
     *
     * @return {@code true} when the setup flow may invoke the Termux service directly
     */
    public boolean hasRunCommandPermission() {
        return context.checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Runs the bundled OpenSSH setup script inside Termux and reports the parsed result.
     * The method verifies installation and permission synchronously and then registers a
     * one-shot result receiver plus a timeout guard; exactly one callback fires on main.
     *
     * @param scheduler scheduler used for the timeout and for main-thread delivery
     * @param callback consumer of the single success or failure outcome
     */
    public void setupOpenSsh(Scheduler scheduler, Callback callback) {
        try {
            ensureInstalled();
        } catch (RuntimeException error) {
            callback.onError(error.getMessage());
            return;
        }
        if (!hasRunCommandPermission()) {
            callback.onError("Termux RUN_COMMAND permission is not granted");
            return;
        }
        String action = context.getPackageName() + ".TERMUX_SETUP_RESULT." + System.currentTimeMillis();
        final BroadcastReceiver[] receiverHolder = new BroadcastReceiver[1];
        final Cancellable[] timeout = new Cancellable[1];
        final boolean[] finished = {false};
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context received, Intent intent) {
                if (finished[0]) return;
                finished[0] = true;
                if (timeout[0] != null) timeout[0].cancel();
                unregister(receiverHolder[0]);
                Bundle result = intent == null ? null : intent.getBundleExtra(RESULT_BUNDLE);
                if (result == null) {
                    scheduler.main(() -> callback.onError("Termux returned no result"));
                    return;
                }
                int err = result.getInt(RESULT_ERR, Activity.RESULT_OK);
                int exitCode = result.getInt(RESULT_EXIT_CODE, 0);
                String stdout = result.getString(RESULT_STDOUT, "");
                String stderr = result.getString(RESULT_STDERR, "");
                String message = result.getString(RESULT_ERRMSG, "");
                if (err != Activity.RESULT_OK) {
                    scheduler.main(() -> callback.onError(message.isEmpty() ? "Termux error " + err : message));
                    return;
                }
                if (exitCode != 0) {
                    scheduler.main(() -> callback.onError("exit status " + exitCode + "\n" + combine(stdout, stderr)));
                    return;
                }
                final String output = combine(stdout, stderr);
                scheduler.main(() -> {
                    try {
                        callback.onSuccess(parse(output));
                    } catch (RuntimeException error) {
                        callback.onError(error.getMessage());
                    }
                });
            }
        };
        receiverHolder[0] = receiver;
        register(receiver, action);
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context,
                (int) (System.currentTimeMillis() & 0x7fffffff),
                new Intent(action).setPackage(context.getPackageName()), flags);
        Intent intent = new Intent()
                .setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                .setAction(TERMUX_ACTION_RUN_COMMAND)
                .putExtra(EXTRA_COMMAND_PATH, TERMUX_SH)
                .putExtra(EXTRA_ARGUMENTS, new String[] {"-s"})
                .putExtra(EXTRA_STDIN, script())
                .putExtra(EXTRA_WORKDIR, TERMUX_HOME)
                .putExtra(EXTRA_BACKGROUND, true)
                .putExtra(EXTRA_RUNNER, "app-shell")
                .putExtra(EXTRA_LABEL, "LineAI OpenSSH setup")
                .putExtra(EXTRA_DESCRIPTION, "Install openssh, create a LineAI key, enable authorized_keys and start sshd.")
                .putExtra(EXTRA_PENDING_INTENT, pending);
        timeout[0] = scheduler.main(SETUP_TIMEOUT_MS, () -> {
            if (finished[0]) return;
            finished[0] = true;
            unregister(receiverHolder[0]);
            callback.onError("Termux setup timed out");
        });
        context.startService(intent);
    }

    /**
     * Extracts the SSH credentials from the setup script output using the
     * {@code LINEAI_*} key/value markers.
     *
     * @param output combined script output; may be {@code null}
     * @return the parsed setup result
     * @throws IllegalStateException when the username or the private key marker is missing
     */
    public static SetupResult parse(String output) {
        String text = output == null ? "" : output;
        String username = value(text, "LINEAI_TERMUX_USERNAME");
        String host = value(text, "LINEAI_TERMUX_HOST");
        int port = parsePort(value(text, "LINEAI_TERMUX_PORT"));
        String shell = value(text, "LINEAI_TERMUX_SHELL");
        String rcPath = value(text, "LINEAI_TERMUX_RC");
        Matcher matcher = PRIVATE_KEY.matcher(text);
        String privateKey = matcher.find() ? matcher.group(1).trim() : "";
        if (username.isEmpty() || privateKey.isEmpty()) {
            throw new IllegalStateException("Unable to parse the Termux setup output:\n" + redact(text));
        }
        SshConfig config = new SshConfig(host.isEmpty() ? SshConfig.DEFAULT_HOST : host, port,
                username, "", privateKey, "");
        return new SetupResult(config, text, shell, rcPath);
    }

    /**
     * Replaces the embedded private key block with a placeholder, making the output safe
     * for logs and error messages.
     *
     * @param output raw script output; may be {@code null}
     * @return the output with any private key block redacted
     */
    public static String redact(String output) {
        return output == null ? "" : PRIVATE_KEY.matcher(output)
                .replaceAll("LINEAI_PRIVATE_KEY_BEGIN\n[redacted]\nLINEAI_PRIVATE_KEY_END");
    }

    /** Verifies the Termux package is installed; throws otherwise. */
    private void ensureInstalled() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE,
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Termux is not installed");
        }
    }

    /** Registers the result receiver, not exported on API 33 and later. */
    private void register(BroadcastReceiver receiver, String action) {
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    /** Unregisters a receiver, tolerating a concurrent unregister race. */
    private void unregister(BroadcastReceiver receiver) {
        if (receiver == null) return;
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException error) {
            android.util.Log.d("TermuxClient", "receiver already unregistered", error);
        }
    }

    /** Loads the setup script from application assets. */
    private String script() {
        try (InputStream input = context.getAssets().open(SETUP_SCRIPT)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read " + SETUP_SCRIPT, error);
        }
    }

    /** Merges trimmed stdout and stderr into one report text. */
    private static String combine(String stdout, String stderr) {
        StringBuilder builder = new StringBuilder();
        if (stdout != null && !stdout.trim().isEmpty()) builder.append(stdout.trim());
        if (stderr != null && !stderr.trim().isEmpty()) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(stderr.trim());
        }
        return builder.toString();
    }

    /** Reads one {@code KEY=value} line; empty when the marker is absent. */
    private static String value(String output, String key) {
        String prefix = key + "=";
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return "";
    }

    /** Parses the port marker, falling back to the default port for invalid values. */
    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 ? port : SshConfig.DEFAULT_PORT;
        } catch (NumberFormatException error) {
            return SshConfig.DEFAULT_PORT;
        }
    }
}
