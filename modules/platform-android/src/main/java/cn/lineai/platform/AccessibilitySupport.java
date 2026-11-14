package cn.lineai.platform;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Reads whether a given accessibility service is enabled.
 *
 * <p>Part of the platform module's ownership boundary: system-settings introspection lives here so
 * features ask this class instead of reading {@link Settings.Secure} themselves. The check matches
 * both the fully qualified component name and the dot-prefixed short form Android may persist.</p>
 */
public final class AccessibilitySupport {

    /** Not instantiable; the check is static. */
    private AccessibilitySupport() {
    }

    /**
     * Reports whether an accessibility service of this app is currently enabled by the user.
     *
     * @param context context used to read secure settings
     * @param serviceClass binary or simple class name of the service, as declared in the manifest
     * @return true when the enabled-services setting lists this app's service under either the
     *     fully qualified or the dot-prefixed component form
     */
    public static boolean isServiceEnabled(Context context, String serviceClass) {
        String expected = context.getPackageName() + "/" + serviceClass;
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        for (String entry : enabled.split(":")) {
            if (entry.equalsIgnoreCase(expected) || entry.equalsIgnoreCase(
                    context.getPackageName() + "/." + serviceClass)) {
                return true;
            }
        }
        return false;
    }
}
