package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.data.api.ArchiveService;

/** JSON-in-ZIP archive of every table owned by the rewritten shell. */
public final class SqliteArchiveService implements ArchiveService {

    private static final String FORMAT = "linecode-archive";
    private static final int VERSION = 1;
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final String[] TABLES = {
            "conversations", "messages", "message_blocks", "settings", "models", "error_logs"
    };

    private final LineCodeDatabase database;
    private final Clock clock;
    private final File filesDir;

    public SqliteArchiveService(LineCodeDatabase database, Clock clock) {
        this(database, clock, null);
    }

    public SqliteArchiveService(LineCodeDatabase database, Clock clock, File filesDir) {
        this.database = database;
        this.clock = clock;
        this.filesDir = filesDir;
    }

    @Override
    public Summary export(OutputStream output) {
        try {
            SQLiteDatabase db = database.open();
            ZipOutputStream zip = new ZipOutputStream(output);
            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT);
            manifest.put("version", VERSION);
            manifest.put("createdAt", clock.nowMs());
            write(zip, "manifest.json", manifest.toString());
            int conversations = 0;
            int models = 0;
            int settings = 0;
            if (filesDir != null) writeFiles(zip);
            for (String table : TABLES) {
                JSONArray rows = readTable(db, table);
                write(zip, table + ".json", rows.toString());
                if ("conversations".equals(table)) conversations = rows.length();
                if ("models".equals(table)) models = rows.length();
                if ("settings".equals(table)) settings = rows.length();
            }
            zip.finish();
            return new Summary(conversations, models, settings);
        } catch (IOException | JSONException error) {
            throw new DomainException(ErrorCode.STORAGE, "export archive", error);
        }
    }

    @Override
    public Summary importFrom(InputStream input) {
        Map<String, byte[]> entries = readZip(input);
        requireEntries(entries);
        try {
            JSONObject manifest = new JSONObject(text(entries, "manifest.json"));
            if (!FORMAT.equals(manifest.optString("format")) || manifest.optInt("version") != VERSION) {
                throw new DomainException(ErrorCode.INVALID_INPUT, "unsupported archive");
            }
            SQLiteDatabase db = database.open();
            db.beginTransaction();
            try {
                for (String table : TABLES) db.delete(table, null, null);
                for (String table : TABLES) {
                    JSONArray rows = new JSONArray(text(entries, table + ".json"));
                    for (int i = 0; i < rows.length(); i++) {
                        db.insertOrThrow(table, null, values(rows.getJSONObject(i)));
                    }
                }
                db.setTransactionSuccessful();
                if (filesDir != null) restoreFiles(entries);
                return new Summary(
                        new JSONArray(text(entries, "conversations.json")).length(),
                        new JSONArray(text(entries, "models.json")).length(),
                        new JSONArray(text(entries, "settings.json")).length());
            } finally {
                db.endTransaction();
            }
        } catch (JSONException | IOException error) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "invalid archive", error);
        }
    }

    private static void requireEntries(Map<String, byte[]> entries) {
        if (!entries.containsKey("manifest.json")) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "missing manifest");
        }
        for (String table : TABLES) {
            if (!entries.containsKey(table + ".json")) {
                throw new DomainException(ErrorCode.INVALID_INPUT, "missing " + table);
            }
        }
    }

    private static JSONArray readTable(SQLiteDatabase db, String table) throws JSONException {
        JSONArray rows = new JSONArray();
        try (Cursor cursor = db.query(table, null, null, null, null, null, null)) {
            String[] columns = cursor.getColumnNames();
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < columns.length; i++) row.put(columns[i], value(cursor, i));
                rows.put(row);
            }
        }
        return rows;
    }

    private static Object value(Cursor cursor, int index) {
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_INTEGER: return cursor.getLong(index);
            case Cursor.FIELD_TYPE_FLOAT: return cursor.getDouble(index);
            case Cursor.FIELD_TYPE_NULL: return JSONObject.NULL;
            case Cursor.FIELD_TYPE_STRING:
            default: return cursor.getString(index);
        }
    }

    private static ContentValues values(JSONObject row) throws JSONException {
        ContentValues values = new ContentValues();
        java.util.Iterator<String> keys = row.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = row.get(key);
            if (value == JSONObject.NULL) values.putNull(key);
            else if (value instanceof Integer) values.put(key, (Integer) value);
            else if (value instanceof Long) values.put(key, (Long) value);
            else if (value instanceof Double) values.put(key, (Double) value);
            else if (value instanceof Boolean) values.put(key, (Boolean) value);
            else values.put(key, String.valueOf(value));
        }
        return values;
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String text(Map<String, byte[]> entries, String name) {
        byte[] bytes = entries.get(name);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeFiles(ZipOutputStream zip) throws IOException {
        for (String name : new String[] {"workspace", "skills", "attachments"}) {
            File root = new File(filesDir, name);
            if (!root.isDirectory()) continue;
            writeDirectory(zip, root, "files/" + name, 0);
        }
    }

    private static void writeDirectory(ZipOutputStream zip, File dir, String prefix,
                                       int depth) throws IOException {
        if (depth > 32) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = prefix + "/" + child.getName();
            if (child.isDirectory()) {
                writeDirectory(zip, child, name, depth + 1);
            } else {
                byte[] bytes = Files.readAllBytes(child.toPath());
                if (bytes.length > 8 * 1024 * 1024) continue;
                write(zip, name, bytes);
            }
        }
    }

    private void restoreFiles(Map<String, byte[]> entries) throws IOException {
        List<String> roots = new ArrayList<>();
        for (String name : new String[] {"workspace", "skills", "attachments"}) {
            for (String entry : entries.keySet()) {
                if (entry.startsWith("files/" + name + "/")) {
                    roots.add(name);
                    break;
                }
            }
        }
        for (String name : roots) deleteTree(new File(filesDir, name));
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("files/")) continue;
            String[] parts = key.split("/", 3);
            if (parts.length < 3) continue;
            if (!"workspace".equals(parts[1]) && !"skills".equals(parts[1])
                    && !"attachments".equals(parts[1])) {
                continue;
            }
            String relative = parts[2];
            if (relative.contains("..") || relative.startsWith("/")) {
                throw new IOException("unsafe archive path: " + key);
            }
            File target = new File(new File(filesDir, parts[1]), relative);
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(target.toPath(), entry.getValue());
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static Map<String, byte[]> readZip(InputStream input) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ENTRY_BYTES) {
                        throw new DomainException(ErrorCode.INVALID_INPUT, "archive too large");
                    }
                    content.write(buffer, 0, read);
                }
                entries.put(entry.getName(), content.toByteArray());
            }
        } catch (IOException error) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "cannot read archive", error);
        }
        return entries;
    }
}
