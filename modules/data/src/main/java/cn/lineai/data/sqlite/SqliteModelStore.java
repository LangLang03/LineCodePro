package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.SettingsStore;

/** SQLite-backed model configurations; the selected row is the chat default. */
public final class SqliteModelStore implements ModelStore {

    public static final String KEY_BASE_URL = "model.baseUrl";
    public static final String KEY_API_KEY = "model.apiKey";
    public static final String KEY_MODEL_ID = "model.modelId";
    public static final String KEY_MODEL_NAME = "model.name";
    public static final String KEY_CONTEXT_SIZE = "model.contextSize";
    public static final String KEY_MAX_TOKENS = "model.maxTokens";

    private final LineCodeDatabase database;

    public SqliteModelStore(LineCodeDatabase database) {
        this.database = database;
    }

    @Override
    public List<ModelConfig> list() {
        List<ModelConfig> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("models", null, null, null, null, null,
                "is_default DESC, name ASC")) {
            while (cursor.moveToNext()) result.add(read(cursor));
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "list models", error);
        }
        return result;
    }

    @Override
    public ModelConfig find(String id) {
        try (Cursor cursor = database.open().query("models", null, "id=?",
                new String[] {id}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "find model", error);
        }
    }

    @Override
    public ModelConfig selected() {
        List<ModelConfig> models = list();
        if (models.isEmpty()) return null;
        for (ModelConfig model : models) if (model.isDefault) return model;
        return models.get(0);
    }

    @Override
    public void save(ModelConfig model) {
        if (model == null) throw new IllegalArgumentException("model == null");
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            boolean first = count(db) == 0;
            ContentValues values = write(model, model.isDefault || first);
            long row = db.insertWithOnConflict("models", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            if (row == -1) throw new SQLiteException("insert model");
            if (model.isDefault || first) clearOtherDefaults(db, model.id);
            db.setTransactionSuccessful();
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "save model", error);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void select(String id) {
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            ContentValues clear = new ContentValues();
            clear.put("is_default", 0);
            db.update("models", clear, null, null);
            ContentValues select = new ContentValues();
            select.put("is_default", 1);
            db.update("models", select, "id=?", new String[] {id});
            db.setTransactionSuccessful();
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "select model", error);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            for (String id : ids) db.delete("models", "id=?", new String[] {id});
            if (count(db) > 0) {
                try (Cursor cursor = db.query("models", new String[] {"id"}, null, null,
                        null, null, "is_default DESC, name ASC LIMIT 1")) {
                    if (cursor.moveToFirst()) {
                        ContentValues values = new ContentValues();
                        values.put("is_default", 1);
                        db.update("models", values, "id=?", new String[] {cursor.getString(0)});
                    }
                }
            }
            db.setTransactionSuccessful();
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "delete models", error);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void seedFromSettings(SettingsStore settings) {
        if (!list().isEmpty()) return;
        String baseUrl = settings.get(KEY_BASE_URL, "");
        String modelId = settings.get(KEY_MODEL_ID, "");
        if (baseUrl.isEmpty() || modelId.isEmpty()) return;
        save(ModelConfig.builder()
                .identity(Ids.prefixed("model"), settings.get(KEY_MODEL_NAME, modelId))
                .endpoint("custom", modelId, baseUrl)
                .secret(settings.get(KEY_API_KEY, ""))
                .context(settings.getInt(KEY_CONTEXT_SIZE, 128000),
                        settings.getInt(KEY_MAX_TOKENS, 4096))
                .flags(true, true)
                .build());
    }

    private static int count(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM models", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static void clearOtherDefaults(SQLiteDatabase db, String keep) {
        ContentValues values = new ContentValues();
        values.put("is_default", 0);
        db.update("models", values, "id<>?", new String[] {keep});
    }

    private static ContentValues write(ModelConfig model, boolean isDefault) {
        ContentValues values = new ContentValues();
        values.put("id", model.id);
        values.put("name", model.displayName());
        values.put("provider_id", model.providerId);
        values.put("provider_label", model.providerLabel);
        values.put("model_id", model.modelId);
        values.put("base_url", model.baseUrl);
        values.put("api_key", model.apiKey);
        values.put("protocol", model.protocol.name());
        values.put("context_size", model.contextSize);
        values.put("temperature", model.temperature);
        values.put("max_tokens", model.maxTokens);
        values.put("reasoning_effort", model.reasoningEffort.name());
        values.put("enabled", model.enabled ? 1 : 0);
        values.put("is_default", isDefault ? 1 : 0);
        values.put("supports_vision", model.supportsVision ? 1 : 0);
        values.put("supports_tools", model.supportsTools ? 1 : 0);
        values.put("tool_call_limit", model.toolCallLimit);
        values.put("compression_enabled", model.compressionEnabled ? 1 : 0);
        values.put("compression_auto", model.compressionAuto ? 1 : 0);
        values.put("compression_model_id", model.compressionModelId);
        values.put("local_model_path", model.localModelPath);
        values.put("local_acceleration", model.localAcceleration);
        return values;
    }

    private static ModelConfig read(Cursor cursor) {
        return ModelConfig.builder()
                .identity(cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")))
                .endpoint(cursor.getString(cursor.getColumnIndexOrThrow("provider_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("model_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("base_url")))
                .secret(cursor.getString(cursor.getColumnIndexOrThrow("api_key")))
                .providerLabel(cursor.getString(cursor.getColumnIndexOrThrow("provider_label")))
                .toolCalls(cursor.getInt(cursor.getColumnIndexOrThrow("tool_call_limit")))
                .compression(
                        cursor.getInt(cursor.getColumnIndexOrThrow("compression_enabled")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("compression_auto")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("compression_model_id")))
                .local(cursor.getString(cursor.getColumnIndexOrThrow("local_model_path")),
                        cursor.getString(cursor.getColumnIndexOrThrow("local_acceleration")))
                .protocol(protocol(cursor.getString(cursor.getColumnIndexOrThrow("protocol"))))
                .context(cursor.getInt(cursor.getColumnIndexOrThrow("context_size")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("max_tokens")))
                .sampling(cursor.getFloat(cursor.getColumnIndexOrThrow("temperature")),
                        effort(cursor.getString(cursor.getColumnIndexOrThrow("reasoning_effort"))))
                .flags(cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1)
                .capabilities(cursor.getInt(cursor.getColumnIndexOrThrow("supports_vision")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("supports_tools")) == 1)
                .build();
    }

    private static ModelConfig.Protocol protocol(String value) {
        try {
            return ModelConfig.Protocol.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            return ModelConfig.Protocol.OPENAI_COMPATIBLE;
        }
    }

    private static ModelConfig.ReasoningEffort effort(String value) {
        try {
            return ModelConfig.ReasoningEffort.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            return ModelConfig.ReasoningEffort.MEDIUM;
        }
    }
}
