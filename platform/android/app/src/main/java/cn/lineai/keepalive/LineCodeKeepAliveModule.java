package cn.lineai.keepalive;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/** Android API adapter only. Keep-alive policy remains in portable C++. */
public final class LineCodeKeepAliveModule implements HuxerUIPlatformModule.Factory {
    private static final String PREFERENCES_NAME = "linecode_keep_alive";
    private static final String WAKE_LOCK_KEY = "wake_lock_enabled";
    private static final String FOREGROUND_KEY = "foreground_enabled";
    private static final String SILENT_AUDIO_KEY = "fake_audio_enabled";

    @Override
    public HuxerUIPlatformModule create(
            Context context,
            PlatformPayload options,
            HuxerUIPlatformChannel.Events events) {
        options.requireNull();
        return new Module(context);
    }

    private static final class Module implements HuxerUIPlatformModule {
        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION = () -> { };
        private static final int NOTIFICATION_PERMISSION_REQUEST = 7301;

        private final Context context;
        private final Activity activity;
        private final SharedPreferences preferences;

        Module(Context context) {
            this.context = context.getApplicationContext();
            this.activity = context instanceof Activity ? (Activity) context : null;
            this.preferences = this.context.getSharedPreferences(
                    PREFERENCES_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            try {
                switch (method) {
                    case "loadPreferences":
                        arguments.requireNull();
                        result.complete(loadPreferences());
                        break;
                    case "savePreferences":
                        savePreferences(arguments);
                        result.complete(PlatformPayload.nullValue());
                        break;
                    case "applyEffectiveState":
                        applyEffectiveState(arguments);
                        result.complete(PlatformPayload.nullValue());
                        break;
                    case "querySystemState":
                        arguments.requireNull();
                        result.complete(querySystemState());
                        break;
                    case "requestNotificationPermission":
                        arguments.requireNull();
                        requestNotificationPermission();
                        result.complete(PlatformPayload.nullValue());
                        break;
                    case "requestIgnoreBatteryOptimizations":
                        arguments.requireNull();
                        requestIgnoreBatteryOptimizations();
                        result.complete(PlatformPayload.nullValue());
                        break;
                    default:
                        result.fail(
                                "linecode/keep-alive/not-implemented",
                                "Unknown keep-alive method: " + method,
                                PlatformPayload.nullValue());
                        break;
                }
            } catch (RuntimeException error) {
                String message = error.getMessage();
                result.fail(
                        "linecode/keep-alive/android-error",
                        message == null ? error.getClass().getSimpleName() : message,
                        PlatformPayload.nullValue());
            }
            return NO_CANCELLATION;
        }

        @Override
        public void dispose() {
            // The foreground service owns its Android resources independently.
        }

        private PlatformPayload loadPreferences() {
            Map<String, PlatformPayload> fields = new LinkedHashMap<>();
            fields.put("wakeLockEnabled", PlatformPayload.booleanValue(
                    preferences.getBoolean(WAKE_LOCK_KEY, true)));
            fields.put("foregroundEnabled", PlatformPayload.booleanValue(
                    preferences.getBoolean(FOREGROUND_KEY, false)));
            fields.put("silentAudioEnabled", PlatformPayload.booleanValue(
                    preferences.getBoolean(SILENT_AUDIO_KEY, false)));
            return PlatformPayload.object(fields);
        }

        private void savePreferences(PlatformPayload payload) {
            boolean wakeLock = payload.requireField("wakeLockEnabled").requireBoolean();
            boolean foreground = payload.requireField("foregroundEnabled").requireBoolean();
            boolean silentAudio = payload.requireField("silentAudioEnabled").requireBoolean();
            preferences.edit()
                    .putBoolean(WAKE_LOCK_KEY, wakeLock)
                    .putBoolean(FOREGROUND_KEY, foreground)
                    .putBoolean(SILENT_AUDIO_KEY, silentAudio)
                    .apply();
        }

        private void applyEffectiveState(PlatformPayload payload) {
            boolean wakeLock = payload.requireField("wakeLockEnabled").requireBoolean();
            boolean foreground = payload.requireField("foregroundEnabled").requireBoolean();
            boolean silentAudio = payload.requireField("silentAudioEnabled").requireBoolean();
            String status = payload.requireField("status").requireString();
            LineCodeKeepAliveService.apply(
                    context, wakeLock, foreground, silentAudio, status);
        }

        private PlatformPayload querySystemState() {
            Map<String, PlatformPayload> fields = new LinkedHashMap<>();
            fields.put("notificationsGranted", PlatformPayload.booleanValue(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                            || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED));
            PowerManager manager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            fields.put("batteryOptimizationIgnored", PlatformPayload.booleanValue(
                    manager == null
                            || manager.isIgnoringBatteryOptimizations(context.getPackageName())));
            return PlatformPayload.object(fields);
        }

        private void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                    || activity == null) {
                return;
            }
            activity.requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }

        private void requestIgnoreBatteryOptimizations() {
            PowerManager manager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (manager == null
                    || manager.isIgnoringBatteryOptimizations(context.getPackageName())) {
                return;
            }
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
