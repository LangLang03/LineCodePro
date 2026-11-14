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

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param clock clock used to timestamp created, selected and updated projects
     */
    public SqliteProjectStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Reads every project, newest update first.
     *
     * @return all stored projects
     */
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

    /**
     * Reads the single selected project.
     *
     * @return the selected project or null when none is selected
     */
    @Override public Project selected() {
        try (Cursor cursor = database.open().query("projects", null, "selected=1",
                null, null, null, "updated_at DESC", "1")) {
            if (cursor.moveToNext()) return read(cursor);
        }
        return null;
    }

    /**
     * Guarantees a usable selection: keeps the current selection, otherwise selects the most
     * recently updated existing project, otherwise creates the built-in default project bound to
     * the given directory.
     *
     * @param directory workspace directory used when the default project must be created
     * @return the project that is selected after the call
     */
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

    /**
     * Creates a project with a unique identifier and selects it.
     *
     * @param name display name; blank names are rejected
     * @param path workspace directory path
     * @return the created project, marked selected
     * @throws DomainException with {@code ErrorCode.INVALID_INPUT} when the name is blank
     */
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

    /**
     * Clears every selected flag and marks one project selected inside a transaction, so the
     * exactly-one-selected invariant holds for every observer.
     *
     * @param id project identifier; unknown identifiers are ignored
     */
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

    /**
     * Deletes a project and, when it was selected, moves the selection to the most recently
     * updated remaining project. The built-in default project cannot be deleted.
     *
     * @param id project identifier; unknown identifiers are ignored
     */
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

    /** Loads one project or null when the identifier is unknown. */
    private Project find(String id) {
        try (Cursor cursor = database.open().query("projects", null, "id=?",
                new String[] {id}, null, null, null, "1")) {
            return cursor.moveToNext() ? read(cursor) : null;
        }
    }

    /** Clears the selected flag on every row; called within the select transaction. */
    private void clearSelected() {
        ContentValues values = new ContentValues();
        values.put("selected", 0);
        database.open().update("projects", values, null, null);
    }

    /** Inserts or replaces one project row. */
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

    /** Maps the current row onto a project. */
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
