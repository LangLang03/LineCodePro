package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;
import static org.junit.Assert.*;

public class ToolWireFormatTest {

    private ChatRequest request() {
        Message assistant = Message.builder().id("a").conversationId("c").seq(2)
                .role(Role.ASSISTANT)
                .addBlock(ContentBlock.text("let me read"))
                .toolCalls(Collections.singletonList(
                        new ToolCallRecord("call_1", "read_file", "{\"path\":\"a.txt\"}")))
                .build();
        Message tool = Message.builder().id("t").conversationId("c").seq(3)
                .role(Role.TOOL).tool("call_1", "read_file")
                .addBlock(ContentBlock.text("file body"))
                .build();
        return ChatRequest.builder()
                .model("m")
                .messages(Arrays.asList(user(), assistant, tool))
                .tools(Collections.singletonList(new ToolSchema("read_file", "Read a file",
                        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}")))
                .build();
    }

    private Message user() {
        return Message.builder().id("u").conversationId("c").seq(1).role(Role.USER)
                .addBlock(ContentBlock.text("read a.txt")).build();
    }

    @Test public void openAiSendsToolsAndReplaysCallsAndResults() throws Exception {
        JSONObject body = new JSONObject(OpenAiChatCodec.requestBody(request()));
        assertEquals("function", body.getJSONArray("tools").getJSONObject(0).getString("type"));
        assertEquals("read_file", body.getJSONArray("tools").getJSONObject(0)
                .getJSONObject("function").getString("name"));
        JSONArray messages = body.getJSONArray("messages");
        assertEquals("assistant", messages.getJSONObject(1).getString("role"));
        assertEquals("call_1", messages.getJSONObject(1).getJSONArray("tool_calls")
                .getJSONObject(0).getString("id"));
        assertEquals("tool", messages.getJSONObject(2).getString("role"));
        assertEquals("call_1", messages.getJSONObject(2).getString("tool_call_id"));
        assertEquals("file body", messages.getJSONObject(2).getString("content"));
    }

    @Test public void anthropicUsesInputSchemaAndContentBlocks() throws Exception {
        JSONObject body = new JSONObject(AnthropicCodec.requestBody(request()));
        assertEquals("read_file", body.getJSONArray("tools").getJSONObject(0).getString("name"));
        assertNotNull(body.getJSONArray("tools").getJSONObject(0).getJSONObject("input_schema"));
        JSONArray messages = body.getJSONArray("messages");
        JSONArray assistant = messages.getJSONObject(1).getJSONArray("content");
        assertEquals("tool_use", assistant.getJSONObject(1).getString("type"));
        assertEquals("call_1", assistant.getJSONObject(1).getString("id"));
        JSONArray result = messages.getJSONObject(2).getJSONArray("content");
        assertEquals("tool_result", result.getJSONObject(0).getString("type"));
        assertEquals("call_1", result.getJSONObject(0).getString("tool_use_id"));
    }

    @Test public void codexSendsFunctionCallsAndOutputs() throws Exception {
        JSONObject body = new JSONObject(CodexCodec.requestBody(request(), "https://api.openai.com/v1"));
        assertEquals("read_file", body.getJSONArray("tools").getJSONObject(0).getString("name"));
        JSONArray input = body.getJSONArray("input");
        boolean call = false;
        boolean output = false;
        for (int i = 0; i < input.length(); i++) {
            String type = input.getJSONObject(i).optString("type");
            if ("function_call".equals(type)) {
                call = true;
                assertEquals("call_1", input.getJSONObject(i).getString("call_id"));
            }
            if ("function_call_output".equals(type)) {
                output = true;
                assertEquals("file body", input.getJSONObject(i).getString("output"));
            }
        }
        assertTrue(call);
        assertTrue(output);
    }
}
