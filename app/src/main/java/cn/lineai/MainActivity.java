package cn.lineai;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.UserAgreementRepository;
import cn.lineai.log.ErrorLog;
import cn.lineai.mvp.MainCoordinator;
import cn.lineai.mvp.MainDependencies;
import cn.lineai.ui.MainChatView;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.component.PermissionUiHelper;
import cn.lineai.ui.component.SafPickerDelegate;
import cn.lineai.ui.component.UserAgreementDialog;
import cn.lineai.tool.builtin.PhoneScreenshotCache;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity implements MainChatView.WorkspaceHost {
    private MainCoordinator presenter;
    private MainChatView mainView;
    private MainChatView.DocumentPickCallback documentPickCallback;
    private MainChatView.DocumentCreateCallback documentCreateCallback;
    private SafPickerDelegate safPickerDelegate;
    private PermissionUiHelper permissionUiHelper;
    private Object backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        ErrorLog.init(this);
        PhoneScreenshotCache.clear(this);

        safPickerDelegate = new SafPickerDelegate(this);
        permissionUiHelper = new PermissionUiHelper(this);
        permissionUiHelper.setListener((requestCode, grantResults) -> {
            if (requestCode == PermissionUiHelper.REQUEST_LEGACY_STORAGE && presenter != null) {
                presenter.onStoragePermissionResult();
            } else if (requestCode == PermissionUiHelper.REQUEST_POST_NOTIFICATIONS && presenter != null) {
                presenter.onKeepAliveSettingsChanged();
            }
        });

        // 先设置启动画面占位，避免白屏
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(LineTheme.BG);
        setContentView(splash);

        // 在后台线程构建 MainDependencies + MainCoordinator，
        // 避免大量 DB 初始化阻塞主线程导致 ANR
        new Thread(() -> {
            MainDependencies dependencies = new MainDependencies(this);
            MainCoordinator coordinator = new MainCoordinator(dependencies);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                presenter = coordinator;
                mainView = new MainChatView(this, presenter);
                setContentView(mainView);
                presenter.attachView(mainView);
                registerBackCallback();
            });
        }).start();

        UserAgreementRepository agreement = new UserAgreementRepository(new cn.lineai.data.repository.SettingsRepository(cn.lineai.data.db.LineCodeDatabase.getInstance(this)));
        if (agreement.shouldShow()) {
            UserAgreementDialog.show(this, () -> {
                agreement.setAccepted(true);
                agreement.setVersion(UserAgreementRepository.CURRENT_VERSION);
            }, () -> {
                finishAffinity();
            });
            return;
        }
    }

    @Override
    protected void onPause() {
        if (presenter != null) {
            presenter.onEnterBackground();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Only finish/destroy should cancel generation. Home / multi-task must keep
        // the stream + pending tool reviews alive (KeepAliveService covers process priority).
        if (presenter != null
                && cn.lineai.mvp.ActivityGenerationLifecyclePolicy.shouldStopGenerationOnStop(isFinishing())) {
            presenter.resetGenerationState();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Never cancel on resume: process-death orphans are cleaned by ConversationResumeSanitizer.
        if (presenter != null
                && cn.lineai.mvp.ActivityGenerationLifecyclePolicy.shouldStopGenerationOnStart()) {
            presenter.resetGenerationState();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterBackCallback();
        // destroy() always cancels generation + keep-alive (explicit teardown).
        if (presenter != null) {
            presenter.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (presenter != null) {
            presenter.onStoragePermissionResult();
            if (mainView != null) {
                presenter.onResume(mainView.getCurrentScreenId());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (safPickerDelegate.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        if (requestCode != SafPickerDelegate.REQUEST_OPEN_DOCUMENT_TREE) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            presenter.onExternalProjectPickerCancelled();
            return;
        }
        presenter.onExternalProjectTreePicked(data.getData().toString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionUiHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
        LineTheme.apply(new ThemeSettingsRepository(new cn.lineai.resource.SystemConfigProvider() {
            @Override
            public boolean isDarkModeEnabled() {
                int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            }
            @Override
            public int getSdkInt() { return android.os.Build.VERSION.SDK_INT; }
            @Override
            public String getFilesDirPath() { return getFilesDir().getAbsolutePath(); }
            @Override
            public String getDatabasePath(String name) { return MainActivity.this.getDatabasePath(name).getAbsolutePath(); }
            @Override
            public String getExternalFilesDirPath() {
                java.io.File dir = getExternalFilesDir(null);
                return dir != null ? dir.getAbsolutePath() : "";
            }
        }, new cn.lineai.data.repository.SettingsRepository(cn.lineai.data.db.LineCodeDatabase.getInstance(this))).resolveCurrentPalette());
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
        safPickerDelegate.openDocumentTree(new SafPickerDelegate.TreePickCallback() {
            @Override
            public void onTreePicked(String uri) {
                if (presenter != null) {
                    presenter.onExternalProjectTreePicked(uri);
                }
            }

            @Override
            public void onCancelled() {
                if (presenter != null) {
                    presenter.onExternalProjectPickerCancelled();
                }
            }
        });
    }

    @Override
    public void openManageAllFilesPermissionSettings() {
        permissionUiHelper.openManageAllFilesAccessSettings();
    }

    @Override
    public void requestLegacyStoragePermissions() {
        permissionUiHelper.requestLegacyStoragePermissions();
    }

    @Override
    public void openDocumentPicker(String mimeType, String[] extensions, MainChatView.DocumentPickCallback callback) {
        documentPickCallback = callback;
        safPickerDelegate.openDocument(mimeType, extensions, new SafPickerDelegate.DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                MainChatView.DocumentPickCallback pending = documentPickCallback;
                documentPickCallback = null;
                if (pending != null) {
                    pending.onDocumentPicked(uri, displayName);
                }
            }

            @Override
            public void onCancelled() {
                MainChatView.DocumentPickCallback pending = documentPickCallback;
                documentPickCallback = null;
                if (pending != null) {
                    pending.onDocumentPickCancelled();
                }
            }
        });
    }

    @Override
    public void pickImage(MainChatView.DocumentPickCallback callback) {
        documentPickCallback = callback;
        safPickerDelegate.pickImage(new SafPickerDelegate.DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                MainChatView.DocumentPickCallback pending = documentPickCallback;
                documentPickCallback = null;
                if (pending != null) {
                    pending.onDocumentPicked(uri, displayName);
                }
            }

            @Override
            public void onCancelled() {
                MainChatView.DocumentPickCallback pending = documentPickCallback;
                documentPickCallback = null;
                if (pending != null) {
                    pending.onDocumentPickCancelled();
                }
            }
        });
    }

    @Override
    public void createDocument(String mimeType, String displayName, MainChatView.DocumentCreateCallback callback) {
        documentCreateCallback = callback;
        safPickerDelegate.createDocument(mimeType, displayName, new SafPickerDelegate.DocumentCreateCallback() {
            @Override
            public void onDocumentCreated(String uri, String displayName) {
                MainChatView.DocumentCreateCallback pending = documentCreateCallback;
                documentCreateCallback = null;
                if (pending != null) {
                    pending.onDocumentCreated(uri, displayName);
                }
            }

            @Override
            public void onCancelled() {
                MainChatView.DocumentCreateCallback pending = documentCreateCallback;
                documentCreateCallback = null;
                if (pending != null) {
                    pending.onDocumentCreateCancelled();
                }
            }
        });
    }
}
