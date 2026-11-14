package cn.lineai.data.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

import cn.lineai.data.api.StorageStats;
import cn.lineai.data.api.StorageStatsStore;

/** Sizes derived from the database rows plus the files under the app home directory. */
public final class SqliteStorageStatsStore implements StorageStatsStore {

    private final LineCodeDatabase database;
    private final File home;

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param home app home directory walked recursively for the home usage category
     */
    public SqliteStorageStatsStore(LineCodeDatabase database, File home) {
        this.database = database;
        this.home = home;
    }

    /**
     * Measures database text columns per category and walks the home directory recursively.
     *
     * @return usage snapshot covering diffs, chat, configuration and home files
     */
    @Override
    public StorageStats load() {
        SQLiteDatabase db = database.open();
        long chatBytes = length(db, "message_blocks", "text") + length(db, "messages", "error_code");
        int chatCount = count(db, "messages");
        long configBytes = length(db, "settings", "value") + length(db, "models", "api_key");
        int configCount = count(db, "settings") + count(db, "models");
        long[] homeUsage = homeUsage(home);
        long diffBytes = length(db, "file_diffs", "before_text")
                + length(db, "file_diffs", "after_text");
        int diffCount = count(db, "file_diffs");
        return new StorageStats(diffBytes, diffCount, chatBytes, chatCount,
                configBytes, configCount, homeUsage[0], (int) homeUsage[1]);
    }

    /** Deletes all blocks, messages and conversations in one transaction. */
    @Override
    public void clearChatHistory() {
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            db.delete("message_blocks", null, null);
            db.delete("messages", null, null);
            db.delete("conversations", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Deletes every stored diff snapshot without touching workspace files. */
    @Override
    public void clearDiffCache() {
        database.open().delete("file_diffs", null, null);
    }

    /** Sums the stored lengths of one text column; table and column come from internal callers. */
    private static long length(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(LENGTH(" + column + ")), 0) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    /** Counts the rows of one table. */
    private static int count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Recursively totals file bytes and file count; returns {@code {bytes, files}}. */
    private static long[] homeUsage(File directory) {
        if (directory == null || !directory.exists()) return new long[] {0L, 0L};
        long bytes = 0L;
        long files = 0L;
        File[] children = directory.listFiles();
        if (children == null) return new long[] {0L, 0L};
        for (File child : children) {
            if (child.isDirectory()) {
                long[] nested = homeUsage(child);
                bytes += nested[0];
                files += nested[1];
            } else {
                bytes += child.length();
                files++;
            }
        }
        return new long[] {bytes, files};
    }
}
