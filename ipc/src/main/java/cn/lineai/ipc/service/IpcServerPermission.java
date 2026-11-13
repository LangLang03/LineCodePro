package cn.lineai.ipc.service;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Server-side permission verification helper.
 *
 * <p>Android already enforces {@code <service android:permission="...">} at the system
 * layer. This class offers an optional second check that the calling package declares the
 * corresponding {@code <uses-permission>}, for providers that want stricter access
 * control than the platform gate.
 */
public final class IpcServerPermission {

    private static final String TAG = "IpcServerPermission";

    private IpcServerPermission() {
    }

    /**
     * Reports whether the given package declares the requested permission.
     *
     * @return true when declared; false when the package is absent or has not requested it
     */
    public static boolean callerHasPermission(Context context, String packageName, String permission) {
        if (context == null || packageName == null || packageName.length() == 0
                || permission == null || permission.length() == 0) {
            return false;
        }
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions == null) {
                return false;
            }
            for (String requested : info.requestedPermissions) {
                if (permission.equals(requested)) {
                    return true;
                }
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "calling package not found: " + packageName);
            return false;
        }
    }
}
