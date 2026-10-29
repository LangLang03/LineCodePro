package cn.lineai.tool.api;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only array view used by {@link ToolJson} for tool payloads with repeated entries. */
public final class ToolJsonArray {

    private final JSONArray value;

    ToolJsonArray(JSONArray value) {
        this.value = value;
    }

    public int length() {
        return value.length();
    }

    public boolean isEmpty() {
        return value.length() == 0;
    }

    /** Object at {@code index}, or {@code null} when the slot holds another type. */
    public ToolJson object(int index) {
        JSONObject object = value.optJSONObject(index);
        return object == null ? null : new ToolJson(object);
    }

    public String text(int index) {
        return value.optString(index, "");
    }

    public boolean flag(int index) {
        return value.optBoolean(index, false);
    }
}
