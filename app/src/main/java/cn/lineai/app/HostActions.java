package cn.lineai.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
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

    private HostActions() {
    }

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

    static List<WorkspaceNode> workspaceFiles(AppGraph graph, String path) {
        Project project = graph.projects().selected();
        if (project == null) {
            return Collections.emptyList();
        }
        File baseDir = new File(project.path);
        if (!baseDir.isDirectory()) {
            return Collections.emptyList();
        }
        List<WorkspaceNode> nodes = new ArrayList<>();
        try {
            File base = baseDir.getCanonicalFile();
            File dir = path == null || path.isEmpty()
                    ? base : new File(base, path).getCanonicalFile();
            if (!dir.equals(base) && !dir.getPath().startsWith(base.getPath() + File.separator)) {
                return nodes;
            }
            File[] children = dir.listFiles();
            if (children == null) {
                return nodes;
            }
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File child : children) {
                if (nodes.size() >= 300) {
                    break;
                }
                String relative = base.toPath().relativize(child.toPath()).toString();
                nodes.add(new WorkspaceNode(child.getName(), relative, child.isDirectory()));
            }
        } catch (java.io.IOException ignored) {
            return Collections.emptyList();
        }
        return nodes;
    }

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

    static boolean isIgnoringBatteryOptimizations(Activity activity) {
        android.os.PowerManager power =
                (android.os.PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        return power == null || power.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

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

    static void showCrashIfNeeded(Activity activity) {
        File crash = CrashHandler.crashFile(activity);
        if (!crash.isFile()) {
            return;
        }
        Intent intent = new Intent(activity, CrashActivity.class);
        intent.putExtra("crash_file", crash.getAbsolutePath());
        activity.startActivity(intent);
    }

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
