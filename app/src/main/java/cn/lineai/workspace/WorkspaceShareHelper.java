package cn.lineai.workspace;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import cn.lineai.R;
import java.io.File;

public final class WorkspaceShareHelper {
    private WorkspaceShareHelper() {
    }

    public static void shareHome(Context context) {
        if (context == null) {
            return;
        }
        try {
            WorkspacePaths paths = new WorkspacePaths(context);
            paths.ensurePrivateRoots();
            File home = paths.getHomeRoot();
            Uri uri = WorkspaceFileProvider.fileUri(context, "home");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(intent, context.getString(R.string.workspace_provider_open_title));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Also offer a send intent fallback with a marker text + content root.
            try {
                context.startActivity(chooser);
            } catch (Exception primary) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, home.getAbsolutePath());
                send.putExtra(Intent.EXTRA_STREAM, WorkspaceFileProvider.rootUri(context));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(send, context.getString(R.string.workspace_provider_open_title)));
            }
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.workspace_provider_open_failed), Toast.LENGTH_LONG).show();
        }
    }
}
