package cn.lineai.ai.prompt;

import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.tool.ToolInfo;
import java.lang.reflect.Proxy;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class StablePromptSerializationTest {
    @Test public void templateSubstitutionIsSinglePassAndIndependentOfMapOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("LEARNING_CONTEXT", "literal {{TOOLS_CONTEXT}} $1 \\ data");
        first.put("TOOLS_CONTEXT", "tools");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("TOOLS_CONTEXT", "tools");
        second.put("LEARNING_CONTEXT", first.get("LEARNING_CONTEXT"));
        StringTemplate template = new StringTemplate("{{LEARNING_CONTEXT}} / {{TOOLS_CONTEXT}} / {{UNKNOWN}}");
        assertEquals(template.render(first), template.render(second));
        assertEquals("literal {{TOOLS_CONTEXT}} $1 \\ data / tools / {{UNKNOWN}}", template.render(first));
    }

    @Test public void nestedJsonUsesStableObjectKeysAndPreservesArrayAndStringValues() throws Exception {
        JSONObject first = new JSONObject().put("z", new JSONObject().put("b", 2).put("a", 1))
                .put("a", new JSONArray().put("z").put("a"))
                .put("text", "中文\n\\\"{{TOOLS_CONTEXT}}").put("null", JSONObject.NULL).put("bool", true);
        JSONObject second = new JSONObject().put("bool", true).put("null", JSONObject.NULL)
                .put("text", first.get("text")).put("a", first.get("a"))
                .put("z", new JSONObject().put("a", 1).put("b", 2));
        String serialized = StableJson.stringify(first);
        assertEquals(serialized, StableJson.stringify(second));
        JSONObject decoded = new JSONObject(serialized);
        assertEquals("z", decoded.getJSONArray("a").getString(0));
        assertEquals("a", decoded.getJSONArray("a").getString(1));
        assertEquals(first.getString("text"), decoded.getString("text"));
        assertEquals(2, decoded.getJSONObject("z").getInt("b"));
        assertTrue(decoded.isNull("null"));
        assertTrue(decoded.getBoolean("bool"));
    }

    @Test public void toolRegistrationOrderDoesNotChangeNativeRequestOrder() {
        ToolInfo a = tool("a"), z = tool("z");
        List<ToolInfo> input = new ArrayList<>(Arrays.asList(z, a));
        ModelRequestOptions first = new ModelRequestOptions("medium", false, input);
        ModelRequestOptions second = new ModelRequestOptions("medium", false, Arrays.asList(a, z));
        assertEquals(first.getTools(), second.getTools());
        assertSame(z, input.get(0));
    }

    private static ToolInfo tool(String name) {
        return (ToolInfo) Proxy.newProxyInstance(ToolInfo.class.getClassLoader(), new Class<?>[]{ToolInfo.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : null);
    }
}
