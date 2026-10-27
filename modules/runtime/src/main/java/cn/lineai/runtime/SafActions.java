package cn.lineai.runtime;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.ui.i18n.R;

/** SAF result handling for chat attachments, skill import and workspace roots. */
public final class SafActions {

    public static final int REQUEST_ATTACH = 7403;
    public static final int REQUEST_SKILL_IMPORT = 7404;
    public static final int REQUEST_TREE = 7405;
    public static final int REQUEST_MODEL_FILE = 7406;
    public static final int REQUEST_ATTACH_FILE = 7407;

    private SafActions() {
    }

    public static boolean handle(PageHost host, PageFactory pages, int requestCode, Uri uri) {
        if (requestCode == REQUEST_ATTACH) {
            copyAttachment(host, uri);
            return true;
        }
        if (requestCode == REQUEST_ATTACH_FILE) {
            copyFileAttachment(host, uri);
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

    private static void copyAttachment(PageHost host, Uri uri) {
        Scheduler scheduler = scheduler(host);
        String mime = host.activity().getContentResolver().getType(uri);
        String extension = "jpg";
        if (mime != null && mime.contains("/")) {
            String suffix = mime.substring(mime.indexOf('/') + 1)
                    .toLowerCase(java.util.Locale.ROOT);
            if (suffix.equals("jpeg")) suffix = "jpg";
            if (suffix.matches("[a-z0-9]{2,5}")) extension = suffix;
        }
        final String fileName = "image-" + System.currentTimeMillis() + "." + extension;
        scheduler.io(() -> {
            File target = new File(attachmentsDir(host), fileName);
            try (InputStream input = host.activity().getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("no stream");
                copy(input, output);
                scheduler.main(() -> host.addChatImage(target.getAbsolutePath()));
            } catch (IOException error) {
                target.delete();
                scheduler.main(() -> Toast.makeText(host.activity(),
                        R.string.screen_model_add_local_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static void copyFileAttachment(PageHost host, Uri uri) {
        Scheduler scheduler = scheduler(host);
        String name = "attachment.bin";
        String last = uri.getLastPathSegment();
        if (last != null && !last.isEmpty()) {
            int slash = last.lastIndexOf('/');
            name = slash >= 0 ? last.substring(slash + 1) : last;
        }
        final String fileName = name;
        scheduler.io(() -> {
            File target = new File(attachmentsDir(host),
                    System.currentTimeMillis() + "-" + fileName);
            try (InputStream input = host.activity().getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("no stream");
                copy(input, output);
                scheduler.main(() -> host.addChatFile(target.getAbsolutePath()));
            } catch (IOException error) {
                target.delete();
                scheduler.main(() -> Toast.makeText(host.activity(),
                        R.string.screen_model_add_local_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

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

    private static void useExternalWorkspace(PageHost host, PageFactory pages, Uri uri) {
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
        Toast.makeText(host.activity(), R.string.workspace_created, Toast.LENGTH_SHORT).show();
    }

    private static Scheduler scheduler(PageHost host) {
        return host.graph().graph().get(Scheduler.class);
    }

    private static File attachmentsDir(PageHost host) {
        File dir = new File(host.activity().getFilesDir(), "attachments");
        dir.mkdirs();
        return dir;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
    }
}
