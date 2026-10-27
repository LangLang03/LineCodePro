package cn.lineai.ipc.service;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * 服务端权限校验工具。
 *
 * <p>Android 系统已通过 {@code <service android:permission="...">} 在系统层做了
 * 强制校验；本类提供可选的二次校验：检查调用方是否在 manifest 中声明了对应
 * {@code <uses-permission>}，便于服务端做更严格的访问控制。
 */
public final class IpcServerPermission {

    private static final String TAG = "IpcServerPermission";

    private IpcServerPermission() {
    }

    /**
     * 检查给定包名的应用是否声明了指定权限。
     *
     * @return true 表示已声明；包未安装或权限未声明均返回 false
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
            Log.w(TAG, "调用方包未找到: " + packageName);
            return false;
        }
    }
}
