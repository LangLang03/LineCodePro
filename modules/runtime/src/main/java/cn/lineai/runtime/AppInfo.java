package cn.lineai.runtime;

import android.app.Activity;
import android.content.pm.PackageInfo;

/** App label and version metadata for the About screen. */
public final class AppInfo {

    private AppInfo() {
    }

    public static String label(Activity activity) {
        CharSequence label = activity.getApplicationInfo().loadLabel(activity.getPackageManager());
        return label == null ? "" : label.toString();
    }

    public static String versionName(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception error) {
            return "";
        }
    }

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
