package cn.lineai.data.migration;

/** Converts the legacy JSON-encoded extension lists into the new tab/newline format. */
final class LegacyJson {

    private LegacyJson() {
    }

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
