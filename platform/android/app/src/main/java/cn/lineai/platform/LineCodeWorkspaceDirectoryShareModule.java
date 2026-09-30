package cn.lineai.platform;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;

import cn.lineai.R;
import java.io.File;
import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

/** Android boundary for opening the LineCode workspace in the system picker. */
public final class LineCodeWorkspaceDirectoryShareModule
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
        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION =
                () -> { };
        private final Context context;

        Module(Context context) {
            this.context = context;
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            if (!"mountWorkspace".equals(method)) {
                result.fail(
                        "linecode/workspace-directory-share/not-implemented",
                        "Unknown workspace-directory-share method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                arguments.requireNull();
                mountWorkspace(context);
                result.complete(PlatformPayload.nullValue());
            } catch (RuntimeException error) {
                Toast.makeText(
                                context,
                                context.getString(R.string.workspace_provider_open_failed),
                                Toast.LENGTH_LONG)
                        .show();
                String message = error.getMessage();
                result.fail(
                        "linecode/workspace-directory-share/android-error",
                        message == null ? error.getClass().getSimpleName() : message,
                        PlatformPayload.nullValue());
            }
            return NO_CANCELLATION;
        }

        @Override
        public void dispose() {
            // No Android resource is retained beyond the application Context.
        }
    }

    private static void mountWorkspace(Context context) {
        File workspace = WorkspaceDirectoryProvider.workspaceDirectory(context);
        if (!workspace.isDirectory() && !workspace.mkdirs()) {
            throw new IllegalStateException(
                    context.getString(R.string.workspace_provider_open_failed));
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    WorkspaceDirectoryProvider.rootDocumentUri(context));
        }
        context.startActivity(picker);
    }
}
