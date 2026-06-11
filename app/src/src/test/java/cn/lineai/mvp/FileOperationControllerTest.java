package cn.lineai.mvp;

import cn.lineai.model.SheetOption;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;

public final class FileOperationControllerTest {
    @Test
    public void requestDeleteBuildsConfirmationDialog() {
        Fixture fixture = new Fixture();

        fixture.controller.requestDeleteFileNode("/repo/src/Main.java");

        Assert.assertEquals("确认删除", fixture.host.dialogTitle);
        Assert.assertTrue(fixture.host.dialogMessage.contains("\"Main.java\""));
        Assert.assertEquals("file:delete:/repo/src/Main.java", fixture.host.dialogActionId);
    }

    @Test
    public void fileActionsForDirectoryIncludePasteWhenClipboardMatchesMode() {
        Fixture fixture = new Fixture();
        fixture.controller.copyFileNode("/repo/file.txt");

        fixture.controller.showFileNodeActions("/repo", "repo", true, false);

        Assert.assertEquals("repo", fixture.host.fileActionTitle);
        Assert.assertEquals("/repo", fixture.host.fileActionSubtitle);
        Assert.assertEquals("file:create_file:/repo", fixture.host.fileActionOptions.get(0).getId());
        Assert.assertEquals("file:paste:/repo", fixture.host.fileActionOptions.get(2).getId());
        Assert.assertEquals("file.txt", fixture.host.fileActionOptions.get(2).getDescription());
    }

    @Test
    public void fileActionsForRootDirectoryHideDangerousRootActions() {
        Fixture fixture = new Fixture();

        fixture.controller.showFileNodeActions("/repo", "repo", true, true);

        Assert.assertEquals("工作区根目录", fixture.host.fileActionTitle);
        Assert.assertEquals(2, fixture.host.fileActionOptions.size());
        Assert.assertEquals("file:create_file:/repo", fixture.host.fileActionOptions.get(0).getId());
        Assert.assertEquals("file:create_folder:/repo", fixture.host.fileActionOptions.get(1).getId());
    }

    @Test
    public void createFileUsesLocalStoreAndRenders() {
        Fixture fixture = new Fixture();

        fixture.controller.createFileFromInput("/repo", "Main.java");

        Assert.assertEquals("/repo", fixture.localStore.createFileParent);
        Assert.assertEquals("Main.java", fixture.localStore.createFileName);
        Assert.assertEquals("/repo", fixture.host.expandedPath);
        Assert.assertEquals("", fixture.host.sshRefreshPath);
        Assert.assertEquals(1, fixture.host.renderCount);
    }

    @Test
    public void createFolderUsesSshStoreAndRefreshesSshTree() {
        Fixture fixture = new Fixture();
        fixture.host.sshMode = true;

        fixture.controller.createFolderFromInput("/repo", "src");

        Assert.assertEquals("/repo", fixture.sshStore.createDirectoryParent);
        Assert.assertEquals("src", fixture.sshStore.createDirectoryName);
        Assert.assertEquals("/repo", fixture.host.expandedPath);
        Assert.assertEquals("/repo", fixture.host.sshRefreshPath);
        Assert.assertEquals(1, fixture.host.renderCount);
    }

    @Test
    public void renameUsesParentPathForRefresh() {
        Fixture fixture = new Fixture();

        fixture.controller.renameFileNodeFromInput("/repo/src/Old.java", "New.java");

        Assert.assertEquals("/repo/src/Old.java", fixture.localStore.renamePath);
        Assert.assertEquals("New.java", fixture.localStore.renameName);
        Assert.assertEquals("/repo/src", fixture.host.expandedPath);
    }

    @Test
    public void pasteIsIgnoredWhenClipboardModeDiffers() {
        Fixture fixture = new Fixture();

        fixture.controller.copyFileNode("/repo/file.txt");
        fixture.host.sshMode = true;
        fixture.controller.pasteFileNode("/remote");

        Assert.assertEquals("", fixture.localStore.copySource);
        Assert.assertEquals("", fixture.sshStore.copySource);
        Assert.assertEquals(0, fixture.host.renderCount);
    }

    @Test
    public void pasteUsesClipboardPathAndTargetDirectory() {
        Fixture fixture = new Fixture();

        fixture.controller.copyFileNode("/repo/file.txt");
        Assert.assertTrue(fixture.controller.canPasteInto(true));
        Assert.assertEquals("file.txt", fixture.controller.clipboardName());

        fixture.controller.pasteFileNode("/repo/subdir");

        Assert.assertEquals("/repo/file.txt", fixture.localStore.copySource);
        Assert.assertEquals("/repo/subdir", fixture.localStore.copyTarget);
        Assert.assertEquals("/repo/subdir", fixture.host.expandedPath);
        Assert.assertEquals(1, fixture.host.renderCount);
    }

    @Test
    public void failureShowsNoticeWithoutRender() {
        Fixture fixture = new Fixture();
        fixture.localStore.error = new IllegalStateException("boom");

        fixture.controller.deleteFileNode("/repo/file.txt");

        Assert.assertEquals("文件操作失败: boom", fixture.host.notice);
        Assert.assertEquals(0, fixture.host.renderCount);
    }

    private static final class Fixture {
        private final FakeFileStore localStore = new FakeFileStore();
        private final FakeFileStore sshStore = new FakeFileStore();
        private final FakeHost host = new FakeHost();
        private final FileOperationController controller = new FileOperationController(
                localStore,
                sshStore,
                host,
                (name, runnable) -> runnable.run(),
                Runnable::run
        );
    }

    private static final class FakeFileStore implements FileOperationController.FileStore {
        private Exception error;
        private String createFileParent = "";
        private String createFileName = "";
        private String createDirectoryParent = "";
        private String createDirectoryName = "";
        private String renamePath = "";
        private String renameName = "";
        private String deletePath = "";
        private String copySource = "";
        private String copyTarget = "";

        @Override
        public void createFile(String parentPath, String name) throws Exception {
            throwIfNeeded();
            createFileParent = parentPath;
            createFileName = name;
        }

        @Override
        public void createDirectory(String parentPath, String name) throws Exception {
            throwIfNeeded();
            createDirectoryParent = parentPath;
            createDirectoryName = name;
        }

        @Override
        public void rename(String path, String newName) throws Exception {
            throwIfNeeded();
            renamePath = path;
            renameName = newName;
        }

        @Override
        public void delete(String path) throws Exception {
            throwIfNeeded();
            deletePath = path;
        }

        @Override
        public void copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
            throwIfNeeded();
            copySource = sourcePath;
            copyTarget = targetDirectoryPath;
        }

        private void throwIfNeeded() throws Exception {
            if (error != null) {
                throw error;
            }
        }
    }

    private static final class FakeHost implements FileOperationController.Host {
        private boolean sshMode;
        private String dialogTitle = "";
        private String dialogMessage = "";
        private String dialogActionId = "";
        private String fileActionTitle = "";
        private String fileActionSubtitle = "";
        private ArrayList<SheetOption> fileActionOptions = new ArrayList<>();
        private String expandedPath = "";
        private String sshRefreshPath = "";
        private int renderCount;
        private String notice = "";

        @Override
        public boolean isSshExecutionMode() {
            return sshMode;
        }

        @Override
        public void showInputDialog(String title, String message, String initialValue, String actionId) {
            dialogTitle = title;
            dialogMessage = message;
            dialogActionId = actionId;
        }

        @Override
        public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
            dialogTitle = title;
            dialogMessage = message;
            dialogActionId = actionId;
        }

        @Override
        public void showFileActionDialog(String title, String subtitle, ArrayList<SheetOption> options) {
            fileActionTitle = title;
            fileActionSubtitle = subtitle;
            fileActionOptions = options;
        }

        @Override
        public void addExpandedPath(String path) {
            expandedPath = path;
        }

        @Override
        public void refreshSshDirectoryAfterFileOperation(String path) {
            sshRefreshPath = path;
        }

        @Override
        public void render() {
            renderCount++;
        }

        @Override
        public void showNotice(String text) {
            notice = text;
        }

        @Override
        public String basename(String path) {
            String value = path == null ? "" : path;
            int slash = value.lastIndexOf('/');
            return slash >= 0 ? value.substring(slash + 1) : value;
        }

        @Override
        public String parentPath(String path) {
            String value = path == null ? "" : path;
            int slash = value.lastIndexOf('/');
            return slash <= 0 ? "" : value.substring(0, slash);
        }
    }
}
