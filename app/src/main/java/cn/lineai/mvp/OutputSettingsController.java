package cn.lineai.mvp;

import cn.lineai.model.OutputSettings;

public interface OutputSettingsController {
    OutputSettings getOutputSettings();

    void onCodeWrapChanged(boolean enabled);

    void onProcessAutoExpandChanged(boolean enabled);

    void onBrowserModeChanged(String mode);

    void onBrowserJavaScriptChanged(boolean enabled);

    void onAllowAnyHttpChanged(boolean enabled);

    void onBypassPathProtectionChanged(boolean enabled);
}
