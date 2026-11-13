package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Hidden user messages injected after tool results (background-task notices) must merge into the
 * previous user turn instead of producing consecutive user messages.
 */
public class AnthropicNoticeMergeTest {

    private static Message message(String id, long seq, Role role, String text) {
        return Message.builder().id(id).conversationId("c").seq(seq).role(role)
                .addBlock(ContentBlock.text(text)).build();
    }

    @Test public void noticeAfterToolResultsMergesIntoTheSameUserTurn() throws Exception {
        Message assistant = Message.builder().id("a").conversationId("c").seq(2)
                .role(Role.ASSISTANT)
                .toolCalls(Arrays.asList(new ToolCallRecord("call_1", "shell", "{}")))
                .build();
        Message tool = Message.builder().id("t").conversationId("c").seq(3)
                .role(Role.TOOL).tool("call_1", "shell")
                .addBlock(ContentBlock.text("started bg-1")).build();
        Message notice = message("n", 4, Role.USER,
                "(background task notice - not from the user)\n- bg-1 finished");
        ChatRequest request = ChatRequest.builder().model("m")
                .messages(Arrays.asList(message("u", 1, Role.USER, "start the server"),
                        assistant, tool, notice))
                .build();

        JSONArray messages = new JSONObject(AnthropicCodec.requestBody(request))
                .getJSONArray("messages");
        assertEquals(3, messages.length());
        JSONObject merged = messages.getJSONObject(2);
        assertEquals("user", merged.optString("role"));
        JSONArray content = merged.getJSONArray("content");
        assertEquals("tool_result", content.getJSONObject(0).getString("type"));
        assertEquals("text", content.getJSONObject(1).getString("type"));
        assertTrue(content.getJSONObject(1).getString("text").contains("bg-1 finished"));
    }

    @Test public void noticeAfterAPlainUserMessageAlsoMerges() throws Exception {
        Message user = message("u", 1, Role.USER, "continue");
        Message notice = message("n", 2, Role.USER, "(background task notice)");
        ChatRequest request = ChatRequest.builder().model("m")
                .messages(Arrays.asList(user, notice)).build();
        JSONArray messages = new JSONObject(AnthropicCodec.requestBody(request))
                .getJSONArray("messages");
        assertEquals(1, messages.length());
        assertEquals("continue", messages.getJSONObject(0).getJSONArray("content")
                .getJSONObject(0).getString("text"));
        assertEquals("(background task notice)", messages.getJSONObject(0)
                .getJSONArray("content").getJSONObject(1).getString("text"));
    }

    @Test public void assistantThenUserStaysSeparate() throws Exception {
        ChatRequest request = ChatRequest.builder().model("m")
                .messages(Arrays.asList(
                        message("a", 1, Role.ASSISTANT, "answer"),
                        message("u", 2, Role.USER, "next question")))
                .build();
        JSONArray messages = new JSONObject(AnthropicCodec.requestBody(request))
                .getJSONArray("messages");
        assertEquals(2, messages.length());
        assertTrue(messages.getJSONObject(1).optString("content").contains("next question"));
    }
}
