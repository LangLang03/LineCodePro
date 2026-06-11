package cn.lineai.data.importer;

import cn.lineai.model.ModelConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

final class ArchiveSecretRedactor {
    private static final String REDACTED = "";

    private ArchiveSecretRedactor() {
    }

    static ImportedLineCodeData redactData(ImportedLineCodeData data) {
        ImportedLineCodeData safeData = data == null
                ? new ImportedLineCodeData(null, "", null, "", null)
                : data;
        ArrayList<ModelConfig> models = new ArrayList<>();
        for (ModelConfig model : safeData.getModels()) {
            models.add(redactModel(model));
        }
        return new ImportedLineCodeData(
                models,
                safeData.getSelectedModelId(),
                safeData.getConversations(),
                safeData.getCurrentConversationId(),
                redactSettings(safeData.getSettings())
        );
    }

    static JSONObject redactDatabaseSnapshot(JSONObject snapshot) {
        if (snapshot == null) {
            return null;
        }
        JSONObject redacted;
        try {
            redacted = new JSONObject(snapshot.toString());
        } catch (Exception ignored) {
            return snapshot;
        }
        JSONObject tables = redacted.optJSONObject("tables");
        if (tables == null) {
            return redacted;
        }
        redactSettingsTable(tables.optJSONObject("settings"));
        redactModelTable(tables.optJSONObject("model_configs"));
        redactMcpTable(tables.optJSONObject("extension_mcps"));
        return redacted;
    }

    static Map<String, String> redactSettings(Map<String, String> settings) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (settings == null) {
            return values;
        }
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() == 0) {
                continue;
            }
            values.put(key, redactSettingValue(key, entry.getValue()));
        }
        return values;
    }

    private static ModelConfig redactModel(ModelConfig model) {
        if (model == null) {
            return new ModelConfig("", "", null, "", "", "", "");
        }
        return new ModelConfig(
                model.getId(),
                model.getName(),
                model.getProtocolType(),
                model.getProviderLabel(),
                model.getBaseUrl(),
                REDACTED,
                model.getModelId(),
                model.getToolCallLimit(),
                model.isCompressionModelEnabled(),
                model.isCompressionModelAuto(),
                model.getCompressionModelId()
        );
    }

    private static void redactSettingsTable(JSONObject table) {
        JSONArray rows = rows(table);
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String key = cellString(row, "key");
            String value = cellString(row, "value");
            setStringCell(row, "value", redactSettingValue(key, value));
        }
    }

    private static void redactModelTable(JSONObject table) {
        JSONArray rows = rows(table);
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            setStringCell(row, "api_key", REDACTED);
            setStringCell(row, "raw_json", redactModelJson(cellString(row, "raw_json")));
        }
    }

    private static void redactMcpTable(JSONObject table) {
        JSONArray rows = rows(table);
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            setStringCell(row, "request_headers_json", redactHeaderArray(cellString(row, "request_headers_json")));
            setStringCell(row, "raw_json", redactRecursiveJson(cellString(row, "raw_json")));
        }
    }

    private static String redactSettingValue(String key, String value) {
        String safeKey = key == null ? "" : key;
        if ("@lineai_ssh_config".equals(safeKey)) {
            return redactJsonFields(value, "password", "privateKey", "passphrase");
        }
        if ("@lineai_web_search_config".equals(safeKey)) {
            return redactJsonFields(value, "apiKey");
        }
        if (isSensitiveName(safeKey)) {
            return REDACTED;
        }
        return value == null ? "" : value;
    }

    private static String redactModelJson(String raw) {
        return redactJsonFields(raw, "apiKey", "api_key");
    }

    private static String redactHeaderArray(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return "[]";
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (isSensitiveName(item.optString("name"))) {
                    item.put("value", REDACTED);
                }
            }
            return array.toString();
        } catch (Exception ignored) {
            return REDACTED;
        }
    }

    private static String redactRecursiveJson(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return "";
        }
        try {
            Object parsed = raw.trim().startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
            redactRecursive(parsed);
            return parsed.toString();
        } catch (Exception ignored) {
            return isSensitiveName(raw) ? REDACTED : raw;
        }
    }

    private static String redactJsonFields(String raw, String... fields) {
        if (raw == null || raw.trim().length() == 0) {
            return "";
        }
        try {
            JSONObject object = new JSONObject(raw);
            for (String field : fields) {
                if (object.has(field)) {
                    object.put(field, REDACTED);
                }
            }
            return object.toString();
        } catch (Exception ignored) {
            return REDACTED;
        }
    }

    private static void redactRecursive(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONArray names = object.names();
            if (names == null) {
                return;
            }
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i);
                Object child = object.opt(key);
                if (isSensitiveName(key)) {
                    object.put(key, REDACTED);
                } else {
                    redactRecursive(child);
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                redactRecursive(array.opt(i));
            }
        }
    }

    private static boolean isSensitiveName(String name) {
        String value = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return value.contains("apikey")
                || value.contains("api_key")
                || value.contains("api-key")
                || value.contains("authorization")
                || value.contains("password")
                || value.contains("passwd")
                || value.contains("passphrase")
                || value.contains("privatekey")
                || value.contains("private_key")
                || value.contains("private-key")
                || value.contains("secret")
                || value.contains("token")
                || value.contains("cookie");
    }

    private static JSONArray rows(JSONObject table) {
        return table == null ? null : table.optJSONArray("rows");
    }

    private static String cellString(JSONObject row, String column) {
        JSONObject cell = row.optJSONObject(column);
        return cell == null ? "" : cell.optString("value", "");
    }

    private static void setStringCell(JSONObject row, String column, String value) {
        JSONObject cell = row.optJSONObject(column);
        if (cell == null || !"string".equals(cell.optString("type", "string"))) {
            return;
        }
        try {
            cell.put("value", value == null ? "" : value);
        } catch (Exception ignored) {
        }
    }
}
