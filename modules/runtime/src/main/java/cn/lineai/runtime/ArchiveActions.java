package cn.lineai.runtime;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;

/** SAF export/import of the user-data archive: conversation, model and settings state. */
public final class ArchiveActions {

    /** Request code of the export create-document picker. */
    public static final int REQUEST_EXPORT = 7401;
    /** Request code of the import open-document picker. */
    public static final int REQUEST_IMPORT = 7402;

    /** Utility class; instantiation is not permitted. */
    private ArchiveActions() {
    }

    /**
     * Launches the SAF create-document picker for a new archive file.
     *
     * @param host host providing the activity for the picker
     */
    public static void requestExport(PageHost host) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,
                "linecode-" + System.currentTimeMillis() + ".linecode");
        try {
            host.activity().startActivityForResult(intent, REQUEST_EXPORT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(host.activity(), R.string.archive_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Launches the SAF open-document picker for an existing archive file.
     *
     * @param host host providing the activity for the picker
     */
    public static void requestImport(PageHost host) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            host.activity().startActivityForResult(intent, REQUEST_IMPORT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(host.activity(), R.string.archive_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Dispatches a picker result to the matching export or import flow.
     *
     * @param host host providing the graph, scheduler and dialogs
     * @param requestCode request code of the finished picker
     * @param uri selected document, {@code null} when the user cancelled
     * @return {@code true} when this class consumed the request code
     */
    public static boolean handleResult(PageHost host, int requestCode, Uri uri) {
        if (requestCode == REQUEST_EXPORT) {
            export(host, uri);
            return true;
        }
        if (requestCode == REQUEST_IMPORT) {
            Dialogs.confirm(host.activity(), host.tokens(),
                    host.activity().getString(R.string.archive_import_title),
                    host.activity().getString(R.string.archive_import_message),
                    host.activity().getString(R.string.common_cancel),
                    host.activity().getString(R.string.common_confirm),
                    confirmed -> {
                        if (confirmed) importArchive(host, uri);
                    });
            return true;
        }
        return false;
    }

    /** Writes the archive on an io thread and toasts the summary on main. */
    private static void export(PageHost host, Uri uri) {
        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
        scheduler.io(() -> {
            try (java.io.OutputStream output = host.activity().getContentResolver()
                    .openOutputStream(uri)) {
                cn.lineai.data.api.ArchiveService.Summary summary =
                        host.graph().archives().export(output);
                scheduler.main(() -> Toast.makeText(host.activity(),
                        host.activity().getString(R.string.archive_exported,
                                summary.conversations, summary.models, summary.settings),
                        Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                scheduler.main(() -> Toast.makeText(host.activity(),
                        R.string.archive_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Halts chat, imports on io, then reloads settings and chat state on main. */
    private static void importArchive(PageHost host, Uri uri) {
        // Halt the chat before the import wipes conversations/messages; otherwise a running
        // turn keeps writing into tables the import is deleting underneath it.
        host.graph().chat().haltForDataReset();
        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
        scheduler.io(() -> {
            try (java.io.InputStream input = host.activity().getContentResolver()
                    .openInputStream(uri)) {
                cn.lineai.data.api.ArchiveService.Summary summary =
                        host.graph().archives().importFrom(input);
                scheduler.main(() -> {
                    host.graph().settings().invalidate();
                    host.graph().preferences().dispatch(
                            new cn.lineai.core.service.SettingsAction.Reload());
                    host.graph().chat().reload();
                    Toast.makeText(host.activity(), host.activity().getString(
                            R.string.archive_imported, summary.conversations,
                            summary.models, summary.settings), Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                scheduler.main(() -> Toast.makeText(host.activity(),
                        R.string.archive_failed, Toast.LENGTH_LONG).show());
            }
        });
    }
}
