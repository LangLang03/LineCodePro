package cn.lineai.platform;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import cn.lineai.R;
import java.io.File;
import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

/** Android boundary for opening the private LineCode workspace directory. */
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
            if (!"openHome".equals(method)) {
                result.fail(
                        "linecode/workspace-directory-share/not-implemented",
                        "Unknown workspace-directory-share method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                arguments.requireNull();
                openHome(context);
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

    private static void openHome(Context context) {
        File home = WorkspaceDirectoryProvider.homeDirectory(context);
        if (!home.isDirectory() && !home.mkdirs()) {
            throw new IllegalStateException(
                    context.getString(R.string.workspace_provider_open_failed));
        }

        Uri homeUri = WorkspaceDirectoryProvider.fileUri(context, "home");
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(homeUri, "resource/folder");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent chooser = Intent.createChooser(
                view, context.getString(R.string.workspace_provider_open_title));
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (context.getPackageManager().queryIntentActivities(view, 0).isEmpty()) {
            openSendFallback(context, home);
            return;
        }
        try {
            context.startActivity(chooser);
        } catch (RuntimeException primary) {
            openSendFallback(context, home);
        }
    }

    private static void openSendFallback(Context context, File home) {
        Uri rootUri = WorkspaceDirectoryProvider.rootUri(context);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, home.getAbsolutePath());
        send.putExtra(Intent.EXTRA_STREAM, rootUri);
        send.setClipData(ClipData.newRawUri("LineCode workspace", rootUri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent fallback = Intent.createChooser(
                send, context.getString(R.string.workspace_provider_open_title));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(fallback);
    }
}
