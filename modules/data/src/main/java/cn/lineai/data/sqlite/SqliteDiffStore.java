package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.FileDiff;
import cn.lineai.data.api.DiffStore;

/** File snapshots plus rollback. Paths are relative to the recorded workspace root. */
public final class SqliteDiffStore implements DiffStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    public SqliteDiffStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override public void record(String rootPath, String path, String before, String after,
                                 boolean existedBefore) {
        ContentValues values = new ContentValues();
        values.put("id", Ids.prefixed("diff"));
        values.put("root_path", rootPath == null ? "" : rootPath);
        values.put("path", path == null ? "" : path);
        values.put("before_text", before == null ? "" : before);
        values.put("after_text", after == null ? "" : after);
        values.put("existed_before", existedBefore ? 1 : 0);
        values.put("reverted", 0);
        values.put("created_at", clock.nowMs());
        database.open().insertWithOnConflict("file_diffs", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override public List<FileDiff> list() {
        List<FileDiff> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("file_diffs", null, null, null,
                null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(new FileDiff(
                        text(cursor, "id"), text(cursor, "root_path"), text(cursor, "path"),
                        text(cursor, "before_text"), text(cursor, "after_text"),
                        cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("reverted")) != 0,
                        cursor.getInt(cursor.getColumnIndexOrThrow("existed_before")) != 0));
            }
        }
        return result;
    }

    @Override public boolean revert(String id) {
        FileDiff diff = find(id);
        if (diff == null || diff.reverted) return false;
        try {
            File root = new File(diff.rootPath).getCanonicalFile();
            File target = new File(root, diff.path).getCanonicalFile();
            if (!target.getPath().startsWith(root.getPath() + File.separator)) return false;
            if (!diff.existedBefore) {
                target.delete();
            } else {
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                Files.write(target.toPath(), diff.before.getBytes(StandardCharsets.UTF_8));
            }
            ContentValues values = new ContentValues();
            values.put("reverted", 1);
            database.open().update("file_diffs", values, "id=?", new String[] {id});
            return true;
        } catch (IOException error) {
            throw new DomainException(ErrorCode.STORAGE, "revert diff", error);
        }
    }

    @Override public void clear() {
        database.open().delete("file_diffs", null, null);
    }

    private FileDiff find(String id) {
        try (Cursor cursor = database.open().query("file_diffs", null, "id=?",
                new String[] {id}, null, null, null, "1")) {
            if (!cursor.moveToNext()) return null;
            return new FileDiff(cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    text(cursor, "root_path"), text(cursor, "path"),
                    text(cursor, "before_text"), text(cursor, "after_text"),
                    cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("reverted")) != 0,
                    cursor.getInt(cursor.getColumnIndexOrThrow("existed_before")) != 0);
        }
    }

    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }
}
