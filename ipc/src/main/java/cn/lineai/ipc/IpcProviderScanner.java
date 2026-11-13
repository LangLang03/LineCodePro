package cn.lineai.ipc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Device-wide discovery of provider services for one {@link IpcProviderType}.
 *
 * <p>Uses the package manager to resolve the type's intent action and keeps only
 * services whose declaring package also requests the type's custom permission, so the
 * result can be turned into bindable {@link IpcProviderConfig}s directly.
 */
public final class IpcProviderScanner {

    /**
     * Returns every installed provider service matching the given type.
     *
     * @param context context used to reach the package manager; null yields an empty list
     * @param type the provider kind to scan for; null yields an empty list
     * @return the compliant services, possibly empty; never null
     */
    public List<ScannedProvider> scan(Context context, IpcProviderType type) {
        List<ScannedProvider> result = new ArrayList<>();
        if (context == null || type == null) {
            return result;
        }
        Intent intent = new Intent(type.getIntentAction());
        List<ResolveInfo> services = context.getPackageManager()
                .queryIntentServices(intent, 0);
        if (services == null) {
            return result;
        }
        for (ResolveInfo info : services) {
            ServiceInfo serviceInfo = info.serviceInfo;
            if (serviceInfo == null) {
                continue;
            }
            String packageName = serviceInfo.packageName;
            String serviceClass = serviceInfo.name;
            String label = info.loadLabel(context.getPackageManager()).toString();
            if (!hasPermission(context, packageName, type.getPermissionName())) {
                continue;
            }
            result.add(new ScannedProvider(packageName, serviceClass, label, type.getId()));
        }
        return result;
    }

    /**
     * Reports whether the declaring package requests the required permission. An empty
     * permission name passes so unguarded provider kinds are not filtered out.
     */
    private boolean hasPermission(Context context, String packageName, String permission) {
        if (permission == null || permission.length() == 0) {
            return true;
        }
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (packageInfo.requestedPermissions == null) {
                return false;
            }
            for (String requested : packageInfo.requestedPermissions) {
                if (permission.equals(requested)) {
                    return true;
                }
            }
            return false;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
