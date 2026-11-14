package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.McpHeader;
import cn.lineai.core.model.McpToolInfo;
import cn.lineai.data.api.McpExtensionStore;

/** MCP servers with tab-separated header and tool records. */
public final class SqliteMcpExtensionStore implements McpExtensionStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param clock clock used to timestamp saved servers
     */
    public SqliteMcpExtensionStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Reads every configured MCP server, newest update first.
     *
     * @return all stored server configurations
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public List<McpExtension> list() {
        List<McpExtension> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("extension_mcps", null, null, null,
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(new McpExtension.Builder()
                        .id(text(cursor, "id"))
                        .enabled(cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0)
                        .name(text(cursor, "name"))
                        .url(text(cursor, "url"))
                        .headers(headers(text(cursor, "headers")))
                        .tools(tools(text(cursor, "tools")))
                        .times(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                        .build());
            }
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "list MCP extensions", e);
        }
        return result;
    }

    /**
     * Inserts or replaces one server configuration, assigning an identifier when missing,
     * trimming name and URL, and preserving the original creation time on update.
     *
     * @param input configuration to persist; null is treated as an empty configuration
     * @return the stored form including generated identity fields
     * @throws DomainException with {@code ErrorCode.STORAGE} when the write fails
     */
    @Override
    public McpExtension save(McpExtension input) {
        McpExtension source = input == null ? McpExtension.builder().build() : input;
        long now = clock.nowMs();
        McpExtension saved = source.toBuilder()
                .id(source.id.isEmpty() ? Ids.prefixed("mcp") : source.id)
                .name(source.name.trim())
                .url(source.url.trim())
                .times(source.createdAt <= 0 ? now : source.createdAt, now)
                .build();
        ContentValues values = new ContentValues();
        values.put("id", saved.id);
        values.put("enabled", saved.enabled ? 1 : 0);
        values.put("name", saved.name);
        values.put("url", saved.url);
        values.put("headers", joinHeaders(saved.headers));
        values.put("tools", joinTools(saved.tools));
        values.put("created_at", saved.createdAt);
        values.put("updated_at", saved.updatedAt);
        try {
            database.open().insertWithOnConflict("extension_mcps", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "save MCP extension", e);
        }
        return saved;
    }

    /**
     * Toggles the enabled flag without rewriting the configuration.
     *
     * @param id server identifier; unknown identifiers are ignored
     * @param enabled whether the server may be contacted
     */
    @Override
    public void setEnabled(String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", clock.nowMs());
        database.open().update("extension_mcps", values, "id=?", new String[] {id});
    }

    /**
     * Removes the server configuration with the given identifier.
     *
     * @param id server identifier; unknown identifiers are ignored
     */
    @Override
    public void delete(String id) {
        database.open().delete("extension_mcps", "id=?", new String[] {id});
    }

    /** Reads one required text column. */
    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    /** Parses the newline/tab-separated column into header pairs, skipping malformed lines. */
    private static List<McpHeader> headers(String raw) {
        List<McpHeader> result = new ArrayList<>();
        if (raw == null) return result;
        for (String line : raw.split("\n")) {
            int split = line.indexOf('\t');
            if (split <= 0) continue;
            String name = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            if (!name.isEmpty()) result.add(new McpHeader(name, value));
        }
        return result;
    }

    /** Encodes header pairs into the newline/tab-separated storage format. */
    private static String joinHeaders(List<McpHeader> headers) {
        StringBuilder out = new StringBuilder();
        for (McpHeader header : headers) {
            if (header.name.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(clean(header.name)).append('\t').append(clean(header.value));
        }
        return out.toString();
    }

    /** Parses the four-field tab-separated column back into tool records. */
    private static List<McpToolInfo> tools(String raw) {
        List<McpToolInfo> result = new ArrayList<>();
        if (raw == null) return result;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", 4);
            if (parts.length < 2 || parts[0].trim().isEmpty()) continue;
            result.add(new McpToolInfo(parts[0].trim(), "1".equals(parts[1]),
                    parts.length > 2 ? parts[2] : "",
                    parts.length > 3 ? parts[3] : ""));
        }
        return result;
    }

    /** Encodes tool records into the four-field tab-separated storage format. */
    private static String joinTools(List<McpToolInfo> tools) {
        StringBuilder out = new StringBuilder();
        for (McpToolInfo tool : tools) {
            if (tool.name.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(clean(tool.name)).append('\t').append(tool.enabled ? '1' : '0')
                    .append('\t').append(clean(tool.description))
                    .append('\t').append(clean(tool.inputSchemaJson));
        }
        return out.toString();
    }

    /** Replaces tab and newline characters so a value cannot break the record encoding. */
    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
