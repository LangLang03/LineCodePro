package cn.lineai.engine.ai;

import java.util.List;

import cn.lineai.core.model.McpToolInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class McpClientTest {

    @Test public void parsesToolsFromJsonRpcResponse() {
        List<McpToolInfo> tools = McpClient.parseTools(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                        + "{\"name\":\"search\",\"description\":\"Search the web\","
                        + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"
                        + "{\"description\":\"missing name\"}]}}");
        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).name);
        assertTrue(tools.get(0).enabled);
        assertEquals("Search the web", tools.get(0).description);
        assertTrue(tools.get(0).inputSchemaJson.contains("\"object\""));
    }

    @Test public void parsesToolsFromEventStream() {
        String body = "event: message\n"
                + "data: {\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"ping\"}]}}\n\n";
        assertEquals("ping", McpClient.parseTools(body).get(0).name);
    }

    @Test public void toolResultTextJoinsContentBlocks() {
        String text = McpClient.toolResultText(
                "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"one\"},"
                        + "{\"type\":\"text\",\"text\":\"two\"}]}}");
        assertEquals("one\ntwo", text);
    }

    @Test public void normalizesUrlsAndRejectsRemoteCleartext() {
        assertEquals("https://example.com/mcp",
                McpClient.normalizeUrl("https://example.com/mcp/"));
        assertEquals("http://127.0.0.1:3000/mcp",
                McpClient.normalizeUrl("http://127.0.0.1:3000/mcp"));
        try {
            McpClient.normalizeUrl("http://example.com/mcp");
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            // remote cleartext is not allowed
        }
    }
}
