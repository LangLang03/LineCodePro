package cn.lineai.mvp;

public interface MainUiController extends ChatController,
        WorkspaceController,
        SettingsController,
        ExtensionController,
        ModelController,
        NavigationController {
    void attachView(MainContract.View view);

    void detachView();

    void destroy();
}
