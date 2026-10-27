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

    public SqliteMcpExtensionStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

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

    @Override
    public void setEnabled(String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", clock.nowMs());
        database.open().update("extension_mcps", values, "id=?", new String[] {id});
    }

    @Override
    public void delete(String id) {
        database.open().delete("extension_mcps", "id=?", new String[] {id});
    }

    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

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

    private static String joinHeaders(List<McpHeader> headers) {
        StringBuilder out = new StringBuilder();
        for (McpHeader header : headers) {
            if (header.name.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(clean(header.name)).append('\t').append(clean(header.value));
        }
        return out.toString();
    }

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

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
