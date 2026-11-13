package cn.lineai.runtime;

import android.app.Activity;
import android.content.pm.PackageInfo;

/** App label and version metadata for the About screen. */
public final class AppInfo {

    /** Utility class; instantiation is not permitted. */
    private AppInfo() {
    }

    /**
     * Reads the application label from the package manager.
     *
     * @param activity activity used to reach the application info
     * @return the localized label, or the empty string when unavailable
     */
    public static String label(Activity activity) {
        CharSequence label = activity.getApplicationInfo().loadLabel(activity.getPackageManager());
        return label == null ? "" : label.toString();
    }

    /**
     * Reads the version name from the package manager.
     *
     * @param activity activity used to reach the package info
     * @return the version name, or the empty string on lookup failure
     */
    public static String versionName(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception error) {
            return "";
        }
    }

    /**
     * Reads the version code, using the wide variant on API 28 and later.
     *
     * @param activity activity used to reach the package info
     * @return the version code, or 0 on lookup failure
     */
    public static long versionCode(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception error) {
            return 0;
        }
    }
}
