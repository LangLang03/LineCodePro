package cn.lineai.ipc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import java.util.ArrayList;
import java.util.List;

public final class IpcProviderScanner {

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
