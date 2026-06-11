package cn.lineai.data.importer;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.db.LineCodeSchema;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LineCodeDatabaseArchive {
    private static final String[] TABLES = new String[] {
            LineCodeSchema.TABLE_SETTINGS,
            LineCodeSchema.TABLE_PROJECTS,
            LineCodeSchema.TABLE_MODELS,
            LineCodeSchema.TABLE_CONVERSATIONS,
            LineCodeSchema.TABLE_MESSAGES,
            LineCodeSchema.TABLE_MESSAGE_BLOCKS,
            LineCodeSchema.TABLE_TOOL_CALLS,
            LineCodeSchema.TABLE_TOOL_RESULTS,
            LineCodeSchema.TABLE_ATTACHMENTS,
            LineCodeSchema.TABLE_DIFFS,
            LineCodeSchema.TABLE_MEMORIES,
            LineCodeSchema.TABLE_WORKING_MEMORY,
            LineCodeSchema.TABLE_CONVERSATION_INDEX,
            LineCodeSchema.TABLE_SKILLS,
            LineCodeSchema.TABLE_SKILL_USAGE,
            LineCodeSchema.TABLE_EXTENSION_AGENTS,
            LineCodeSchema.TABLE_EXTENSION_MCPS
    };

    private final LineCodeArchiveCodec codec = new LineCodeArchiveCodec();

    public JSONObject exportSnapshot(LineCodeDatabase database) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "linecode-database");
        root.put("schemaVersion", LineCodeSchema.VERSION);
        JSONObject tables = new JSONObject();
        SQLiteDatabase db = database.getReadableDatabase();
        for (String table : TABLES) {
            tables.put(table, exportTable(db, table));
        }
        root.put("tables", tables);
        return root;
    }

    public boolean hasSnapshot(File payloadDir) {
        return new File(payloadDir, LineCodeArchiveCodec.ENTRY_DATABASE).isFile();
    }

    public JSONObject readSnapshot(File payloadDir) throws Exception {
        return new JSONObject(codec.readUtf8(new File(payloadDir, LineCodeArchiveCodec.ENTRY_DATABASE)));
    }

    public void importSnapshot(LineCodeDatabase database, JSONObject snapshot) throws Exception {
        if (snapshot == null || !"linecode-database".equals(snapshot.optString("format"))) {
            throw new IllegalArgumentException(".linecode 数据库快照无效。");
        }
        JSONObject tables = snapshot.optJSONObject("tables");
        if (tables == null) {
            throw new IllegalArgumentException(".linecode 数据库快照缺少 tables。");
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = TABLES.length - 1; i >= 0; i--) {
                db.delete(TABLES[i], null, null);
            }
            for (String table : TABLES) {
                importTable(db, table, tables.optJSONObject(table));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private JSONObject exportTable(SQLiteDatabase db, String table) throws Exception {
        JSONObject object = new JSONObject();
        JSONArray columns = new JSONArray();
        JSONArray rows = new JSONArray();
        Cursor cursor = db.query(table, null, null, null, null, null, null);
        try {
            String[] names = cursor.getColumnNames();
            for (String name : names) {
                columns.put(name);
            }
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < names.length; i++) {
                    row.put(names[i], encodeValue(cursor, i));
                }
                rows.put(row);
            }
        } finally {
            cursor.close();
        }
        object.put("columns", columns);
        object.put("rows", rows);
        return object;
    }

    private JSONObject encodeValue(Cursor cursor, int index) throws Exception {
        JSONObject object = new JSONObject();
        int type = cursor.getType(index);
        if (type == Cursor.FIELD_TYPE_NULL) {
            object.put("type", "null");
        } else if (type == Cursor.FIELD_TYPE_INTEGER) {
            object.put("type", "integer");
            object.put("value", cursor.getLong(index));
        } else if (type == Cursor.FIELD_TYPE_FLOAT) {
            object.put("type", "float");
            object.put("value", cursor.getDouble(index));
        } else if (type == Cursor.FIELD_TYPE_BLOB) {
            object.put("type", "blob");
            object.put("value", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP));
        } else {
            object.put("type", "string");
            object.put("value", cursor.getString(index));
        }
        return object;
    }

    private void importTable(SQLiteDatabase db, String table, JSONObject tableJson) throws Exception {
        if (tableJson == null) {
            return;
        }
        JSONArray rows = tableJson.optJSONArray("rows");
        if (rows == null) {
            return;
        }
        HashSet<String> columns = tableColumns(db, table);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            ContentValues values = new ContentValues();
            Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String column = keys.next();
                if (!columns.contains(column)) {
                    continue;
                }
                putValue(values, column, row.optJSONObject(column));
            }
            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void putValue(ContentValues values, String column, JSONObject cell) {
        if (cell == null || "null".equals(cell.optString("type"))) {
            values.putNull(column);
            return;
        }
        String type = cell.optString("type");
        if ("integer".equals(type)) {
            values.put(column, cell.optLong("value"));
        } else if ("float".equals(type)) {
            values.put(column, cell.optDouble("value"));
        } else if ("blob".equals(type)) {
            values.put(column, Base64.decode(cell.optString("value"), Base64.NO_WRAP));
        } else {
            values.put(column, cell.optString("value"));
        }
    }

    private HashSet<String> tableColumns(SQLiteDatabase db, String table) {
        HashSet<String> columns = new HashSet<>();
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        } finally {
            cursor.close();
        }
        return columns;
    }
}
