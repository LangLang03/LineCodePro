package cn.lineai.data.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.db.LineCodeSchema;
import cn.lineai.resource.SystemConfigProvider;
import java.io.File;

public final class StorageStatsRepository {
    private final SystemConfigProvider systemConfigProvider;
    private final LineCodeDatabase database;
    private final MessageTextChunkStore textChunks;

    public StorageStatsRepository(SystemConfigProvider systemConfigProvider, LineCodeDatabase database) {
        this.systemConfigProvider = systemConfigProvider;
        this.database = database;
        this.textChunks = new MessageTextChunkStore(database);
    }

    public StorageStats getStats() {
        StorageStats stats = new StorageStats();
        SQLiteDatabase db = database.getReadableDatabase();

        stats.diffCacheSize = calculateDiffCache(db);
        stats.diffCacheCount = countRecords(db, "diff_records");

        stats.chatSize = calculateChatSize(db);
        stats.chatCount = countRecords(db, "conversations");

        stats.configSize = calculateConfigSize();
        stats.configCount = countConfigFiles();

        stats.homeSize = calculateHomeSize();
        stats.homeCount = countHomeFiles();

        stats.totalSize = stats.diffCacheSize + stats.chatSize + stats.configSize + stats.homeSize;
        stats.totalCount = stats.diffCacheCount + stats.chatCount + stats.configCount + stats.homeCount;

        return stats;
    }

    private long calculateDiffCache(SQLiteDatabase db) {
        return textChunks.totalLength(db, "diff_records", "old_content")
                + textChunks.totalLength(db, "diff_records", "new_content");
    }

    private long calculateChatSize(SQLiteDatabase db) {
        long size = textChunks.totalLength(db, "messages", "content")
                + textChunks.totalLength(db, "messages", "reasoning_content")
                + textChunks.totalLength(db, "message_text_chunks", "content")
                + textChunks.totalLength(db, "tool_calls", "arguments")
                + textChunks.totalLength(db, "tool_results", "content");
        size += getDatabaseFileSize();
        return size;
    }

    private long getDatabaseFileSize() {
        String dbPath = systemConfigProvider.getDatabasePath("linecode.db");
        if (dbPath != null) {
            File dbFile = new File(dbPath);
            if (dbFile.exists()) {
                return dbFile.length();
            }
        }
        return 0;
    }

    private long calculateConfigSize() {
        String filesDirPath = systemConfigProvider.getFilesDirPath();
        File filesDir = new File(filesDirPath);
        File prefsDir = new File(filesDir.getParentFile(), "shared_prefs");
        long size = 0;
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            size += calculateDirectorySize(prefsDir);
        }
        if (filesDir.exists() && filesDir.isDirectory()) {
            size += calculateDirectorySize(filesDir);
        }
        return size;
    }

    private int countConfigFiles() {
        int count = 0;
        String filesDirPath = systemConfigProvider.getFilesDirPath();
        File filesDir = new File(filesDirPath);
        File prefsDir = new File(filesDir.getParentFile(), "shared_prefs");
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            count += countFilesInDirectory(prefsDir);
        }
        if (filesDir.exists() && filesDir.isDirectory()) {
            count += countFilesInDirectory(filesDir);
        }
        return count;
    }

    private long calculateHomeSize() {
        File homeDir = getHomeDirectory();
        if (homeDir != null && homeDir.exists()) {
            return calculateDirectorySize(homeDir);
        }
        return 0;
    }

    private int countHomeFiles() {
        File homeDir = getHomeDirectory();
        if (homeDir != null && homeDir.exists()) {
            return countFilesInDirectory(homeDir);
        }
        return 0;
    }

    private File getHomeDirectory() {
        String homePath = System.getenv("HOME");
        if (homePath != null && homePath.length() > 0) {
            return new File(homePath);
        }
        String externalPath = systemConfigProvider.getExternalFilesDirPath();
        if (externalPath != null) {
            return new File(externalPath);
        }
        return new File(systemConfigProvider.getFilesDirPath());
    }

    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                size += calculateDirectorySize(file);
            } else {
                size += file.length();
            }
        }
        return size;
    }

    private int countFilesInDirectory(File directory) {
        int count = 0;
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                count += countFilesInDirectory(file);
            } else {
                count++;
            }
        }
        return count;
    }

    private int countRecords(SQLiteDatabase db, String table) {
        if (!LineCodeSchema.isValidTable(table)) {
            throw new IllegalArgumentException("非法表名: " + table);
        }
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public void clearDiffCache() {
        database.getWritableDatabase().delete("diff_records", null, null);
    }

    public void clearChatHistory() {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("message_text_chunks", null, null);
            db.delete("messages", null, null);
            db.delete("conversations", null, null);
            db.delete("conversation_index", null, null);
            try {
                db.delete("conversation_index_fts", null, null);
            } catch (RuntimeException ignored) {
            }
            db.delete("tool_calls", null, null);
            db.delete("tool_results", null, null);
            db.delete("message_blocks", null, null);
            db.delete("attachments", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static final class StorageStats {
        public long totalSize = 0;
        public int totalCount = 0;
        public long diffCacheSize = 0;
        public int diffCacheCount = 0;
        public long chatSize = 0;
        public int chatCount = 0;
        public long configSize = 0;
        public int configCount = 0;
        public long homeSize = 0;
        public int homeCount = 0;

        public String formatSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return (bytes / 1024) + " KB";
            }
            if (bytes < 1024 * 1024 * 1024) {
                long mb = bytes / (1024 * 1024);
                return mb + " MB";
            }
            long gb = bytes / (1024 * 1024 * 1024);
            return gb + " GB";
        }

        public String formatTotalSize() {
            return formatSize(totalSize);
        }

        public String formatDiffCacheSize() {
            return formatSize(diffCacheSize);
        }

        public String formatChatSize() {
            return formatSize(chatSize);
        }

        public String formatConfigSize() {
            return formatSize(configSize);
        }

        public String formatHomeSize() {
            return formatSize(homeSize);
        }
    }
}
