package cn.lineai.tool.api;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Read-only array view used by {@link ToolJson} for tool payloads with repeated entries.
 *
 * <p>Accessors degrade to defaults instead of throwing so a card renders even when the model
 * produced unexpected element types.</p>
 */
public final class ToolJsonArray {

    private final JSONArray value;

    /**
     * Wraps an array; created only by {@link ToolJson#array(String)}.
     *
     * @param value backing {@code org.json} array, never {@code null}
     */
    ToolJsonArray(JSONArray value) {
        this.value = value;
    }

    /**
     * Number of elements in the array.
     *
     * @return element count
     */
    public int length() {
        return value.length();
    }

    /**
     * Whether the array holds no elements.
     *
     * @return {@code true} when the array is empty
     */
    public boolean isEmpty() {
        return value.length() == 0;
    }

    /**
     * Object at {@code index}, or {@code null} when the slot holds another type.
     *
     * @param index element position
     * @return wrapped object element, or {@code null}
     */
    public ToolJson object(int index) {
        JSONObject object = value.optJSONObject(index);
        return object == null ? null : new ToolJson(object);
    }

    /**
     * Text at {@code index}.
     *
     * @param index element position
     * @return string value, or the empty string when absent or non-textual
     */
    public String text(int index) {
        return value.optString(index, "");
    }

    /**
     * Boolean at {@code index}.
     *
     * @param index element position
     * @return boolean value, or {@code false} when absent or non-boolean
     */
    public boolean flag(int index) {
        return value.optBoolean(index, false);
    }
}
