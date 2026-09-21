package cn.lineai.storage;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

import java.util.HashMap;
import java.util.Map;

/** Android adapter for LineCode's manage-all-files permission entry. */
public final class LineCodeStoragePermissionModule
        implements HuxerUIPlatformModule.Factory {
    @Override
    public HuxerUIPlatformModule create(
            Context context,
            PlatformPayload options,
            HuxerUIPlatformChannel.Events events) {
        options.requireNull();
        return new Module(context.getApplicationContext());
    }

    private static final class Module implements HuxerUIPlatformModule {
        private interface Invocation {
            boolean invoke(PlatformPayload arguments);
        }

        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION = () -> { };
        private final Context context;
        private final Map<String, Invocation> invocations = new HashMap<>();

        Module(Context context) {
            this.context = context;
            invocations.put("query", arguments -> {
                arguments.requireNull();
                return isGranted();
            });
            invocations.put("openManagementSettings", arguments -> {
                arguments.requireNull();
                openManagementSettings();
                return isGranted();
            });
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            Invocation invocation = invocations.get(method);
            if (invocation == null) {
                result.fail(
                        "linecode/storage-permission/not-implemented",
                        "Unknown storage permission method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                result.complete(PlatformPayload.booleanValue(invocation.invoke(arguments)));
            } catch (RuntimeException error) {
                String message = error.getMessage();
                result.fail(
                        "linecode/storage-permission/android-error",
                        message == null ? error.getClass().getSimpleName() : message,
                        PlatformPayload.nullValue());
            }
            return NO_CANCELLATION;
        }

        @Override
        public void dispose() {
            invocations.clear();
        }

        private boolean isGranted() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager();
            }
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }

        private void openManagementSettings() {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
            } else {
                intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName()));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (RuntimeException appSettingsError) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    throw appSettingsError;
                }
                Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            }
        }
    }
}
