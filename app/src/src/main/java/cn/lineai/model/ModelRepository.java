package cn.lineai.model;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

public final class ModelRepository {
    private static final String PREFS = "linecode_models";
    private static final String KEY_MODELS = "models";
    private static final String KEY_SELECTED_ID = "selected_id";

    private static final String TABLE = "model_configs";
    private static final String MIGRATED_PREF_KEY = "sqlite_migrated";

    private final LineCodeDatabase database;
    private final SharedPreferences legacyPreferences;

    public ModelRepository(Context context) {
        Context appContext = context.getApplicationContext();
        database = LineCodeDatabase.getInstance(appContext);
        legacyPreferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureModelConfigColumns();
        migrateLegacyPreferencesIfNeeded();
    }

    public synchronized List<ModelConfig> getModels() {
        ArrayList<ModelConfig> models = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query(
                TABLE,
                null,
                null,
                null,
                null,
                null,
                "selected DESC, updated_at DESC"
        );
        try {
            while (cursor.moveToNext()) {
                models.add(readModel(cursor));
            }
        } finally {
            cursor.close();
        }
        return models;
    }

    public synchronized ModelConfig save(ModelConfig model) {
        ModelConfig normalized = model.getId().length() == 0
                ? model.withId(String.valueOf(System.currentTimeMillis()))
                : model;
        insertOrReplace(normalized, isSelected(normalized.getId()));
        if (getSelectedModelId().length() == 0) {
            setSelectedModelId(normalized.getId());
        }
        return normalized;
    }

    public synchronized ModelConfig getModel(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        Cursor cursor = database.getReadableDatabase().query(
                TABLE,
                null,
                "id = ?",
                new String[] {id},
                null,
                null,
                null,
                "1"
        );
        try {
            return cursor.moveToFirst() ? readModel(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized void deleteModels(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String selectedId = getSelectedModelId();
        boolean selectedDeleted = false;
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String id : ids) {
                if (id == null || id.length() == 0) {
                    continue;
                }
                if (id.equals(selectedId)) {
                    selectedDeleted = true;
                }
                db.delete(TABLE, "id = ?", new String[] {id});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (selectedDeleted) {
            setSelectedModelId("");
        }
    }

    public synchronized void setSelectedModelId(String id) {
        String safeId = id == null ? "" : id;
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues clear = new ContentValues();
            clear.put("selected", 0);
            db.update(TABLE, clear, null, null);
            if (safeId.length() > 0) {
                ContentValues selected = new ContentValues();
                selected.put("selected", 1);
                selected.put("updated_at", System.currentTimeMillis());
                db.update(TABLE, selected, "id = ?", new String[] {safeId});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized String getSelectedModelId() {
        Cursor cursor = database.getReadableDatabase().query(
                TABLE,
                new String[] {"id"},
                "selected = 1",
                null,
                null,
                null,
                "updated_at DESC",
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        List<ModelConfig> models = getModels();
        return models.isEmpty() ? "" : models.get(0).getId();
    }

    public synchronized ModelConfig getSelectedModel() {
        String selectedId = getSelectedModelId();
        List<ModelConfig> models = getModels();
        for (ModelConfig model : models) {
            if (model.getId().equals(selectedId)) {
                return model;
            }
        }
        return models.isEmpty() ? null : models.get(0);
    }

    public synchronized void clearAll() {
        database.getWritableDatabase().delete(TABLE, null, null);
    }

    private void migrateLegacyPreferencesIfNeeded() {
        if (legacyPreferences.getBoolean(MIGRATED_PREF_KEY, false)) {
            return;
        }
        if (modelCount() > 0) {
            legacyPreferences.edit().putBoolean(MIGRATED_PREF_KEY, true).apply();
            return;
        }

        String raw = legacyPreferences.getString(KEY_MODELS, "[]");
        String selectedId = legacyPreferences.getString(KEY_SELECTED_ID, "");
        try {
            JSONArray array = new JSONArray(raw);
            SQLiteDatabase db = database.getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = 0; i < array.length(); i++) {
                    ModelConfig model = ModelConfig.fromJson(array.getJSONObject(i));
                    insertOrReplace(model, model.getId().equals(selectedId));
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (JSONException ignored) {
        }
        legacyPreferences.edit().putBoolean(MIGRATED_PREF_KEY, true).apply();
    }

    private int modelCount() {
        Cursor cursor = database.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private boolean isSelected(String id) {
        return id != null && id.equals(getSelectedModelId());
    }

    private void insertOrReplace(ModelConfig model, boolean selected) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", model.getId());
        values.put("name", model.getName());
        values.put("protocol_type", model.getProtocolType().name());
        values.put("provider_label", model.getProviderLabel());
        values.put("base_url", model.getBaseUrl());
        values.put("api_key", model.getApiKey());
        values.put("model_id", model.getModelId());
        values.put("tool_call_limit", model.getToolCallLimit());
        values.put("compression_model_enabled", model.isCompressionModelEnabled() ? 1 : 0);
        values.put("compression_model_auto", model.isCompressionModelAuto() ? 1 : 0);
        values.put("compression_model_id", model.getCompressionModelId());
        values.put("selected", selected ? 1 : 0);
        try {
            values.put("raw_json", model.toJson().toString());
        } catch (JSONException ignored) {
            values.put("raw_json", "");
        }
        values.put("created_at", now);
        values.put("updated_at", now);
        database.getWritableDatabase().insertWithOnConflict(
                TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private ModelConfig readModel(Cursor cursor) {
        return new ModelConfig(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                ModelProtocolType.fromStorage(cursor.getString(cursor.getColumnIndexOrThrow("protocol_type"))),
                cursor.getString(cursor.getColumnIndexOrThrow("provider_label")),
                cursor.getString(cursor.getColumnIndexOrThrow("base_url")),
                cursor.getString(cursor.getColumnIndexOrThrow("api_key")),
                cursor.getString(cursor.getColumnIndexOrThrow("model_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("tool_call_limit")),
                cursor.getInt(cursor.getColumnIndexOrThrow("compression_model_enabled")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("compression_model_auto")) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow("compression_model_id"))
        );
    }

    private void ensureModelConfigColumns() {
        SQLiteDatabase db = database.getWritableDatabase();
        Set<String> columns = tableColumns(db, TABLE);
        if (!columns.contains("tool_call_limit")) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN tool_call_limit INTEGER NOT NULL DEFAULT "
                    + ModelConfig.DEFAULT_TOOL_CALL_LIMIT);
        }
        if (!columns.contains("compression_model_enabled")) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN compression_model_enabled INTEGER NOT NULL DEFAULT 0");
        }
        if (!columns.contains("compression_model_auto")) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN compression_model_auto INTEGER NOT NULL DEFAULT 1");
        }
        if (!columns.contains("compression_model_id")) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN compression_model_id TEXT");
        }
    }

    private Set<String> tableColumns(SQLiteDatabase db, String table) {
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
