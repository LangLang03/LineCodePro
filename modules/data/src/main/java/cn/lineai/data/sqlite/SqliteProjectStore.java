package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.Project;
import cn.lineai.data.api.ProjectStore;

/** SQLite project list with exactly one selected project. */
public final class SqliteProjectStore implements ProjectStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    public SqliteProjectStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override public List<Project> list() {
        List<Project> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("projects", null, null, null,
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(read(cursor));
            }
        }
        return result;
    }

    @Override public Project selected() {
        try (Cursor cursor = database.open().query("projects", null, "selected=1",
                null, null, null, "updated_at DESC", "1")) {
            if (cursor.moveToNext()) return read(cursor);
        }
        return null;
    }

    @Override public Project ensureDefault(File directory) {
        Project current = selected();
        if (current != null) return current;
        List<Project> projects = list();
        if (!projects.isEmpty()) {
            select(projects.get(0).id);
            return projects.get(0).selected(true);
        }
        long now = clock.nowMs();
        Project project = new Project(Project.DEFAULT_ID, "LineCode", directory.getAbsolutePath(),
                true, now, now);
        write(project);
        return project;
    }

    @Override public Project create(String name, String path) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "project name is required");
        }
        long now = clock.nowMs();
        String id = Ids.prefixed("project");
        while (find(id) != null) id = Ids.prefixed("project");
        Project project = new Project(id, safeName, path, false, now, now);
        write(project);
        select(id);
        return project.selected(true);
    }

    @Override public void select(String id) {
        if (find(id) == null) return;
        database.open().beginTransaction();
        try {
            clearSelected();
            ContentValues values = new ContentValues();
            values.put("selected", 1);
            values.put("updated_at", clock.nowMs());
            database.open().update("projects", values, "id=?", new String[] {id});
            database.open().setTransactionSuccessful();
        } finally {
            database.open().endTransaction();
        }
    }

    @Override public void delete(String id) {
        if (Project.DEFAULT_ID.equals(id)) return;
        Project target = find(id);
        if (target == null) return;
        database.open().delete("projects", "id=?", new String[] {id});
        if (target.selected) {
            List<Project> remaining = list();
            if (!remaining.isEmpty()) select(remaining.get(0).id);
        }
    }

    private Project find(String id) {
        try (Cursor cursor = database.open().query("projects", null, "id=?",
                new String[] {id}, null, null, null, "1")) {
            return cursor.moveToNext() ? read(cursor) : null;
        }
    }

    private void clearSelected() {
        ContentValues values = new ContentValues();
        values.put("selected", 0);
        database.open().update("projects", values, null, null);
    }

    private void write(Project project) {
        ContentValues values = new ContentValues();
        values.put("id", project.id);
        values.put("name", project.name);
        values.put("path", project.path);
        values.put("selected", project.selected ? 1 : 0);
        values.put("created_at", project.createdAt);
        values.put("updated_at", project.updatedAt);
        database.open().insertWithOnConflict("projects", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static Project read(Cursor cursor) {
        return new Project(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("path")),
                cursor.getInt(cursor.getColumnIndexOrThrow("selected")) != 0,
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));
    }

}
