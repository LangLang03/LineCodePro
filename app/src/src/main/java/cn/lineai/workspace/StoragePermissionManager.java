package cn.lineai.workspace;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

public final class StoragePermissionManager {
    private final Context context;

    public StoragePermissionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasExternalStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean needsManageAllFilesPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager();
    }

    public String permissionDeniedMessage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return "缺少“管理所有文件”权限，无法访问外部工作区。请在系统设置中允许 LineCode 管理所有文件，然后重试。";
        }
        return "缺少文件读写权限，无法访问外部工作区。请允许 LineCode 读取和写入存储空间后重试。";
    }
}
