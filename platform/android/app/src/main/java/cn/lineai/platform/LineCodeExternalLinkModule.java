package cn.lineai.platform;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

/** Android adapter for the portable external-link application port. */
public final class LineCodeExternalLinkModule implements HuxerUIPlatformModule.Factory {
    @Override
    public HuxerUIPlatformModule create(
            Context context,
            PlatformPayload options,
            HuxerUIPlatformChannel.Events events) {
        options.requireNull();
        return new Module(context.getApplicationContext());
    }

    private static final class Module implements HuxerUIPlatformModule {
        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION = () -> { };
        private final Context context;

        Module(Context context) {
            this.context = context;
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            try {
                if (!"openUrl".equals(method)) {
                    result.fail(
                            "linecode/external-link/not-implemented",
                            "Unknown external-link method: " + method,
                            PlatformPayload.nullValue());
                    return NO_CANCELLATION;
                }
                Uri uri = Uri.parse(arguments.requireString());
                String scheme = uri.getScheme();
                if (scheme == null
                        || !("https".equalsIgnoreCase(scheme)
                        || "http".equalsIgnoreCase(scheme))) {
                    result.fail(
                            "linecode/external-link/unsupported-url",
                            "Only HTTP and HTTPS links can be opened",
                            PlatformPayload.nullValue());
                    return NO_CANCELLATION;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                result.complete(PlatformPayload.nullValue());
            } catch (RuntimeException error) {
                String message = error.getMessage();
                result.fail(
                        "linecode/external-link/android-error",
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
