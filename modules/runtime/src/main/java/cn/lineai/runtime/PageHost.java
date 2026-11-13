package cn.lineai.runtime;

import android.app.Activity;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Project;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.feature.settings.SettingsHome;
import cn.lineai.ui.tokens.Tokens;

/**
 * Activity callbacks consumed by screen construction outside the activity; the chat slice
 * of the contract lives in feature-chat's {@code ChatHost}.
 */
public interface PageHost extends cn.lineai.feature.chat.ChatHost {

    /** Host activity, used as view context and for system calls. */
    Activity activity();

    /** Design tokens shared by all pages. */
    Tokens tokens();

    /** Application-scoped dependency graph. */
    AppGraph graph();

    /** Navigation back stack shared with the screen host. */
    BackStack navigation();

    /** Selects the settings home entry highlighted when returning from a subpage. */
    void settingsDestination(SettingsHome.Destination destination);

    /** Launches the SAF create-document picker for the full user-data archive. */
    void requestExport();

    /** Launches the SAF create-document picker for the chat-only export. */
    void requestChatExport();

    /** Launches the SAF open-document picker for the user-data archive import. */
    void requestImport();

    /**
     * Opens the bottom-sheet model picker for a tool image model.
     *
     * @param understanding {@code true} for image understanding, {@code false} for generation
     */
    void pickToolModel(boolean understanding);

    /**
     * Persists the tool image model selection and refreshes the tool settings page.
     *
     * @param understanding {@code true} for image understanding, {@code false} for generation
     * @param id selected model id, empty to clear
     */
    void saveToolModel(boolean understanding, String id);

    /** Runs a blocking probe of the given model on an io thread and reports the result. */
    void probeModel(ModelConfig model);

    /** Opens the system dialog requesting the battery optimization exemption. */
    void requestBatteryOptimizationExemption();

    /** Whether the app is already exempt from battery optimizations. */
    boolean isIgnoringBatteryOptimizations();

    /** Starts or stops the keep-alive foreground service per the stored preference. */
    void updateKeepAliveService();

    /** Opens the main navigation drawer. */
    void openDrawer();

    /** Launches the SAF picker for a local GGUF model file. */
    void pickLocalModelFile();

    /** Launches the SAF picker for a skill archive import. */
    void pickSkillArchive();

    /** Imports the given image path as a chat attachment. */
    void addChatImage(String path);

    /** Imports the given file path as a chat attachment. */
    void addChatFile(String path);

    /** Launches the SAF directory picker to register an external workspace project. */
    void openExternalWorkspace();

    /** Opens the system picker granting another app read/write access to the workspace. */
    default void shareWorkspace() {
        unavailable();
    }

    /** Applies the project name to the chat header and stores the label setting. */
    void applyProjectLabel(Project project);

    /** Opens an external URL in the in-app browser page. */
    void openLink(String url);

    /** Shows the generic "not available" toast for routes the build does not support. */
    void unavailable();
}
