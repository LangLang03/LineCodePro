package cn.lineai.mvp;

import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.SheetOption;
import cn.lineai.workspace.WorkspacePaths;
import java.util.ArrayList;
import java.util.List;

public final class ProjectSheetController {
    static final class ProjectSheet {
        private final String title;
        private final ArrayList<SheetOption> options;

        ProjectSheet(String title, ArrayList<SheetOption> options) {
            this.title = title;
            this.options = options;
        }

        String getTitle() {
            return title;
        }

        ArrayList<SheetOption> getOptions() {
            return options;
        }
    }

    interface Host {
        String executionMode();

        boolean isTermuxSshHost();

        boolean hasExternalStorageAccess();

        String storagePermissionMessage();
    }

    interface ProjectStore {
        ProjectRecord getSelectedProject(String executionMode);

        List<ProjectRecord> getProjects(String executionMode);
    }

    private static final class RepositoryProjectStore implements ProjectStore {
        private final ProjectRepository repository;

        RepositoryProjectStore(ProjectRepository repository) {
            this.repository = repository;
        }

        @Override
        public ProjectRecord getSelectedProject(String executionMode) {
            return repository.getSelectedProject(executionMode);
        }

        @Override
        public List<ProjectRecord> getProjects(String executionMode) {
            return repository.getProjects(executionMode);
        }
    }

    private final ProjectStore projectStore;
    private final Host host;

    public ProjectSheetController(ProjectRepository projectRepository, Host host) {
        this(new RepositoryProjectStore(projectRepository), host);
    }

    ProjectSheetController(ProjectStore projectStore, Host host) {
        this.projectStore = projectStore;
        this.host = host;
    }

    public ProjectSheet buildProjectSheet() {
        String executionMode = host.executionMode();
        boolean sshMode = ToolSettingsRepository.EXECUTION_SSH.equals(executionMode);
        boolean termuxSsh = sshMode && host.isTermuxSshHost();
        ArrayList<SheetOption> options = new ArrayList<>();
        ProjectRecord selected = projectStore.getSelectedProject(executionMode);
        String selectedId = selected == null ? "" : selected.getId();
        List<ProjectRecord> projects = projectStore.getProjects(executionMode);
        for (ProjectRecord project : projects) {
            options.add(new SheetOption(
                    "project:select:" + project.getId(),
                    project.getLabel(),
                    projectDisplayDescription(project),
                    project.getId().equals(selectedId),
                    projectDeleteActionId(project),
                    "删除"
            ));
        }
        if (!sshMode || termuxSsh) {
            options.add(new SheetOption(
                    "project:open_local_saf",
                    "打开本地项目",
                    termuxSsh ? "选择本机目录，并作为 SSH 工作区路径使用" : "通过系统 SAF 选择目录，并保存为本地项目",
                    false
            ));
        }
        options.add(new SheetOption(
                "project:create",
                "创建工作区",
                sshMode ? "在 SSH ~/.linecode/project 下创建项目" : "在 .linecode/project 下创建托管项目",
                false
        ));
        if (!sshMode || termuxSsh) {
            options.add(new SheetOption(
                    "storage:manage_all_files",
                    "管理所有文件权限",
                    host.hasExternalStorageAccess() ? "已授权，可访问文件存储" : host.storagePermissionMessage(),
                    host.hasExternalStorageAccess()
            ));
        }
        return new ProjectSheet(sshMode ? "工作区 SSH" : "工作区", options);
    }

    private String projectDeleteActionId(ProjectRecord project) {
        if (project == null
                || WorkspacePaths.DEFAULT_PROJECT_ID.equals(project.getId())
                || "ssh:default".equals(project.getId())) {
            return "";
        }
        return "project:delete:" + project.getId();
    }

    private String projectDisplayDescription(ProjectRecord project) {
        if (project == null) {
            return "";
        }
        String path = WorkspacePaths.displayPath(project.getPath());
        if (WorkspacePaths.SOURCE_SSH.equals(project.getSource()) && path.length() == 0) {
            return "SSH 登录目录";
        }
        if (path.length() > 0) {
            return path;
        }
        return project.getDescription();
    }
}
