package cn.lineai.mvp;

public interface NavigationController {
    void onMenuClick();

    void onDialogInputSubmitted(String actionId, String value);

    void onDialogConfirmed(String actionId);

    void onSheetOptionSelected(String id);

    void onScreenBack();

    void onScreenBackFrom(String screenId);

    void onOpenUrl(String url);

    void showModelManagement();
}
