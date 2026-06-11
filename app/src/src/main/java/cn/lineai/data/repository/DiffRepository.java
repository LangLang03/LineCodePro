package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class DiffRepository {
    private final LineCodeDatabase database;
    private final SecureRandom random = new SecureRandom();

    public DiffRepository(Context context) {
        database = LineCodeDatabase.getInstance(context);
    }

    public synchronized DiffRecord recordDiff(
            String filePath,
            String oldContent,
            String newContent,
            boolean oldExists
    ) {
        long now = System.currentTimeMillis();
        DiffRecord record = new DiffRecord(
                now + "_" + Long.toString(Math.abs(random.nextLong()), 36),
                filePath,
                oldContent,
                newContent,
                oldExists,
                now,
                false
        );
        ContentValues values = new ContentValues();
        values.put("id", record.getId());
        values.put("file_path", record.getFilePath());
        values.put("old_content", record.getOldContent());
        values.put("new_content", record.getNewContent());
        values.put("old_exists", record.isOldExists() ? 1 : 0);
        values.put("timestamp", record.getTimestamp());
        values.put("reverted", record.isReverted() ? 1 : 0);
        values.put("raw_json", "");
        database.getWritableDatabase().insertWithOnConflict("diff_records", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return record;
    }

    public synchronized DiffRecord getDiff(String diffId) {
        if (diffId == null || diffId.length() == 0) {
            return null;
        }
        Cursor cursor = database.getReadableDatabase().query(
                "diff_records",
                null,
                "id = ?",
                new String[] {diffId},
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst() ? readRecord(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized List<DiffRecord> getDiffChain(String filePath) {
        ArrayList<DiffRecord> records = new ArrayList<>();
        if (filePath == null || filePath.length() == 0) {
            return records;
        }
        Cursor cursor = database.getReadableDatabase().query(
                "diff_records",
                null,
                "file_path = ?",
                new String[] {filePath},
                null,
                null,
                "timestamp ASC"
        );
        try {
            while (cursor.moveToNext()) {
                records.add(readRecord(cursor));
            }
        } finally {
            cursor.close();
        }
        return records;
    }

    public synchronized RevertResult revertDiff(String diffId) {
        DiffRecord target = getDiff(diffId);
        if (target == null) {
            return RevertResult.error("未找到指定的修改记录");
        }
        if (target.isReverted()) {
            return RevertResult.ok("此修改已撤销");
        }

        List<DiffRecord> chain = getDiffChain(target.getFilePath());
        int targetIndex = -1;
        for (int i = 0; i < chain.size(); i++) {
            if (target.getId().equals(chain.get(i).getId())) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            return RevertResult.error("未找到指定的修改记录");
        }
        for (int i = targetIndex + 1; i < chain.size(); i++) {
            if (!chain.get(i).isReverted()) {
                return RevertResult.error("请先撤销此文件后续的修改");
            }
        }

        try {
            restoreOldContent(target);
            ContentValues values = new ContentValues();
            values.put("reverted", 1);
            database.getWritableDatabase().update("diff_records", values, "id = ?", new String[] {target.getId()});
            return RevertResult.ok("已撤销对 " + target.getFilePath() + " 的修改");
        } catch (Exception e) {
            return RevertResult.error("撤销失败: " + e.getMessage());
        }
    }

    private void restoreOldContent(DiffRecord record) throws Exception {
        File file = new File(record.getFilePath());
        if (!record.isOldExists()) {
            if (file.exists() && !file.delete()) {
                throw new java.io.IOException("无法删除文件: " + record.getFilePath());
            }
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("无法创建父目录: " + parent.getPath());
        }
        FileOutputStream output = new FileOutputStream(file, false);
        try {
            output.write(record.getOldContent().getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private DiffRecord readRecord(Cursor cursor) {
        return new DiffRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("file_path")),
                cursor.getString(cursor.getColumnIndexOrThrow("old_content")),
                cursor.getString(cursor.getColumnIndexOrThrow("new_content")),
                cursor.getInt(cursor.getColumnIndexOrThrow("old_exists")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                cursor.getInt(cursor.getColumnIndexOrThrow("reverted")) == 1
        );
    }

    public static final class RevertResult {
        private final boolean success;
        private final String message;

        private RevertResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        static RevertResult ok(String message) {
            return new RevertResult(true, message);
        }

        static RevertResult error(String message) {
            return new RevertResult(false, message);
        }
    }
}
