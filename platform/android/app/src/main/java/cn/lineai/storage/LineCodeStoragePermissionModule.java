package cn.lineai.storage;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.Settings;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIFileReference;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        private final ExecutorService pathResolver = Executors.newSingleThreadExecutor();

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
            if ("resolveLocalDirectory".equals(method)) {
                Uri uri;
                try (HuxerUIFileReference reference = arguments.requireFileReference()) {
                    uri = reference.uri();
                } catch (RuntimeException error) {
                    fail(result, error);
                    return NO_CANCELLATION;
                }
                try {
                    Future<?> request =
                            pathResolver.submit(() -> resolveLocalDirectory(uri, result));
                    return () -> request.cancel(true);
                } catch (RuntimeException error) {
                    fail(result, error);
                    return NO_CANCELLATION;
                }
            }
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
                fail(result, error);
            }
            return NO_CANCELLATION;
        }

        @Override
        public void dispose() {
            pathResolver.shutdownNow();
            invocations.clear();
        }

        private boolean isGranted() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager();
            }
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED;
        }

        private void resolveLocalDirectory(
                Uri uri, HuxerUIPlatformChannel.Result result) {
            try {
                if (!isGranted()) {
                    result.fail(
                            "linecode/storage-permission/permission-required",
                            "External storage access is required for a local workspace",
                            PlatformPayload.nullValue());
                    return;
                }
                String path = localPathForTree(uri);
                result.complete(PlatformPayload.string(path == null ? "" : path));
            } catch (RuntimeException | IOException error) {
                fail(result, error);
            }
        }

        private static void fail(HuxerUIPlatformChannel.Result result, Exception error) {
            String message = error.getMessage();
            result.fail(
                    "linecode/storage-permission/android-error",
                    message == null ? error.getClass().getSimpleName() : message,
                    PlatformPayload.nullValue());
        }

        private String localPathForTree(Uri uri) throws IOException {
            // Map only the system external-storage provider's volume-relative
            // tree IDs, then verify the result with direct filesystem access.
            if (uri == null || !"com.android.externalstorage.documents".equals(uri.getAuthority())) {
                return null;
            }
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 2 || !"tree".equals(segments.get(0))) {
                return null;
            }
            String documentId = DocumentsContract.getTreeDocumentId(uri);
            int separator = documentId.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            String volumeId = documentId.substring(0, separator);
            String relative = documentId.substring(separator + 1);
            File volumeRoot = volumeRoot(volumeId);
            if (volumeRoot == null) {
                return null;
            }
            return LocalDirectoryPath.resolve(volumeRoot, relative);
        }

        private File volumeRoot(String volumeId) {
            if ("primary".equalsIgnoreCase(volumeId)) {
                return Environment.getExternalStorageDirectory();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StorageManager manager = context.getSystemService(StorageManager.class);
                if (manager != null) {
                    for (StorageVolume volume : manager.getStorageVolumes()) {
                        if (volumeId.equalsIgnoreCase(volume.getUuid())) {
                            return volume.getDirectory();
                        }
                    }
                }
            }
            return null;
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

/** Checks the filesystem half of an Android external-storage tree mapping. */
final class LocalDirectoryPath {
    private LocalDirectoryPath() { }

    static String resolve(File volumeRoot, String relative) throws IOException {
        if (!relative.isEmpty()) {
            for (String segment : relative.split("/", -1)) {
                if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                        || segment.indexOf('\\') >= 0 || segment.indexOf('\0') >= 0) {
                    return null;
                }
            }
        }
        File root = volumeRoot.getCanonicalFile();
        File directory = new File(root, relative).getCanonicalFile();
        String rootPath = root.getPath();
        String directoryPath = directory.getPath();
        if (!directoryPath.equals(rootPath)
                && !directoryPath.startsWith(rootPath + File.separator)) {
            return null;
        }
        return directory.isDirectory() && directory.canRead() && directory.canWrite()
                ? directoryPath : null;
    }
}
