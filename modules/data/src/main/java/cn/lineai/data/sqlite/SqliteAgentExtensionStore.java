package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.AgentExtension;
import cn.lineai.data.api.AgentExtensionStore;

/** Agent extensions stored in one row each; tool names are newline-separated. */
public final class SqliteAgentExtensionStore implements AgentExtensionStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    public SqliteAgentExtensionStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override
    public List<AgentExtension> list() {
        List<AgentExtension> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("extension_agents", null, null, null,
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(new AgentExtension.Builder()
                        .id(text(cursor, "id"))
                        .enabled(cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0)
                        .name(text(cursor, "name"))
                        .slug(text(cursor, "slug"))
                        .prompt(text(cursor, "prompt"))
                        .trigger(text(cursor, "trigger"))
                        .toolNames(toolNames(text(cursor, "tool_names")))
                        .times(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                        .build());
            }
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "list agent extensions", e);
        }
        return result;
    }

    @Override
    public AgentExtension save(AgentExtension input) {
        AgentExtension source = input == null ? AgentExtension.builder().build() : input;
        long now = clock.nowMs();
        String id = source.id.isEmpty() ? Ids.prefixed("agent") : source.id;
        long created = source.createdAt <= 0 ? now : source.createdAt;
        AgentExtension saved = source.toBuilder()
                .id(id)
                .slug(normalizeSlug(source.slug, source.name))
                .times(created, now)
                .build();
        ContentValues values = new ContentValues();
        values.put("id", saved.id);
        values.put("enabled", saved.enabled ? 1 : 0);
        values.put("name", saved.name);
        values.put("slug", saved.slug);
        values.put("prompt", saved.prompt);
        values.put("trigger", saved.trigger);
        values.put("tool_names", joinToolNames(saved.toolNames));
        values.put("created_at", saved.createdAt);
        values.put("updated_at", saved.updatedAt);
        try {
            database.open().insertWithOnConflict("extension_agents", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteException e) {
            throw new DomainException(ErrorCode.STORAGE, "save agent extension", e);
        }
        return saved;
    }

    @Override
    public void setEnabled(String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", clock.nowMs());
        database.open().update("extension_agents", values, "id=?", new String[] {id});
    }

    @Override
    public void delete(String id) {
        database.open().delete("extension_agents", "id=?", new String[] {id});
    }

    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static List<String> toolNames(String raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) return names;
        for (String line : raw.split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) names.add(value);
        }
        return names;
    }

    private static String joinToolNames(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(name.trim());
        }
        return out.toString();
    }

    private static String normalizeSlug(String raw, String fallback) {
        String source = raw == null || raw.trim().isEmpty() ? fallback : raw;
        String lower = source == null ? "" : source.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_') {
                builder.append(ch);
            } else if (Character.isWhitespace(ch)) {
                builder.append('-');
            }
        }
        String value = builder.toString();
        while (value.contains("--")) value = value.replace("--", "-");
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') start++;
        while (end > start && value.charAt(end - 1) == '-') end--;
        value = value.substring(start, end);
        return value.isEmpty() ? "agent-" + System.currentTimeMillis() : value;
    }
}
