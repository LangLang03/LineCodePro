package cn.lineai.runtime;

import android.app.Activity;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Project;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.feature.settings.SettingsHome;
import cn.lineai.ui.tokens.Tokens;

/** Activity callbacks used by screen construction; the chat slice lives in feature-chat. */
public interface PageHost extends cn.lineai.feature.chat.ChatHost {

    Activity activity();

    Tokens tokens();

    AppGraph graph();

    BackStack navigation();

    void settingsDestination(SettingsHome.Destination destination);

    void requestExport();

    void requestChatExport();

    void requestImport();

    void pickToolModel(boolean understanding);

    void saveToolModel(boolean understanding, String id);

    void probeModel(ModelConfig model);

    void requestBatteryOptimizationExemption();

    boolean isIgnoringBatteryOptimizations();

    void updateKeepAliveService();

    void openDrawer();

    void pickLocalModelFile();

    void pickSkillArchive();

    void addChatImage(String path);

    void addChatFile(String path);

    void openExternalWorkspace();

    /** Opens the system picker for another app to read/write the LineCode workspace. */
    default void shareWorkspace() {
        unavailable();
    }

    void applyProjectLabel(Project project);

    void openLink(String url);

    void unavailable();
}
