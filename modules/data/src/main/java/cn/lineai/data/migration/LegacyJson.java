package cn.lineai.data.migration;

/**
 * Converts the legacy JSON-encoded extension lists into the new tab/newline format.
 *
 * <p>The legacy schema stored agent tool lists and MCP headers/tools as JSON documents; the new
 * schema stores them as delimited text columns. Unparseable input converts to empty output rather
 * than failing the migration.</p>
 */
final class LegacyJson {

    /** Not instantiable; the converters are static helpers. */
    private LegacyJson() {
    }

    /**
     * Converts a legacy JSON array of strings into a newline-separated list.
     *
     * @param json legacy JSON document or any unparseable text
     * @return the trimmed non-empty items joined by newlines, or empty on parse failure
     */
    static String lines(String json) {
        try {
            org.json.JSONArray array = new org.json.JSONArray(json);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, "").trim();
                if (item.isEmpty()) continue;
                if (out.length() > 0) out.append('\n');
                out.append(item);
            }
            return out.toString();
        } catch (org.json.JSONException error) {
            return "";
        }
    }

    /**
     * Converts a legacy JSON array of header objects into newline/tab-separated name-value pairs.
     *
     * @param json legacy JSON document or any unparseable text
     * @return {@code name}+tab+{@code value} lines, or empty on parse failure
     */
    static String headers(String json) {
        try {
            org.json.JSONArray array = new org.json.JSONArray(json);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) continue;
                if (out.length() > 0) out.append('\n');
                out.append(name).append('\t').append(item.optString("value", ""));
            }
            return out.toString();
        } catch (org.json.JSONException error) {
            return "";
        }
    }

    /**
     * Converts a legacy JSON array of tool objects into the four-field tool record format
     * (name, enabled flag, description, input schema JSON).
     *
     * @param json legacy JSON document or any unparseable text
     * @return tab-separated tool records joined by newlines, or empty on parse failure
     */
    static String tools(String json) {
        try {
            org.json.JSONArray array = new org.json.JSONArray(json);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) continue;
                if (out.length() > 0) out.append('\n');
                out.append(name).append('\t').append(item.optBoolean("enabled", true) ? '1' : '0')
                        .append('\t').append(item.optString("description", ""))
                        .append('\t').append(item.optJSONObject("inputSchema") == null
                                ? "" : item.optJSONObject("inputSchema").toString());
            }
            return out.toString();
        } catch (org.json.JSONException error) {
            return "";
        }
    }

}
