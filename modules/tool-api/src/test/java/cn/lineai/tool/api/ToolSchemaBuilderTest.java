package cn.lineai.tool.api;

import org.json.JSONObject;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolSchemaBuilderTest {

    @Test public void buildsTypedPropertiesAndRequiredList() throws Exception {
        JSONObject schema = new JSONObject(ToolSchemaBuilder.object()
                .string("path", "Workspace path")
                .number("count", "How many")
                .enumString("mode", "Mode", "fast", "safe")
                .required("path").required("mode").build());
        assertEquals("object", schema.getString("type"));
        JSONObject properties = schema.getJSONObject("properties");
        assertEquals("string", properties.getJSONObject("path").getString("type"));
        assertEquals("number", properties.getJSONObject("count").getString("type"));
        assertEquals("safe", properties.getJSONObject("mode").getJSONArray("enum").getString(1));
        assertEquals(2, schema.getJSONArray("required").length());
    }

    @Test public void buildsNestedArrays() throws Exception {
        ToolSchemaBuilder items = ToolSchemaBuilder.object().string("content", "Task")
                .enumString("status", "Status", "pending", "completed");
        JSONObject schema = new JSONObject(ToolSchemaBuilder.object()
                .objectArray("items", "List", items).required("items").build());
        JSONObject item = schema.getJSONObject("properties").getJSONObject("items")
                .getJSONObject("items");
        assertTrue(item.has("properties"));
    }
}
