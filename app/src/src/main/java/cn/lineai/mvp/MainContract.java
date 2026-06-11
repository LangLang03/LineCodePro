package cn.lineai.mvp;

import cn.lineai.model.ChatUiState;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.SheetOption;
import java.util.List;

public interface MainContract {
    interface View {
        void render(ChatUiState state);

        void setComposerDraft(String text);

        void setComposerDraft(String text, List<InputAttachment> attachments);

        void showDrawer();

        void showSheet(String title, List<SheetOption> options);

        void showFileActionDialog(String title, String subtitle, List<SheetOption> options);

        void showInputDialog(String title, String message, String initialValue, String actionId);

        void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId);

        void showDirectoryPicker(String title, String subtitle, FileTreeNode tree, String selectedPath, boolean loading, String message);

        void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source);

        void hideOverlays();

        void hideDirectoryPicker();

        void hideAttachmentPicker();

        void showScreen(String screenId);

        void showChatScreen();

        void openExternalProjectPicker();

        void openLineCodeImportPicker();

        void openLineCodeExportPicker(String fileName);

        void openManageAllFilesPermissionSettings();

        void requestLegacyStoragePermissions();

        void openExternalUrl(String url);

        void recreateForTheme(String screenId);
    }
}
