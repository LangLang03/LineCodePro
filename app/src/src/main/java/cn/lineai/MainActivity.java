package cn.lineai;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.mvp.MainCoordinator;
import cn.lineai.ui.MainChatView;
import cn.lineai.ui.theme.LineTheme;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity implements MainChatView.WorkspaceHost {
    private static final int REQUEST_OPEN_WORKSPACE_TREE = 7001;
    private static final int REQUEST_LEGACY_STORAGE = 7002;
    private static final int REQUEST_OPEN_DOCUMENT = 7003;
    private static final int REQUEST_CREATE_DOCUMENT = 7004;

    private MainCoordinator presenter;
    private MainChatView mainView;
    private MainChatView.DocumentPickCallback documentPickCallback;
    private MainChatView.DocumentCreateCallback documentCreateCallback;
    private Object backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        presenter = new MainCoordinator(this);
        mainView = new MainChatView(this, presenter);
        setContentView(mainView);
        presenter.attachView(mainView);
        registerBackCallback();
    }

    @Override
    protected void onDestroy() {
        unregisterBackCallback();
        presenter.destroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (presenter != null) {
            presenter.onStoragePermissionResult();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_DOCUMENT) {
            handleDocumentResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_CREATE_DOCUMENT) {
            handleCreateDocumentResult(resultCode, data);
            return;
        }
        if (requestCode != REQUEST_OPEN_WORKSPACE_TREE) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            presenter.onExternalProjectPickerCancelled();
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags();
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            takePersistableReadPermission(uri);
        }
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            takePersistableWritePermission(uri);
        }
        presenter.onExternalProjectTreePicked(uri.toString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE && presenter != null) {
            presenter.onStoragePermissionResult();
        }
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (handleBackNavigation()) {
            return;
        }
        super.onBackPressed();
    }

    private boolean handleBackNavigation() {
        return mainView != null && mainView.handleBackPressed();
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerPlatformBackCallback();
        }
    }

    private void unregisterBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            unregisterPlatformBackCallback();
        }
    }

    @SuppressLint("NewApi")
    private void registerPlatformBackCallback() {
        android.window.OnBackInvokedCallback callback = () -> {
            if (!handleBackNavigation()) {
                finish();
            }
        };
        backCallback = callback;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
        );
    }

    @SuppressLint("NewApi")
    private void unregisterPlatformBackCallback() {
        Object callback = backCallback;
        backCallback = null;
        if (callback instanceof android.window.OnBackInvokedCallback) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) callback
            );
        }
    }

    private void configureWindow() {
        new ThemeSettingsRepository(this).applyCurrentTheme();
        Window window = getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setStatusBarColor(LineTheme.BG);
        window.setNavigationBarColor(LineTheme.BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        int flags = window.getDecorView().getSystemUiVisibility();
        if (isLightColor(LineTheme.BG)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isLightColor(LineTheme.BG)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private boolean isLightColor(int color) {
        double red = Color.red(color) / 255.0;
        double green = Color.green(color) / 255.0;
        double blue = Color.blue(color) / 255.0;
        double luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
        return luminance > 0.64;
    }

    public void recreateMainView(String screenId) {
        configureWindow();
        if (presenter == null) {
            return;
        }
        if (mainView != null) {
            presenter.detachView();
        }
        mainView = new MainChatView(this, presenter);
        setContentView(mainView);
        presenter.attachView(mainView);
        if (screenId != null && screenId.length() > 0) {
            mainView.showScreen(screenId);
        }
    }

    @Override
    public void openExternalProjectPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_WORKSPACE_TREE);
    }

    @Override
    public void openManageAllFilesPermissionSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    public void requestLegacyStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openManageAllFilesPermissionSettings();
            return;
        }
        requestPermissions(new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQUEST_LEGACY_STORAGE);
    }

    @Override
    public void openDocumentPicker(String mimeType, String[] extensions, MainChatView.DocumentPickCallback callback) {
        documentPickCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType == null || mimeType.length() == 0 ? "*/*" : mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (extensions != null && extensions.length > 0) {
            intent.putExtra(Intent.EXTRA_TITLE, extensions[0]);
        }
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
    }

    @Override
    public void createDocument(String mimeType, String displayName, MainChatView.DocumentCreateCallback callback) {
        documentCreateCallback = callback;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType == null || mimeType.length() == 0 ? "application/octet-stream" : mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, displayName == null || displayName.length() == 0 ? "LineCode.linecode" : displayName);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
    }

    private void handleDocumentResult(int resultCode, Intent data) {
        MainChatView.DocumentPickCallback callback = documentPickCallback;
        documentPickCallback = null;
        if (callback == null) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            callback.onDocumentPickCancelled();
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags != 0) {
            takePersistableReadPermission(uri);
        }
        callback.onDocumentPicked(uri.toString(), displayName(uri));
    }

    private void handleCreateDocumentResult(int resultCode, Intent data) {
        MainChatView.DocumentCreateCallback callback = documentCreateCallback;
        documentCreateCallback = null;
        if (callback == null) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            callback.onDocumentCreateCancelled();
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags();
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            takePersistableReadPermission(uri);
        }
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            takePersistableWritePermission(uri);
        }
        callback.onDocumentCreated(uri.toString(), displayName(uri));
    }

    private void takePersistableReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private void takePersistableWritePermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private String displayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && name.length() > 0) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String path = uri.getLastPathSegment();
        return path == null ? "skill.zip" : path;
    }
}
