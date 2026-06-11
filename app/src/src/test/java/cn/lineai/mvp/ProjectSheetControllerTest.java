package cn.lineai.mvp;

import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.SheetOption;
import cn.lineai.workspace.WorkspacePaths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public final class ProjectSheetControllerTest {
    @Test
    public void localSheetIncludesProjectsOpenCreateAndStorage() {
        Fixture fixture = new Fixture();
        fixture.store.projects.add(project(WorkspacePaths.DEFAULT_PROJECT_ID, "Home", "/home/me/.linecode", WorkspacePaths.SOURCE_DEFAULT));
        fixture.store.projects.add(project("p2", "App", "/sdcard/App", WorkspacePaths.SOURCE_EXTERNAL));
        fixture.store.selected = fixture.store.projects.get(1);
        fixture.host.executionMode = ToolSettingsRepository.EXECUTION_LOCAL;
        fixture.host.externalStorageAccess = true;

        ProjectSheetController.ProjectSheet sheet = fixture.controller.buildProjectSheet();

        Assert.assertEquals("工作区", sheet.getTitle());
        Assert.assertEquals(5, sheet.getOptions().size());
        Assert.assertTrue(sheet.getOptions().get(1).isSelected());
        Assert.assertEquals("", sheet.getOptions().get(0).getDeleteActionId());
        Assert.assertEquals("project:delete:p2", sheet.getOptions().get(1).getDeleteActionId());
        Assert.assertEquals("project:open_local_saf", sheet.getOptions().get(2).getId());
        Assert.assertEquals("storage:manage_all_files", sheet.getOptions().get(4).getId());
        Assert.assertTrue(sheet.getOptions().get(4).isSelected());
    }

    @Test
    public void remoteSshSheetOmitsLocalOpenAndStorageForNonTermuxHost() {
        Fixture fixture = new Fixture();
        fixture.host.executionMode = ToolSettingsRepository.EXECUTION_SSH;
        fixture.host.termuxSshHost = false;
        fixture.store.projects.add(project("ssh:default", "SSH Home", "", WorkspacePaths.SOURCE_SSH));
        fixture.store.selected = fixture.store.projects.get(0);

        ProjectSheetController.ProjectSheet sheet = fixture.controller.buildProjectSheet();

        Assert.assertEquals("工作区 SSH", sheet.getTitle());
        Assert.assertEquals(2, sheet.getOptions().size());
        SheetOption project = sheet.getOptions().get(0);
        Assert.assertEquals("SSH 登录目录", project.getDescription());
        Assert.assertEquals("", project.getDeleteActionId());
        Assert.assertEquals("project:create", sheet.getOptions().get(1).getId());
    }

    @Test
    public void termuxSshSheetIncludesLocalOpenAndStoragePermission() {
        Fixture fixture = new Fixture();
        fixture.host.executionMode = ToolSettingsRepository.EXECUTION_SSH;
        fixture.host.termuxSshHost = true;
        fixture.host.externalStorageAccess = false;
        fixture.host.storageMessage = "未授权";
        fixture.store.projects.add(project("ssh:default", "SSH Home", "", WorkspacePaths.SOURCE_SSH));

        ProjectSheetController.ProjectSheet sheet = fixture.controller.buildProjectSheet();

        Assert.assertEquals(4, sheet.getOptions().size());
        Assert.assertEquals("project:open_local_saf", sheet.getOptions().get(1).getId());
        Assert.assertEquals("选择本机目录，并作为 SSH 工作区路径使用", sheet.getOptions().get(1).getDescription());
        Assert.assertEquals("project:create", sheet.getOptions().get(2).getId());
        Assert.assertEquals("storage:manage_all_files", sheet.getOptions().get(3).getId());
        Assert.assertEquals("未授权", sheet.getOptions().get(3).getDescription());
        Assert.assertFalse(sheet.getOptions().get(3).isSelected());
    }

    private static final class Fixture {
        private final FakeProjectStore store = new FakeProjectStore();
        private final FakeHost host = new FakeHost();
        private final ProjectSheetController controller = new ProjectSheetController(store, host);
    }

    private static final class FakeProjectStore implements ProjectSheetController.ProjectStore {
        private final ArrayList<ProjectRecord> projects = new ArrayList<>();
        private ProjectRecord selected;

        @Override
        public ProjectRecord getSelectedProject(String executionMode) {
            return selected;
        }

        @Override
        public List<ProjectRecord> getProjects(String executionMode) {
            return projects;
        }
    }

    private static final class FakeHost implements ProjectSheetController.Host {
        private String executionMode = ToolSettingsRepository.EXECUTION_LOCAL;
        private boolean termuxSshHost;
        private boolean externalStorageAccess;
        private String storageMessage = "";

        @Override
        public String executionMode() {
            return executionMode;
        }

        @Override
        public boolean isTermuxSshHost() {
            return termuxSshHost;
        }

        @Override
        public boolean hasExternalStorageAccess() {
            return externalStorageAccess;
        }

        @Override
        public String storagePermissionMessage() {
            return storageMessage;
        }
    }

    private static ProjectRecord project(String id, String label, String path, String source) {
        return new ProjectRecord(id, label, path, source, "", false, 0L, 0L);
    }
}
