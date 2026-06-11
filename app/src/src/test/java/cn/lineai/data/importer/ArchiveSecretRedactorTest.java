package cn.lineai.data.importer;

import static org.junit.Assert.assertEquals;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class ArchiveSecretRedactorTest {
    @Test
    public void redactsLegacyModelsAndSettings() throws Exception {
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        settings.put("@lineai_ssh_config", new JSONObject()
                .put("host", "127.0.0.1")
                .put("password", "ssh-password")
                .put("privateKey", "private-key")
                .put("passphrase", "passphrase")
                .toString());
        settings.put("@lineai_web_search_config", new JSONObject()
                .put("provider", "tavily")
                .put("apiKey", "search-key")
                .toString());
        settings.put("@lineai_permission_mode", "ask");

        ImportedLineCodeData redacted = ArchiveSecretRedactor.redactData(new ImportedLineCodeData(
                Collections.singletonList(new ModelConfig(
                        "m1",
                        "OpenAI",
                        ModelProtocolType.OPENAI_COMPATIBLE,
                        "OpenAI",
                        "https://api.example.test",
                        "sk-secret",
                        "gpt-test"
                )),
                "m1",
                Collections.emptyList(),
                "",
                settings
        ));

        assertEquals("", redacted.getModels().get(0).getApiKey());
        JSONObject ssh = new JSONObject(redacted.getSettings().get("@lineai_ssh_config"));
        assertEquals("", ssh.optString("password"));
        assertEquals("", ssh.optString("privateKey"));
        assertEquals("", ssh.optString("passphrase"));
        JSONObject web = new JSONObject(redacted.getSettings().get("@lineai_web_search_config"));
        assertEquals("", web.optString("apiKey"));
        assertEquals("ask", redacted.getSettings().get("@lineai_permission_mode"));
    }

    @Test
    public void redactsDatabaseSnapshotSecrets() throws Exception {
        JSONObject snapshot = new JSONObject()
                .put("format", "linecode-database")
                .put("tables", new JSONObject()
                        .put("settings", table(
                                row("key", "@lineai_ssh_config", "value", new JSONObject()
                                        .put("password", "ssh-password")
                                        .put("privateKey", "private-key")
                                        .put("passphrase", "passphrase")
                                        .toString())
                        ))
                        .put("model_configs", table(
                                row("api_key", "sk-secret", "raw_json", new JSONObject()
                                        .put("apiKey", "sk-secret")
                                        .put("name", "OpenAI")
                                        .toString())
                        ))
                        .put("extension_mcps", table(
                                row("request_headers_json", new JSONArray()
                                                .put(new JSONObject().put("name", "Authorization").put("value", "Bearer secret"))
                                                .put(new JSONObject().put("name", "X-Trace").put("value", "trace-id"))
                                                .toString(),
                                        "raw_json", new JSONObject().put("token", "mcp-token").toString())
                        )));

        JSONObject redacted = ArchiveSecretRedactor.redactDatabaseSnapshot(snapshot);

        JSONObject tables = redacted.getJSONObject("tables");
        assertEquals("", cell(tables, "model_configs", 0, "api_key"));
        assertEquals("", new JSONObject(cell(tables, "model_configs", 0, "raw_json")).optString("apiKey"));
        JSONObject ssh = new JSONObject(cell(tables, "settings", 0, "value"));
        assertEquals("", ssh.optString("password"));
        assertEquals("", ssh.optString("privateKey"));
        JSONArray headers = new JSONArray(cell(tables, "extension_mcps", 0, "request_headers_json"));
        assertEquals("", headers.getJSONObject(0).optString("value"));
        assertEquals("trace-id", headers.getJSONObject(1).optString("value"));
        assertEquals("", new JSONObject(cell(tables, "extension_mcps", 0, "raw_json")).optString("token"));
    }

    private static JSONObject table(JSONObject... rows) throws Exception {
        JSONArray array = new JSONArray();
        for (JSONObject row : rows) {
            array.put(row);
        }
        return new JSONObject().put("rows", array);
    }

    private static JSONObject row(String firstColumn, String firstValue, String secondColumn, String secondValue) throws Exception {
        return new JSONObject()
                .put(firstColumn, cell(firstValue))
                .put(secondColumn, cell(secondValue));
    }

    private static JSONObject cell(String value) throws Exception {
        return new JSONObject().put("type", "string").put("value", value);
    }

    private static String cell(JSONObject tables, String table, int row, String column) {
        return tables.optJSONObject(table)
                .optJSONArray("rows")
                .optJSONObject(row)
                .optJSONObject(column)
                .optString("value");
    }
}
