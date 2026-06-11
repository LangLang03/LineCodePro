package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ProjectRepository {
    private static final String SSH_DEFAULT_PROJECT_ID = "ssh:default";
    private static final String KEY_SELECTED_LOCAL_PROJECT = "@linecode_selected_project_local";
    private static final String KEY_SELECTED_SSH_PROJECT = "@linecode_selected_project_ssh";

    private final Context context;
    private final LineCodeDatabase database;
    private final SettingsRepository settingsRepository;
    private final WorkspacePaths workspacePaths;

    public ProjectRepository(Context context) {
        this.context = context.getApplicationContext();
        database = LineCodeDatabase.getInstance(context);
        settingsRepository = new SettingsRepository(this.context);
        workspacePaths = new WorkspacePaths(this.context);
        workspacePaths.ensurePrivateRoots();
        ensureDefaultProject();
    }

    public synchronized List<ProjectRecord> getProjects() {
        return getProjects(ToolSettingsRepository.EXECUTION_LOCAL);
    }

    public synchronized List<ProjectRecord> getProjects(String executionMode) {
        if (ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))) {
            ensureDefaultSshProject();
            return queryProjects("source = ?", new String[] {WorkspacePaths.SOURCE_SSH});
        }
        ensureDefaultProject();
        return queryProjects("source != ?", new String[] {WorkspacePaths.SOURCE_SSH});
    }

    private List<ProjectRecord> queryProjects(String selection, String[] args) {
        ArrayList<ProjectRecord> projects = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                selection,
                args,
                null,
                null,
                "selected DESC, updated_at DESC"
        );
        try {
            while (cursor.moveToNext()) {
                projects.add(readProject(cursor));
            }
        } finally {
            cursor.close();
        }
        if (projects.isEmpty()) {
            return projects;
        }
        return projects;
    }

    public synchronized ProjectRecord getSelectedProject() {
        return getSelectedProject(ToolSettingsRepository.EXECUTION_LOCAL);
    }

    public synchronized ProjectRecord getSelectedProject(String executionMode) {
        String mode = ToolSettingsRepository.normalizeExecutionMode(executionMode);
        if (ToolSettingsRepository.EXECUTION_SSH.equals(mode)) {
            ensureDefaultSshProject();
        } else {
            ensureDefaultProject();
        }
        String selectedId = settingsRepository.getString(selectedKey(mode), "");
        ProjectRecord storedSelection = selectedId.length() == 0 ? null : findProject(selectedId);
        if (isProjectInMode(storedSelection, mode)) {
            return storedSelection;
        }
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                modeSelection(mode) + " AND selected = 1",
                modeArgs(mode),
                null,
                null,
                "updated_at DESC",
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                ProjectRecord project = readProject(cursor);
                settingsRepository.setString(selectedKey(mode), project.getId());
                return project;
            }
        } finally {
            cursor.close();
        }
        List<ProjectRecord> projects = getProjects(mode);
        return projects.isEmpty() ? defaultProjectForMode(mode) : projects.get(0);
    }

    public synchronized ProjectRecord getSelectedProjectLegacy() {
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                "selected = 1",
                null,
                null,
                null,
                "updated_at DESC",
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                return readProject(cursor);
            }
        } finally {
            cursor.close();
        }
        List<ProjectRecord> projects = getProjects();
        return projects.isEmpty() ? defaultProject() : projects.get(0);
    }

    public synchronized void save(ProjectRecord project) {
        ensureProjectPath(project);
        ContentValues values = valuesFor(project);
        SQLiteDatabase db = database.getWritableDatabase();
        db.insertWithOnConflict("projects", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (project.isSelected()) {
            setSelected(project.getId(), executionModeFor(project));
        }
    }

    public synchronized void setSelected(String id) {
        setSelected(id, ToolSettingsRepository.EXECUTION_LOCAL);
    }

    public synchronized void setSelected(String id, String executionMode) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            selectProjectInTransaction(db, id);
            settingsRepository.setString(selectedKey(executionMode), id == null ? "" : id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean deleteProject(String id) {
        return deleteProject(id, ToolSettingsRepository.EXECUTION_LOCAL);
    }

    public synchronized boolean deleteProject(String id, String executionMode) {
        String safeId = id == null ? "" : id.trim();
        if (safeId.length() == 0 || WorkspacePaths.DEFAULT_PROJECT_ID.equals(safeId) || SSH_DEFAULT_PROJECT_ID.equals(safeId)) {
            return false;
        }
        String mode = ToolSettingsRepository.normalizeExecutionMode(executionMode);
        if (ToolSettingsRepository.EXECUTION_SSH.equals(mode)) {
            ensureDefaultSshProject();
        } else {
            ensureDefaultProject();
        }
        ProjectRecord project = findProject(safeId);
        if (!isProjectInMode(project, mode)) {
            return false;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            int deleted = db.delete("projects", "id = ?", new String[] {safeId});
            if (deleted > 0 && project.isSelected()) {
                selectProjectInTransaction(db, defaultProjectForMode(mode).getId());
            }
            if (safeId.equals(settingsRepository.getString(selectedKey(mode), ""))) {
                settingsRepository.setString(selectedKey(mode), defaultProjectForMode(mode).getId());
            }
            db.setTransactionSuccessful();
            return deleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized ProjectRecord selectDefaultProject(String executionMode) {
        String mode = ToolSettingsRepository.normalizeExecutionMode(executionMode);
        String defaultId;
        if (ToolSettingsRepository.EXECUTION_SSH.equals(mode)) {
            ensureDefaultSshProject();
            defaultId = SSH_DEFAULT_PROJECT_ID;
        } else {
            ensureDefaultProject();
            defaultId = WorkspacePaths.DEFAULT_PROJECT_ID;
        }
        setSelected(defaultId, mode);
        return getSelectedProject(mode);
    }

    public synchronized ProjectRecord createManagedProject(String name) {
        String cleanName = sanitizeProjectName(name);
        if (cleanName.length() == 0) {
            cleanName = "Project-" + System.currentTimeMillis();
        }
        File path = new File(workspacePaths.getProjectRoot(), cleanName);
        path.mkdirs();
        long now = System.currentTimeMillis();
        ProjectRecord project = new ProjectRecord(
                "managed:" + cleanName.toLowerCase(java.util.Locale.ROOT),
                cleanName,
                path.getAbsolutePath(),
                WorkspacePaths.SOURCE_MANAGED,
                ".linecode/project",
                true,
                now,
                now
        );
        save(project);
        return getSelectedProject(ToolSettingsRepository.EXECUTION_LOCAL);
    }

    public synchronized ProjectRecord saveExternalProject(String path, String label) {
        String resolved = path == null ? "" : path.trim();
        if (resolved.length() == 0) {
            return getSelectedProject();
        }
        String name = label == null || label.trim().length() == 0
                ? WorkspacePaths.basename(resolved)
                : label.trim();
        if (name.length() == 0) {
            name = "外部工作区";
        }
        long now = System.currentTimeMillis();
        ProjectRecord project = new ProjectRecord(
                "external:" + resolved,
                name,
                resolved,
                WorkspacePaths.SOURCE_EXTERNAL,
                resolved,
                true,
                now,
                now
        );
        save(project);
        return getSelectedProject(ToolSettingsRepository.EXECUTION_LOCAL);
    }

    public synchronized ProjectRecord saveSshProject(String path, String label) {
        String resolved = path == null ? "" : path.trim();
        String name = label == null || label.trim().length() == 0
                ? WorkspacePaths.basename(resolved)
                : label.trim();
        if (name.length() == 0) {
            name = "SSH 工作区";
        }
        long now = System.currentTimeMillis();
        ProjectRecord project = new ProjectRecord(
                "ssh:" + resolved,
                name,
                resolved,
                WorkspacePaths.SOURCE_SSH,
                resolved.length() == 0 ? "SSH 登录目录" : resolved,
                true,
                now,
                now
        );
        save(project);
        return getSelectedProject(ToolSettingsRepository.EXECUTION_SSH);
    }

    public synchronized ProjectRecord ensureSelectedProjectPath() {
        ProjectRecord selected = getSelectedProject();
        ensureProjectPath(selected);
        return selected;
    }

    public synchronized ProjectRecord ensureSelectedProjectPath(String executionMode) {
        ProjectRecord selected = getSelectedProject(executionMode);
        ensureProjectPath(selected);
        return selected;
    }

    public String getLinecodeRootPath() {
        return workspacePaths.getLinecodeRoot().getAbsolutePath();
    }

    public String getDefaultHomePath() {
        return workspacePaths.getHomeRoot().getAbsolutePath();
    }

    private ProjectRecord findProject(String id) {
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                "id = ?",
                new String[] {id},
                null,
                null,
                null,
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                return readProject(cursor);
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    private void ensureDefaultProject() {
        SQLiteDatabase db = database.getWritableDatabase();
        Cursor cursor = db.query("projects", null, "id = ?", new String[] {WorkspacePaths.DEFAULT_PROJECT_ID}, null, null, null, "1");
        try {
            if (cursor.moveToFirst()) {
                ProjectRecord existing = readProject(cursor);
                ProjectRecord defaultProject = defaultProject();
                if (!existing.getPath().equals(defaultProject.getPath())
                        || !WorkspacePaths.SOURCE_DEFAULT.equals(existing.getSource())) {
                    save(new ProjectRecord(
                            WorkspacePaths.DEFAULT_PROJECT_ID,
                            defaultProject.getLabel(),
                            defaultProject.getPath(),
                            WorkspacePaths.SOURCE_DEFAULT,
                            defaultProject.getDescription(),
                            existing.isSelected(),
                            existing.getCreatedAt(),
                            System.currentTimeMillis()
                    ));
                } else {
                    ensureProjectPath(existing);
                }
                return;
            }
        } finally {
            cursor.close();
        }
        save(defaultProject());
    }

    private void ensureDefaultSshProject() {
        SQLiteDatabase db = database.getWritableDatabase();
        Cursor cursor = db.query("projects", null, "id = ?", new String[] {SSH_DEFAULT_PROJECT_ID}, null, null, null, "1");
        try {
            if (cursor.moveToFirst()) {
                return;
            }
        } finally {
            cursor.close();
        }
        save(defaultSshProject(false));
        if (settingsRepository.getString(KEY_SELECTED_SSH_PROJECT, "").length() == 0) {
            settingsRepository.setString(KEY_SELECTED_SSH_PROJECT, SSH_DEFAULT_PROJECT_ID);
        }
    }

    private ProjectRecord defaultProject() {
        long now = System.currentTimeMillis();
        File home = workspacePaths.getHomeRoot();
        return new ProjectRecord(
                WorkspacePaths.DEFAULT_PROJECT_ID,
                "LineCode",
                home.getAbsolutePath(),
                WorkspacePaths.SOURCE_DEFAULT,
                "默认 home 工作区",
                true,
                now,
                now
        );
    }

    private ProjectRecord defaultSshProject(boolean selected) {
        long now = System.currentTimeMillis();
        return new ProjectRecord(
                SSH_DEFAULT_PROJECT_ID,
                "SSH",
                "",
                WorkspacePaths.SOURCE_SSH,
                "SSH 登录目录",
                selected,
                now,
                now
        );
    }

    private ProjectRecord defaultProjectForMode(String executionMode) {
        return ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))
                ? defaultSshProject(false)
                : defaultProject();
    }

    private void ensureProjectPath(ProjectRecord project) {
        if (project == null) {
            return;
        }
        if (WorkspacePaths.SOURCE_SSH.equals(project.getSource())) {
            return;
        }
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(project.getSource())) {
            File root = new File(project.getPath());
            if (root.isDirectory()) {
                new File(root, ".linecode/skills").mkdirs();
            }
            return;
        }
        File path = workspacePaths.resolvePrivatePath(project.getPath());
        if (!path.exists()) {
            path.mkdirs();
        }
        new File(path, ".linecode/skills").mkdirs();
    }

    private String sanitizeProjectName(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", "-");
        if (value.length() > 60) {
            value = value.substring(0, 60);
        }
        return value;
    }

    private void selectProjectInTransaction(SQLiteDatabase db, String id) {
        ContentValues clear = new ContentValues();
        clear.put("selected", 0);
        db.update("projects", clear, null, null);

        ContentValues select = new ContentValues();
        select.put("selected", 1);
        select.put("updated_at", System.currentTimeMillis());
        int updated = db.update("projects", select, "id = ?", new String[] {id});
        if (updated == 0) {
            db.update("projects", select, "id = ?", new String[] {WorkspacePaths.DEFAULT_PROJECT_ID});
        }
    }

    private String selectedKey(String executionMode) {
        return ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))
                ? KEY_SELECTED_SSH_PROJECT
                : KEY_SELECTED_LOCAL_PROJECT;
    }

    private String modeSelection(String executionMode) {
        return ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))
                ? "source = ?"
                : "source != ?";
    }

    private String[] modeArgs(String executionMode) {
        return new String[] {WorkspacePaths.SOURCE_SSH};
    }

    private String executionModeFor(ProjectRecord project) {
        return project != null && WorkspacePaths.SOURCE_SSH.equals(project.getSource())
                ? ToolSettingsRepository.EXECUTION_SSH
                : ToolSettingsRepository.EXECUTION_LOCAL;
    }

    private boolean isProjectInMode(ProjectRecord project, String executionMode) {
        if (project == null) {
            return false;
        }
        boolean sshProject = WorkspacePaths.SOURCE_SSH.equals(project.getSource());
        return ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))
                ? sshProject
                : !sshProject;
    }

    private ContentValues valuesFor(ProjectRecord project) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", project.getId());
        values.put("label", project.getLabel());
        values.put("path", project.getPath());
        values.put("source", project.getSource());
        values.put("description", project.getDescription());
        values.put("selected", project.isSelected() ? 1 : 0);
        values.put("created_at", project.getCreatedAt() > 0 ? project.getCreatedAt() : now);
        values.put("updated_at", project.getUpdatedAt() > 0 ? project.getUpdatedAt() : now);
        return values;
    }

    private ProjectRecord readProject(Cursor cursor) {
        return new ProjectRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("label")),
                cursor.getString(cursor.getColumnIndexOrThrow("path")),
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
                cursor.getString(cursor.getColumnIndexOrThrow("description")),
                cursor.getInt(cursor.getColumnIndexOrThrow("selected")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        );
    }
}
