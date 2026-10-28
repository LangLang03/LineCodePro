package cn.lineai.platform;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

/** Reads whether a given accessibility service is enabled. */
public final class AccessibilitySupport {

    private AccessibilitySupport() {
    }

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
