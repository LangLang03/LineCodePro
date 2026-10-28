package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.data.api.TerminalProviderStore;

/** External terminal providers discovered via IPC. */
public final class SqliteTerminalProviderStore implements TerminalProviderStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    public SqliteTerminalProviderStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override public List<TerminalProviderConfig> list() {
        List<TerminalProviderConfig> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("ipc_providers", null, null, null,
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(new TerminalProviderConfig(
                        text(cursor, "id"),
                        cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0,
                        text(cursor, "name"),
                        text(cursor, "package_name"),
                        text(cursor, "service_class"),
                        text(cursor, "provider_type"),
                        cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))));
            }
        }
        return result;
    }

    @Override public TerminalProviderConfig add(TerminalProviderConfig input) {
        for (TerminalProviderConfig existing : list()) {
            if (existing.packageName.equals(input.packageName)
                    && existing.serviceClass.equals(input.serviceClass)) {
                return existing;
            }
        }
        long now = clock.nowMs();
        TerminalProviderConfig saved = new TerminalProviderConfig(
                input.id.isEmpty() ? Ids.prefixed("ipc") : input.id, input.enabled,
                input.name, input.packageName, input.serviceClass, input.providerType,
                now, now);
        ContentValues values = new ContentValues();
        values.put("id", saved.id);
        values.put("enabled", saved.enabled ? 1 : 0);
        values.put("name", saved.name);
        values.put("package_name", saved.packageName);
        values.put("service_class", saved.serviceClass);
        values.put("provider_type", saved.providerType);
        values.put("created_at", saved.createdAt);
        values.put("updated_at", saved.updatedAt);
        try {
            database.open().insertWithOnConflict("ipc_providers", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (RuntimeException e) {
            throw new DomainException(ErrorCode.STORAGE, "add terminal provider", e);
        }
        return saved;
    }

    @Override public void setEnabled(String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", clock.nowMs());
        database.open().update("ipc_providers", values, "id=?", new String[] {id});
    }

    @Override public void delete(String id) {
        database.open().delete("ipc_providers", "id=?", new String[] {id});
    }

    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }
}
