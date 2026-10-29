package cn.lineai.tool.api;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Thin, UI-safe JSON wrapper for tool arguments/results.
 *
 * <p>Feature modules are not allowed to import {@code org.json} directly, but tool cards still need
 * to read a handful of fields. This wrapper keeps the parser on the tool boundary and exposes only
 * the primitives the UI needs: text, flags, integers, nested objects and arrays.</p>
 */
public final class ToolJson {

    private static final ToolJson EMPTY = new ToolJson(new JSONObject());

    private final JSONObject value;

    ToolJson(JSONObject value) {
        this.value = value;
    }

    public static ToolJson empty() {
        return EMPTY;
    }

    /** Returns {@code null} when the text is not a valid JSON object. */
    public static ToolJson tryParse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return new ToolJson(new JSONObject(json));
        } catch (Exception invalid) {
            return null;
        }
    }

    /** Parses to an empty object when the text is missing or malformed. */
    public static ToolJson parse(String json) {
        ToolJson parsed = tryParse(json);
        return parsed == null ? EMPTY : parsed;
    }

    public boolean isEmpty() {
        return value.length() == 0;
    }

    public int length() {
        return value.length();
    }

    public boolean has(String key) {
        return !value.isNull(key);
    }

    public String text(String key) {
        return value.optString(key, "");
    }

    public String text(String key, String fallback) {
        String result = value.optString(key, fallback);
        return result == null ? fallback : result;
    }

    public int integer(String key, int fallback) {
        return value.optInt(key, fallback);
    }

    public boolean flag(String key) {
        return value.optBoolean(key, false);
    }

    /** Nested object, or {@code null} when the key is absent or holds another type. */
    public ToolJson child(String key) {
        JSONObject object = value.optJSONObject(key);
        return object == null ? null : new ToolJson(object);
    }

    /** Nested array, or {@code null} when the key is absent or holds another type. */
    public ToolJsonArray array(String key) {
        JSONArray array = value.optJSONArray(key);
        return array == null ? null : new ToolJsonArray(array);
    }

    /** Indented JSON for code cards. */
    public String pretty() {
        try {
            return value.toString(2);
        } catch (Exception ignored) {
            return value.toString();
        }
    }
}
