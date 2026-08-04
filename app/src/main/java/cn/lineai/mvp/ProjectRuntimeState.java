package cn.lineai.mvp;

import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.workspace.WorkspacePaths;

final class ProjectRuntimeState {
    private String label = "LineCode";
    private String path = "";
    private String source = WorkspacePaths.SOURCE_DEFAULT;

    String label() {
        return label;
    }

    String path() {
        return path;
    }

    String source() {
        return source;
    }

    void apply(ProjectRecord project, ProjectStore projectRepository) {
        if (project == null) {
            return;
        }
        label = project.getLabel().length() == 0 ? "LineCode" : project.getLabel();
        source = project.getSource();
        path = WorkspacePaths.displayPath(project.getPath());
        if (!WorkspacePaths.SOURCE_SSH.equals(source) && path.length() == 0) {
            path = projectRepository.getDefaultHomePath();
        }
    }

    void applyTerminalProviderPath(String newPath, String newLabel) {
        path = newPath == null ? "" : newPath;
        source = WorkspacePaths.SOURCE_EXTERNAL;
        label = newLabel == null || newLabel.length() == 0 ? "LineCode" : newLabel;
    }

    void clearTerminalProviderPath() {
        path = "";
        label = "LineCode";
        source = WorkspacePaths.SOURCE_DEFAULT;
    }

    void applyPathFromRemoteRoot(String newPath) {
        path = newPath == null ? "" : newPath;
    }

    boolean isSshExecutionMode(ToolSettingsStore toolSettingsRepository) {
        return ToolSettingsStore.EXECUTION_SSH.equals(toolSettingsRepository.getExecutionMode());
    }

    boolean isTerminalProviderExecutionMode(ToolSettingsStore toolSettingsRepository) {
        return ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(toolSettingsRepository.getExecutionMode());
    }

    String basename(String value) {
        return WorkspacePaths.basename(value == null ? "" : value);
    }

    String parentPath(String value) {
        String normalized = value == null ? "" : value.trim();
        int index = normalized.lastIndexOf('/');
        if (index <= 0) {
            return path;
        }
        return normalized.substring(0, index);
    }
}
