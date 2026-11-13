package cn.lineai.feature.chat;

import android.app.Activity;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.core.service.SettingsAction;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.ProjectStore;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.ui.tokens.Tokens;

/**
 * The chat-side slice of the activity host: what chat sheets and export actions need.
 * Implemented by the app shell (via runtime's PageHost); keeps feature-chat free of
 * the runtime composition root.
 */
public interface ChatHost {

    /** Hosting activity, used for toasts, SAF results and system UI. */
    Activity activity();

    /** Design tokens for chat-owned surfaces. */
    Tokens tokens();

    /** Navigation back stack for host-owned pages. */
    BackStack navigation();

    /** Graceful "not supported here" fallback for optional host capabilities. */
    void unavailable();

    /** The chat controller driving this screen. */
    ChatController chat();

    /** Model store for capability lookups and model selection. */
    ModelStore models();

    /** Project store for workspace context. */
    ProjectStore projects();

    /** Settings store for chat preferences. */
    SettingsStore settings();

    /** Dispatches a settings action without exposing the settings store or presenter state. */
    void dispatchSettings(SettingsAction action);

    /** Scheduler for io/main dispatch from chat code. */
    Scheduler scheduler();

    /** Opens the SAF export flow for the chat transcript. */
    void requestChatExport();

    /** Attaches a copied image file to the composer. */
    void addChatImage(String path);

    /** Opens the system image picker for the composer long-press action. */
    default void pickChatImage() {
        unavailable();
    }

    /** Attaches a copied arbitrary file to the composer. */
    void addChatFile(String path);

    /** Opens the tutorial page (owned by feature-settings). */
    void openTutorial();

    /** Opens the settings home. */
    void openSettings();

    /** Opens the workspace projects page (owned by feature-workspace). */
    void openWorkspaceProjects();

    /** Opens the workspace attachment tree picker used by the composer plus button. */
    default void showChatAttachmentPicker() {
        unavailable();
    }
}
