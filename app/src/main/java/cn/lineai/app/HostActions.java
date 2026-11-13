package cn.lineai.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import cn.lineai.core.model.Project;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.WorkspaceNode;
import cn.lineai.feature.chat.toolcall.ToolDiffLookup;
import cn.lineai.runtime.AppGraph;
import cn.lineai.runtime.CrashHandler;
import cn.lineai.runtime.SafActions;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.tokens.Tokens;

/**
 * Platform actions the chat activity delegates to: workspace files, SAF pickers, keep-alive and
 * crash import. Kept out of {@link MainActivity} so the activity stays a thin shell under the
 * 600-line guard while the behaviour remains identical.
 */
final class HostActions {

    private static final int NOTIFICATION_REQUEST = 7301;

    /** Utility class; instantiation is not permitted. */
    private HostActions() {
    }

    /**
     * Opens a workspace file in an external viewer through the read-only content provider,
     * refusing paths that escape the project root via canonical path comparison.
     *
     * @param activity activity used for the chooser and toasts
     * @param graph application graph resolving the selected project
     * @param relativePath path relative to the project root, empty for the root itself
     */
    static void openWorkspaceFile(Activity activity, AppGraph graph, String relativePath) {
        Project project = graph.projects().selected();
        if (project == null) {
            Toast.makeText(activity, R.string.drawer_empty_files, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File base = new File(project.path).getCanonicalFile();
            File file = new File(base, relativePath == null ? "" : relativePath).getCanonicalFile();
            if (!file.equals(base) && !file.getPath().startsWith(base.getPath()
                    + File.separator)) {
                throw new SecurityException("File escapes the workspace");
            }
            if (!file.isFile()) {
                Toast.makeText(activity, R.string.drawer_empty_files, Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = WorkspaceFileProvider.uriFor(activity, file);
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName());
            String mime = extension == null ? null
                    : android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime == null ? "*/*" : mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.setClipData(android.content.ClipData.newRawUri("", uri));
            activity.startActivity(Intent.createChooser(view,
                    activity.getString(R.string.common_open_with)));
        } catch (Exception error) {
            Toast.makeText(activity, R.string.browser_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Launches the SAF open-document picker.
     *
     * @param activity activity receiving the result
     * @param mime primary accepted mime type
     * @param extraMimes additional accepted mime types, or {@code null}
     * @param requestCode request code reported back by the activity
     */
    static void pickDocument(Activity activity, String mime, String[] extraMimes, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        if (extraMimes != null) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimes);
        }
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(activity, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Picks an image or video through the system Photo Picker when the platform has one.
     *
     * <p>{@code ACTION_OPEN_DOCUMENT} opens DocumentsUI, which is a file browser: the user has to
     * navigate folders to reach a picture. Android 13's Photo Picker is the media surface users
     * expect (recent photos, albums, no storage permission). Older devices and OEMs without the
     * picker keep the SAF fallback.</p>
     */
    /**
     * Picks an image or video through the system Photo Picker when the platform has one.
     *
     * <p>{@code ACTION_OPEN_DOCUMENT} opens DocumentsUI, which is a file browser: the user has to
     * navigate folders to reach a picture. Android 13's Photo Picker is the media surface users
     * expect (recent photos, albums, no storage permission). Older devices and OEMs without the
     * picker keep the SAF fallback.</p>
     *
     * @param activity activity receiving the result
     * @param mime accepted media type such as {@code image/*} or {@code video/*}
     * @param requestCode request code reported back by the activity
     */
    static void pickVisualMedia(Activity activity, String mime, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
            picker.setType(mime);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                activity.startActivityForResult(picker, requestCode);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // No Photo Picker on this build; DocumentsUI still works.
                System.err.println("photo picker unavailable, falling back: " + ignored);
            }
        }
        pickDocument(activity, mime, null, requestCode);
    }

    /** Hands the workspace root to another app with read/write grants. */
    static void shareWorkspace(Activity activity) {
        Uri root = DocumentsContract.buildDocumentUri(
                activity.getPackageName() + ".workspace.documents", "/");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(root, DocumentsContract.Document.MIME_TYPE_DIR);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            Intent chooser = Intent.createChooser(intent,
                    activity.getString(R.string.workspace_provider_open_title));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(chooser);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(activity, R.string.workspace_provider_open_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Registers an external directory as a workspace project, requesting all-files access
     * on API 30 and later before falling back to the SAF tree picker.
     *
     * @param activity activity hosting the dialogs and pickers
     * @param tokens design tokens for the confirmation dialog
     * @param graph application graph (reserved for follow-up wiring)
     */
    static void openExternalWorkspace(Activity activity, Tokens tokens, AppGraph graph) {
        if (android.os.Build.VERSION.SDK_INT >= 30
                && !android.os.Environment.isExternalStorageManager()) {
            Dialogs.confirm(activity, tokens,
                    activity.getString(R.string.header_permission_desc),
                    activity.getString(R.string.permission_denied_manage_all),
                    activity.getString(R.string.common_cancel),
                    activity.getString(R.string.common_confirm),
                    confirmed -> {
                        if (!confirmed) {
                            return;
                        }
                        Intent intent = new Intent(
                                android.provider.Settings
                                        .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + activity.getPackageName()));
                        try {
                            activity.startActivity(intent);
                        } catch (ActivityNotFoundException error) {
                            Toast.makeText(activity, R.string.rewrite_not_available,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            activity.startActivityForResult(intent, SafActions.REQUEST_TREE);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(activity, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Reads the battery optimization exemption state.
     *
     * @param activity activity used to reach the power manager
     * @return {@code true} when the app is exempt; conservatively {@code true} when the
     *         power manager is unavailable
     */
    static boolean isIgnoringBatteryOptimizations(Activity activity) {
        android.os.PowerManager power =
                (android.os.PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        return power == null || power.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

    /**
     * Opens the direct exemption request, falling back to the general settings screen when
     * the direct action is unavailable on the device.
     *
     * @param activity activity starting the system screens
     */
    static void requestBatteryOptimizationExemption(Activity activity) {
        try {
            activity.startActivity(new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (ActivityNotFoundException error) {
            activity.startActivity(new Intent(
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    /**
     * Restarts the keep-alive service with the stored wake lock, foreground and fake-audio
     * preferences, requesting the notification permission first when needed.
     *
     * @param activity activity used to start the service and request permissions
     * @param graph application graph providing the settings state
     */
    static void updateKeepAlive(Activity activity, AppGraph graph) {
        UiPreferences values = graph.preferences().state().values;
        boolean wake = values.keepAliveWakeLock();
        boolean foreground = values.keepAliveForeground();
        boolean audio = values.keepAliveFakeAudio();
        cn.lineai.platform.KeepAliveService.start(activity, wake, foreground, audio);
        if ((foreground || audio) && android.os.Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[] {
                    android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    /**
     * Opens {@link CrashActivity} when a crash report from a previous run exists.
     *
     * @param activity activity used to launch the crash screen
     */
    static void showCrashIfNeeded(Activity activity) {
        File crash = CrashHandler.crashFile(activity);
        if (!crash.isFile()) {
            return;
        }
        Intent intent = new Intent(activity, CrashActivity.class);
        intent.putExtra("crash_file", crash.getAbsolutePath());
        activity.startActivity(intent);
    }

    /**
     * Moves a pending (non-fatal) crash log into the error log store and deletes the file.
     *
     * @param activity activity used to resolve the pending file and strings
     * @param graph application graph providing the error log store
     */
    static void importCrashLog(Activity activity, AppGraph graph) {
        File file = CrashHandler.pendingFile(activity);
        if (!file.isFile()) {
            return;
        }
        try {
            String detail = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            graph.errorLogs().record(activity.getString(R.string.crash_title),
                    activity.getString(R.string.crash_subtitle), detail);
            file.delete();
        } catch (Exception error) {
            android.util.Log.e("LineCodeCrash", "cannot import crash log", error);
        }
    }

    /** Resolves a recorded file diff for a tool card; null when the id is unknown. */
    static ToolDiffLookup.Snapshot toolDiff(AppGraph graph, String diffId) {
        if (diffId == null || diffId.isEmpty()) {
            return null;
        }
        for (cn.lineai.core.model.FileDiff diff : graph.diffs().list()) {
            if (diff.id.equals(diffId)) {
                return new ToolDiffLookup.Snapshot(diff.path, diff.before, diff.after);
            }
        }
        return null;
    }
}
