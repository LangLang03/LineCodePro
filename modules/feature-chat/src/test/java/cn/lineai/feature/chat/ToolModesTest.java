package cn.lineai.feature.chat;

import org.json.JSONObject;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolKind;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ToolModesTest {

    private Tool tool(String name, boolean mutating) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return name; }
            public String parametersJson() { return "{}"; }
            public boolean mutating() { return mutating; }
            public ToolResult execute(ToolContext context, JSONObject arguments) {
                return ToolResult.success("", "");
            }
        };
    }

    @Test public void normalizesUnknownModesToConfirm() {
        assertEquals(ToolModes.CONFIRM, ToolModes.normalize(null));
        assertEquals(ToolModes.CONFIRM, ToolModes.normalize("nonsense"));
        assertEquals(ToolModes.AUTO, ToolModes.normalize("auto"));
    }

    @Test public void readonlyDropsMutatingTools() {
        ToolRegistry source = new ToolRegistry()
                .register(tool("read", false)).register(tool("write", true));
        ToolRegistry filtered = ToolModes.filter(ToolModes.READONLY, source);
        assertTrue(filtered.find("read") != null);
        assertNull(filtered.find("write"));
        assertEquals(1, filtered.all().size());
    }

    private Tool phone(String name) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return name; }
            public String parametersJson() { return "{}"; }
            public boolean mutating() { return true; }
            public ToolKind kind() { return ToolKind.PHONE; }
            public ToolResult execute(ToolContext context, JSONObject arguments) {
                return ToolResult.success("", "");
            }
        };
    }

    @Test public void chatAndPlanDropMutatingAndPhoneTools() {
        ToolRegistry source = new ToolRegistry().register(tool("read", false))
                .register(tool("write", true)).register(phone("phone_click"));
        ToolRegistry chat = ToolModes.filterChatMode("chat", source);
        assertTrue(chat.find("read") != null);
        assertNull(chat.find("write"));
        assertNull(chat.find("phone_click"));
        assertEquals(1, chat.all().size());
        assertEquals(1, ToolModes.filterChatMode("plan", source).all().size());
    }

    @Test public void controlKeepsOnlyPhoneTools() {
        ToolRegistry source = new ToolRegistry().register(tool("read", false))
                .register(phone("phone_click"));
        ToolRegistry control = ToolModes.filterChatMode("control", source);
        assertTrue(control.find("phone_click") != null);
        assertNull(control.find("read"));
        assertEquals(1, control.all().size());
    }

    @Test public void agentKeepsEverything() {
        ToolRegistry source = new ToolRegistry().register(tool("write", true));
        assertSame(source, ToolModes.filterChatMode("agent", source));
    }

    @Test public void otherModesKeepTheSameRegistry() {
        ToolRegistry source = new ToolRegistry().register(tool("write", true));
        assertSame(source, ToolModes.filter(ToolModes.CONFIRM, source));
        assertSame(source, ToolModes.filter(ToolModes.AUTO, source));
    }
}
