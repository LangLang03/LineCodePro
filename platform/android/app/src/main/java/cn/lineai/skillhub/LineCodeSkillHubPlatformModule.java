package cn.lineai.skillhub;

import android.content.Context;
import android.webkit.CookieManager;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Android-only bridge for the system WebView cookie jar and share sheet. */
public final class LineCodeSkillHubPlatformModule
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
        private interface Invocation {
            HuxerUIPlatformChannel.Cancellation invoke(
                    PlatformPayload arguments,
                    HuxerUIPlatformChannel.Result result);
        }

        private static final String SKILL_HUB_ORIGIN = "https://skillhub.cn/";
        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION =
                () -> { };

        private final CookieManager cookies;
        private final Map<String, Invocation> invocations = new HashMap<>();

        Module(Context context) {
            cookies = CookieManager.getInstance();
            invocations.put("readSessionCookie", this::readSessionCookie);
            invocations.put("clearSessionCookies", this::clearSessionCookies);
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            Invocation invocation = invocations.get(method);
            if (invocation == null) {
                result.fail(
                        "linecode/skill-hub/not-implemented",
                        "Unknown SkillHub platform method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                return invocation.invoke(arguments, result);
            } catch (RuntimeException error) {
                String message = error.getMessage();
                result.fail(
                        "linecode/skill-hub/android-error",
                        message == null ? error.getClass().getSimpleName() : message,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
        }

        @Override
        public void dispose() {
            invocations.clear();
        }

        private HuxerUIPlatformChannel.Cancellation readSessionCookie(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            arguments.requireNull();
            String value = cookies.getCookie(SKILL_HUB_ORIGIN);
            result.complete(PlatformPayload.string(value == null ? "" : value));
            return NO_CANCELLATION;
        }

        private HuxerUIPlatformChannel.Cancellation clearSessionCookies(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            arguments.requireNull();
            AtomicBoolean cancelled = new AtomicBoolean(false);
            String current = cookies.getCookie(SKILL_HUB_ORIGIN);
            if (current == null || current.trim().isEmpty()) {
                result.complete(PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            String[] pairs = current.split(";");
            AtomicInteger remaining = new AtomicInteger(pairs.length * 3);
            android.webkit.ValueCallback<Boolean> completed = ignored -> {
                if (remaining.decrementAndGet() == 0) {
                    cookies.flush();
                    if (!cancelled.get()) {
                        result.complete(PlatformPayload.nullValue());
                    }
                }
            };
            for (String pair : pairs) {
                int separator = pair.indexOf('=');
                String name = (separator < 0 ? pair : pair.substring(0, separator)).trim();
                String expired = name
                        + "=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/";
                cookies.setCookie(SKILL_HUB_ORIGIN, expired, completed);
                cookies.setCookie("https://api.skillhub.cn/", expired, completed);
                cookies.setCookie(
                        SKILL_HUB_ORIGIN,
                        expired + "; Domain=.skillhub.cn",
                        completed);
            }
            return () -> cancelled.set(true);
        }

    }
}
