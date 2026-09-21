package cn.lineai.platform;

import android.content.Context;
import android.content.Intent;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

/** Android adapter for the reusable native text-sharing application port. */
public final class LineCodeShareTextModule implements HuxerUIPlatformModule.Factory {
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
            if (!"share".equals(method)) {
                result.fail(
                        "linecode/share-text/not-implemented",
                        "Unknown share-text method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                String text = arguments.requireString();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                Intent chooser = Intent.createChooser(send, null);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
                result.complete(PlatformPayload.nullValue());
            } catch (RuntimeException error) {
                String message = error.getMessage();
                result.fail(
                        "linecode/share-text/android-error",
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
}
