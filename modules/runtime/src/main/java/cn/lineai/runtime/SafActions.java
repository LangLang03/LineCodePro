package cn.lineai.runtime;

import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.ui.i18n.R;

/**
 * SAF result dispatch. Chat attachments delegate to feature-chat's {@code ChatAttachments};
 * the skill/model/workspace imports stay here because they drive page reloads owned by the
 * composition root's page factories.
 */
public final class SafActions {

    /** Chat image attachment picker request code. */
    public static final int REQUEST_ATTACH = 7403;
    /** Skill archive import picker request code. */
    public static final int REQUEST_SKILL_IMPORT = 7404;
    /** External workspace directory tree picker request code. */
    public static final int REQUEST_TREE = 7405;
    /** Local GGUF model file picker request code. */
    public static final int REQUEST_MODEL_FILE = 7406;
    /** Chat file attachment picker request code. */
    public static final int REQUEST_ATTACH_FILE = 7407;
    /** Chat video attachment picker request code. */
    public static final int REQUEST_VIDEO = 7408;

    /** Utility class; instantiation is not permitted. */
    private SafActions() {
    }

    /**
     * Dispatches one SAF result to its flow; chat attachments delegate to feature-chat,
     * the remaining flows drive reloads owned by the page factories.
     *
     * @param host host providing graph, scheduler and activity
     * @param pages page factory whose late reload hooks the flows trigger
     * @param requestCode request code of the finished picker
     * @param uri selected document, {@code null} when the user cancelled
     * @return {@code true} when this class consumed the request code
     */
    public static boolean handle(PageHost host, PageFactory pages, int requestCode, Uri uri) {
        if (requestCode == REQUEST_ATTACH) {
            cn.lineai.feature.chat.ChatAttachments.importImage(host, uri);
            return true;
        }
        if (requestCode == REQUEST_ATTACH_FILE || requestCode == REQUEST_VIDEO) {
            cn.lineai.feature.chat.ChatAttachments.importFile(host, uri);
            return true;
        }
        if (requestCode == REQUEST_SKILL_IMPORT) {
            importSkill(host, pages, uri);
            return true;
        }
        if (requestCode == REQUEST_TREE) {
            useExternalWorkspace(host, pages, uri);
            return true;
        }
        if (requestCode == REQUEST_MODEL_FILE) {
            importLocalModel(host, pages, uri);
            return true;
        }
        return false;
    }

    /** Imports the picked archive or markdown as a skill and refreshes the skills page. */
    private static void importSkill(PageHost host, PageFactory pages, Uri uri) {
        String name = "skill.zip";
        String last = uri.getLastPathSegment();
        if (last != null && !last.isEmpty()) {
            int slash = last.lastIndexOf('/');
            name = slash >= 0 ? last.substring(slash + 1) : last;
        }
        String mime = host.activity().getContentResolver().getType(uri);
        if (mime != null && mime.contains("markdown")) name = "SKILL.md";
        final String displayName = name;
        Scheduler scheduler = scheduler(host);
        scheduler.io(() -> {
            try (InputStream input = host.activity().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("no stream");
                host.graph().skills().importSkill(input, displayName);
                scheduler.main(() -> {
                    Toast.makeText(host.activity(), R.string.screen_skill_imported,
                            Toast.LENGTH_SHORT).show();
                    pages.features().reloadSkills();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> Toast.makeText(host.activity(),
                        host.activity().getString(R.string.screen_skill_import_failed, message),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Copies the picked GGUF file into the models directory and applies its path. */
    private static void importLocalModel(PageHost host, PageFactory pages, Uri uri) {
        Scheduler scheduler = scheduler(host);
        String name = "model.gguf";
        String last = uri.getLastPathSegment();
        if (last != null && last.toLowerCase(java.util.Locale.ROOT).endsWith(".gguf")) {
            int slash = last.lastIndexOf('/');
            name = slash >= 0 ? last.substring(slash + 1) : last;
        }
        final String fileName = name;
        scheduler.io(() -> {
            File dir = new File(host.activity().getFilesDir(), "models");
            dir.mkdirs();
            File target = new File(dir, fileName);
            try (InputStream input = host.activity().getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("no stream");
                copy(input, output);
                scheduler.main(() -> {
                    pages.features().applyLocalModelPath(target.getAbsolutePath());
                    Toast.makeText(host.activity(), R.string.screen_model_add_local_imported,
                            Toast.LENGTH_SHORT).show();
                });
            } catch (IOException error) {
                target.delete();
                scheduler.main(() -> Toast.makeText(host.activity(),
                        R.string.screen_model_add_local_import_failed,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** Resolves the picked tree to a local path and registers it as a workspace project. */
    private static void useExternalWorkspace(PageHost host, PageFactory pages, Uri uri) {
        try {
            String path = cn.lineai.feature.workspace.SafPaths.toFileSystemPath(uri.toString());
            File dir = path.isEmpty() ? null : new File(path);
            if (dir == null || !dir.isDirectory()) {
                Toast.makeText(host.activity(), R.string.permission_denied_manage_all,
                        Toast.LENGTH_LONG).show();
                return;
            }
            cn.lineai.core.model.Project created =
                    host.graph().projects().create(dir.getName(), dir.getAbsolutePath());
            host.applyProjectLabel(created);
            pages.features().reloadWorkspace();
            Toast.makeText(host.activity(), R.string.workspace_created,
                    Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            android.util.Log.e("LineCodeSAF", "open workspace failed", error);
            try {
                host.graph().errorLogs().record(
                        host.activity().getString(R.string.workspace_provider_open_failed),
                        uri.toString(), android.util.Log.getStackTraceString(error));
            } catch (RuntimeException ignored) {
                android.util.Log.e("LineCodeSAF", "cannot record workspace failure", ignored);
            }
            Toast.makeText(host.activity(), R.string.workspace_provider_open_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Scheduler resolved from the application graph. */
    private static Scheduler scheduler(PageHost host) {
        return host.graph().graph().get(Scheduler.class);
    }

    /** Buffered stream copy used by the model import. */
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
    }
}
