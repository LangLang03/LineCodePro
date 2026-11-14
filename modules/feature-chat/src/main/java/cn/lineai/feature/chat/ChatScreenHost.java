package cn.lineai.feature.chat;

/**
 * Application-owned commands the chat screen forwards; the view never navigates, opens
 * sheets or touches stores itself. Kept in its own file so the screen stays within the
 * line budget.
 */
/** Application-owned commands the screen forwards; the view never navigates itself. */
public interface ChatScreenHost {
    /** Opens the model configuration page. */
    void onOpenModelSettings();

    /** Opens the settings home. */
    void onOpenSettings();

    /** Opens the reasoning picker. */
    void onReasoning();

    /** Opens the more-options sheet. */
    void onMore();

    /** Opens the project picker. */
    void onProject();

    /** Opens the permission-mode sheet. */
    void onPermission();

    /** Legacy attach entry (system image picker). */
    void onAttach();

    /** Legacy file attach entry (workspace picker). */
    void onAttachFile();

    /** Opens the model-picker sheet; falls back to the models page when no model is set. */
    void onModelPicker();

    /** Argument-aware tool presentation so cards show the real path/command/query. */
    default cn.lineai.tool.api.ToolPresentation toolPresentation(String name,
                                                                 String argumentsJson) {
        return null;
    }

    /** Opens the context-usage sheet. */
    void onContextUsage();

    /** Opens the chat-mode picker. */
    void onChatMode();

    /** Applies a chat mode typed as a slash command. */
    void setChatMode(String mode);

    /** Opens a workspace file selected in the drawer tree. */
    void openFile(String relativePath);

    /** Label shown in the drawer's files tab. */
    String projectLabel();

    /** Path shown under the project label. */
    String projectPath();

    /** Switches the displayed conversation to a sub agent session; empty id means root. */
    default void onSelectAgent(String agentId) { }

    /** Loads the whole workspace tree for the drawer and the attachment picker. */
    default void loadWorkspaceTree(java.util.function.Consumer<DrawerPanel.FileNode> callback) {
        callback.accept(null);
    }

    /** Opens the workspace attachment tree (composer plus menu row). */
    default void onAttachmentPicker() {
        onAttachFile();
    }

    /** Model capability switches for the composer menu and reasoning lock. */
    default ChatCapabilities capabilities() {
        return ChatCapabilities.of(null);
    }

    /** Opens the system video picker (only when the model supports video input). */
    default void onVideoPicker() {
        onAttach();
    }

    /** Long press on the plus button: opens the system image picker. */
    default void onImagePicker() {
        onAttach();
    }

    default boolean projectRemovable() {
        return false;
    }

    default void onProjectRemoveRequested() {
        onProject();
    }

    /** Optional persisted file-diff lookup for write cards; {@code null} falls back. */
    default cn.lineai.feature.chat.toolcall.ToolDiffLookup.Snapshot toolDiff(String diffId) {
        return null;
    }
}
