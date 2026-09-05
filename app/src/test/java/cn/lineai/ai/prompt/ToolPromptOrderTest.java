package cn.lineai.ai.prompt;

import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.McpToolConfig;
import cn.lineai.tool.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ToolPromptOrderTest {
    @Test public void reorderedGroupsNamesAndSchemasProduceTheSameToolInstructions() throws Exception {
        Set<String> enabled = new HashSet<>(Arrays.asList("a", "b", "z"));
        Map<String, ToolInfo> tools = new HashMap<>();
        for (String name : enabled) tools.put(name, tool(name, false));
        List<McpToolConfig> first = Arrays.asList(group("z", "z"), group("a", "b", "a"));
        List<McpToolConfig> second = Arrays.asList(group("a", "a", "b"), group("z", "z"));
        Map<String, ToolInfo> reordered = new HashMap<>();
        for (String name : enabled) reordered.put(name, tool(name, true));
        for (String target : Arrays.asList(ToolSettingsStore.EXECUTION_LOCAL, ToolSettingsStore.EXECUTION_SSH,
                ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER)) {
            for (boolean nativeProtocol : new boolean[]{true, false}) {
                String one = ToolPromptRenderer.renderToolPrompt(target, first, enabled, tools, nativeProtocol);
                String two = ToolPromptRenderer.renderToolPrompt(target, second, enabled, reordered, nativeProtocol);
                assertEquals(one, two);
                assertTrue(one.contains("<LEOF \"完成了任务重写\">"));
            }
        }
        assertEquals("z", first.get(0).getId());
        assertEquals("b", first.get(1).getTools()[0]);
    }

    private static McpToolConfig group(String id, String... tools) {
        return new McpToolConfig(id, "Group " + id, "", true, tools);
    }

    private static ToolInfo tool(String name, boolean reverse) {
        return (ToolInfo) Proxy.newProxyInstance(ToolInfo.class.getClassLoader(), new Class<?>[]{ToolInfo.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName": return name;
                        case "getDescription": return "Tool " + name;
                        case "getCategory": return ToolCategory.READ;
                        case "needsConfirmation": return false;
                        case "getParameters":
                            return reverse ? new JSONObject().put("z", 2).put("a", 1)
                                    : new JSONObject().put("a", 1).put("z", 2);
                        default: return null;
                    }
                });
    }
}
