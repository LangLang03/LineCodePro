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
    private static final int VERSION = 2;
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    /** Per-file cap mirrored from {@link #writeDirectory}; enforced again on import. */
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final String STAGING_DIR = ".restore-staging";
    private static final java.util.List<String> RESTORE_ROOTS =
            java.util.Arrays.asList("workspace", "skills", "attachments");
    /** Order matters: parents must be restored before the rows that reference them. */
    private static final String[] TABLES = {
            "conversations", "messages", "message_blocks", "message_tool_calls",
            "settings", "models", "error_logs", "memories",
            "extension_agents", "extension_mcps", "file_diffs", "projects", "ipc_providers"
    };
    /** v1 archives carried only the conversation/settings core. */
    private static final String[] V1_TABLES = {
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
                JSONArray rows = sanitize(table, readTable(db, table));
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
        try {
            if (!entries.containsKey("manifest.json")) {
                throw new DomainException(ErrorCode.INVALID_INPUT, "missing manifest");
            }
            JSONObject manifest = new JSONObject(text(entries, "manifest.json"));
            int version = manifest.optInt("version");
            if (!FORMAT.equals(manifest.optString("format")) || version < 1 || version > VERSION) {
                throw new DomainException(ErrorCode.INVALID_INPUT, "unsupported archive");
            }
            requireEntries(entries, version);
            // Validate (and stage) every file entry before any live data is touched, so a
            // malformed archive can never leave the database restored but files destroyed.
            if (filesDir != null) validateFileEntries(entries);
            SQLiteDatabase db = database.open();
            db.beginTransaction();
            try {
                for (String table : TABLES) db.delete(table, null, null);
                for (String table : TABLES) {
                    String payload = text(entries, table + ".json");
                    if (payload.isEmpty()) continue; // v1 archives carry only the core tables
                    JSONArray rows = new JSONArray(payload);
                    for (int i = 0; i < rows.length(); i++) {
                        db.insertOrThrow(table, null, values(rows.getJSONObject(i)));
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            if (filesDir != null) restoreFiles(entries);
            return new Summary(
                    new JSONArray(text(entries, "conversations.json")).length(),
                    new JSONArray(text(entries, "models.json")).length(),
                    new JSONArray(text(entries, "settings.json")).length());
        } catch (JSONException | IOException error) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "invalid archive", error);
        }
    }

    private static void requireEntries(Map<String, byte[]> entries, int version) {
        String[] required = version < 2 ? V1_TABLES : TABLES;
        for (String table : required) {
            if (!entries.containsKey(table + ".json")) {
                throw new DomainException(ErrorCode.INVALID_INPUT, "missing " + table);
            }
        }
    }

    /** Exports must not contain secrets: models lose api_key, secret settings rows are dropped. */
    private static JSONArray sanitize(String table, JSONArray rows) throws JSONException {
        if ("models".equals(table)) {
            for (int i = 0; i < rows.length(); i++) rows.getJSONObject(i).remove("api_key");
            return rows;
        }
        if ("settings".equals(table)) {
            JSONArray kept = new JSONArray();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!isSecretSetting(row.optString("key"))) kept.put(row);
            }
            return kept;
        }
        return rows;
    }

    private static final String[] SECRET_KEY_SUFFIXES = {
            "apikey", "password", "privatekey", "passphrase"
    };

    private static boolean isSecretSetting(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT)
                .replace("_", "").replace("-", "");
        for (String suffix : SECRET_KEY_SUFFIXES) {
            if (normalized.endsWith(suffix)) return true;
        }
        return false;
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

    /** Rejects any files/ entry that could escape its root or exceed the export cap. */
    private static void validateFileEntries(Map<String, byte[]> entries) throws IOException {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("files/")) continue;
            String[] parts = key.split("/", 3);
            if (parts.length < 3) continue; // directory marker, nothing to write
            if (!RESTORE_ROOTS.contains(parts[1])) {
                throw new IOException("unsafe archive root: " + key);
            }
            String relative = parts[2];
            if (relative.isEmpty() || relative.contains("..") || relative.startsWith("/")) {
                throw new IOException("unsafe archive path: " + key);
            }
            if (entry.getValue().length > MAX_FILE_BYTES) {
                throw new IOException("archive file too large: " + key);
            }
        }
    }

    private void restoreFiles(Map<String, byte[]> entries) throws IOException {
        // Stage every file first, swap the roots in only after the database has committed.
        File staging = new File(filesDir, STAGING_DIR);
        deleteTree(staging);
        stageFiles(entries, staging);
        for (String name : RESTORE_ROOTS) {
            boolean present = false;
            for (String entry : entries.keySet()) {
                if (entry.startsWith("files/" + name + "/")) {
                    present = true;
                    break;
                }
            }
            if (present) deleteTree(new File(filesDir, name));
        }
        for (String name : RESTORE_ROOTS) {
            File staged = new File(staging, name);
            if (!staged.exists()) continue;
            if (!staged.renameTo(new File(filesDir, name))) {
                throw new IOException("archive restore could not activate " + name);
            }
        }
        deleteTree(staging);
    }

    private static void stageFiles(Map<String, byte[]> entries, File staging) throws IOException {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("files/")) continue;
            String[] parts = key.split("/", 3);
            if (parts.length < 3 || parts[2].isEmpty()) continue;
            if (!RESTORE_ROOTS.contains(parts[1])) continue; // already rejected on validation
            File target = new File(new File(staging, parts[1]), parts[2]);
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
