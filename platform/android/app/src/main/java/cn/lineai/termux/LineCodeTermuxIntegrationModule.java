package cn.lineai.termux;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Android mechanics for the C++ Termux integration application port. */
public final class LineCodeTermuxIntegrationModule
        implements HuxerUIPlatformModule.Factory {
    @Override
    public HuxerUIPlatformModule create(
            Context context,
            PlatformPayload options,
            HuxerUIPlatformChannel.Events events) {
        options.requireNull();
        return new Module(context);
    }

    private static final class Module implements HuxerUIPlatformModule {
        private interface Invocation {
            HuxerUIPlatformChannel.Cancellation invoke(
                    PlatformPayload arguments,
                    HuxerUIPlatformChannel.Result result);
        }

        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION = () -> { };
        private static final int RUN_COMMAND_PERMISSION_REQUEST = 7104;
        private static final String RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
        private static final String TERMUX_PACKAGE = "com.termux";
        private static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
        private static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
        private static final String EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
        private static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
        private static final String EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN";
        private static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
        private static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
        private static final String EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER";
        private static final String EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
        private static final String EXTRA_COMMAND_DESCRIPTION =
                "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION";
        private static final String EXTRA_PENDING_INTENT =
                "com.termux.RUN_COMMAND_PENDING_INTENT";
        private static final String RESULT_BUNDLE = "result";
        private static final String RESULT_STDOUT = "stdout";
        private static final String RESULT_STDERR = "stderr";
        private static final String RESULT_EXIT_CODE = "exitCode";
        private static final String RESULT_ERR = "err";
        private static final String RESULT_ERRMSG = "errmsg";
        private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
        private static final String TERMUX_SH = "/data/data/com.termux/files/usr/bin/sh";

        private final Context context;
        private final Activity activity;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final Set<SetupOperation> setupOperations = new LinkedHashSet<>();
        private final Map<String, Invocation> invocations = new HashMap<>();

        Module(Context context) {
            this.context = context.getApplicationContext();
            this.activity = context instanceof Activity ? (Activity) context : null;
            invocations.put("queryState", this::queryState);
            invocations.put("requestRunCommandPermission", this::requestRunCommandPermission);
            invocations.put("openTermux", this::openTermux);
            invocations.put("setupOpenSsh", this::setupOpenSsh);
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            Invocation invocation = invocations.get(method);
            if (invocation == null) {
                result.fail(
                        "linecode/termux/not-implemented",
                        "Unknown Termux method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                return invocation.invoke(arguments, result);
            } catch (RuntimeException error) {
                failAndroid(result, error);
                return NO_CANCELLATION;
            }
        }

        @Override
        public void dispose() {
            for (SetupOperation operation : setupOperations.toArray(new SetupOperation[0])) {
                operation.cancel();
            }
            invocations.clear();
        }

        private HuxerUIPlatformChannel.Cancellation queryState(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            arguments.requireNull();
            Map<String, PlatformPayload> fields = new LinkedHashMap<>();
            fields.put("installed", PlatformPayload.booleanValue(isTermuxInstalled()));
            fields.put("permissionGranted", PlatformPayload.booleanValue(
                    context.checkSelfPermission(RUN_COMMAND_PERMISSION)
                            == PackageManager.PERMISSION_GRANTED));
            result.complete(PlatformPayload.object(fields));
            return NO_CANCELLATION;
        }

        private HuxerUIPlatformChannel.Cancellation requestRunCommandPermission(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            arguments.requireNull();
            if (activity == null) {
                result.fail(
                        "linecode/termux/permission-denied",
                        "Current Android Context is not an Activity",
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            activity.requestPermissions(
                    new String[]{RUN_COMMAND_PERMISSION},
                    RUN_COMMAND_PERMISSION_REQUEST);
            result.complete(PlatformPayload.nullValue());
            return NO_CANCELLATION;
        }

        private HuxerUIPlatformChannel.Cancellation openTermux(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            arguments.requireNull();
            requireTermuxInstalled();
            Intent launchIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(TERMUX_PACKAGE);
            if (launchIntent == null) {
                launchIntent = new Intent().setClassName(
                        TERMUX_PACKAGE, "com.termux.app.TermuxActivity");
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            result.complete(PlatformPayload.nullValue());
            return NO_CANCELLATION;
        }

        private HuxerUIPlatformChannel.Cancellation setupOpenSsh(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            requireTermuxInstalled();
            if (context.checkSelfPermission(RUN_COMMAND_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) {
                result.fail(
                        "linecode/termux/permission-denied",
                        "LineCode has not been granted the Termux RUN_COMMAND permission",
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            String script = arguments.requireField("script").requireString();
            long requestedTimeout = arguments.requireField("timeoutMs").requireInt64();
            long timeout = Math.max(60_000L, Math.min(requestedTimeout, 900_000L));
            SetupOperation operation = new SetupOperation(script, timeout, result);
            setupOperations.add(operation);
            operation.start();
            return operation::cancel;
        }

        private boolean isTermuxInstalled() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.getPackageManager().getPackageInfo(
                            TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0));
                } else {
                    context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
                }
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
                return false;
            }
        }

        private void requireTermuxInstalled() {
            if (!isTermuxInstalled()) {
                throw new TermuxException(
                        "linecode/termux/not-installed",
                        "Termux is not detected. Please install Termux first.");
            }
        }

        private void failAndroid(
                HuxerUIPlatformChannel.Result result,
                RuntimeException error) {
            String code = error instanceof TermuxException
                    ? ((TermuxException) error).code
                    : "linecode/termux/android-error";
            String message = error.getMessage();
            result.fail(
                    code,
                    message == null ? error.getClass().getSimpleName() : message,
                    PlatformPayload.nullValue());
        }

        private final class SetupOperation {
            private final String script;
            private final long timeout;
            private final HuxerUIPlatformChannel.Result result;
            private final String resultAction = context.getPackageName()
                    + ".TERMUX_SETUP_RESULT." + System.nanoTime();
            private boolean completed;
            private boolean registered;

            private final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    completeFrom(intent == null ? null
                            : intent.getBundleExtra(RESULT_BUNDLE));
                }
            };
            private final Runnable timeoutAction = () -> fail(
                    "linecode/termux/timeout",
                    "Timed out waiting for Termux to configure OpenSSH");

            SetupOperation(
                    String script,
                    long timeout,
                    HuxerUIPlatformChannel.Result result) {
                this.script = script;
                this.timeout = timeout;
                this.result = result;
            }

            void start() {
                try {
                    IntentFilter filter = new IntentFilter(resultAction);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(
                                receiver, filter, Context.RECEIVER_NOT_EXPORTED);
                    } else {
                        context.registerReceiver(receiver, filter);
                    }
                    registered = true;

                    int flags = PendingIntent.FLAG_ONE_SHOT
                            | PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        flags |= PendingIntent.FLAG_MUTABLE;
                    }
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            context,
                            (int) (System.nanoTime() & 0x7fffffff),
                            new Intent(resultAction).setPackage(context.getPackageName()),
                            flags);
                    Intent command = new Intent()
                            .setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                            .setAction(ACTION_RUN_COMMAND)
                            .putExtra(EXTRA_COMMAND_PATH, TERMUX_SH)
                            .putExtra(EXTRA_ARGUMENTS, new String[]{"-s"})
                            .putExtra(EXTRA_STDIN, script)
                            .putExtra(EXTRA_WORKDIR, TERMUX_HOME)
                            .putExtra(EXTRA_BACKGROUND, true)
                            .putExtra(EXTRA_RUNNER, "app-shell")
                            .putExtra(EXTRA_COMMAND_LABEL, "LineCode OpenSSH setup")
                            .putExtra(
                                    EXTRA_COMMAND_DESCRIPTION,
                                    "Install OpenSSH, configure a key, start sshd and verify the SSH connection.")
                            .putExtra(EXTRA_PENDING_INTENT, pendingIntent);
                    context.startService(command);
                    mainHandler.postDelayed(timeoutAction, timeout);
                } catch (RuntimeException error) {
                    String message = error.getMessage();
                    fail(
                            error instanceof SecurityException
                                    ? "linecode/termux/permission-denied"
                                    : "linecode/termux/android-error",
                            message == null ? error.getClass().getSimpleName() : message);
                }
            }

            void completeFrom(Bundle payload) {
                if (payload == null) {
                    fail(
                            "linecode/termux/no-result",
                            "Termux returned no execution result. Termux 0.109 or newer is required.");
                    return;
                }
                String stdout = payload.getString(RESULT_STDOUT, "");
                String stderr = payload.getString(RESULT_STDERR, "");
                int exitCode = payload.getInt(RESULT_EXIT_CODE, 0);
                int err = payload.getInt(RESULT_ERR, Activity.RESULT_OK);
                String errmsg = payload.getString(RESULT_ERRMSG, "");
                String combined = combine(stdout, stderr);
                if (err != Activity.RESULT_OK) {
                    fail(
                            "linecode/termux/command-failed",
                            errmsg.isEmpty() ? "Termux failed to start command: " + err : errmsg);
                    return;
                }
                if (exitCode != 0) {
                    fail(
                            "linecode/termux/command-failed",
                            "exit status " + exitCode
                                    + (combined.isEmpty() ? "" : "\n" + combined));
                    return;
                }
                if (!finish()) {
                    return;
                }
                result.complete(PlatformPayload.string(combined));
            }

            void fail(String code, String message) {
                if (!finish()) {
                    return;
                }
                result.fail(code, message, PlatformPayload.nullValue());
            }

            void cancel() {
                finish();
            }

            private boolean finish() {
                if (completed) {
                    return false;
                }
                completed = true;
                mainHandler.removeCallbacks(timeoutAction);
                if (registered) {
                    try {
                        context.unregisterReceiver(receiver);
                    } catch (RuntimeException ignored) {
                        // Receiver may already have been removed by platform teardown.
                    }
                    registered = false;
                }
                setupOperations.remove(this);
                return true;
            }
        }

        private static String combine(String stdout, String stderr) {
            String output = stdout == null ? "" : stdout.trim();
            String error = stderr == null ? "" : stderr.trim();
            if (output.isEmpty()) {
                return error;
            }
            return error.isEmpty() ? output : output + "\n" + error;
        }
    }

    private static final class TermuxException extends RuntimeException {
        final String code;

        TermuxException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
