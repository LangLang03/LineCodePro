package cn.lineai.ai.protocol;

import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.tool.ToolCall;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class AnthropicMessagesProtocolTest {
    @Test
    public void serializesVisionRawInputAsImageBlocks() throws Exception {
        AnthropicMessagesProtocol protocol = new AnthropicMessagesProtocol();
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new UserModelMessage("fallback", ImageInputPayload.rawInputJson("识别图片", "image/webp", "abc123")));

        JSONArray json = protocol.messagesJsonForTest(messages);

        JSONObject user = json.getJSONObject(0);
        Assert.assertEquals("user", user.getString("role"));
        JSONArray content = user.getJSONArray("content");
        Assert.assertEquals("text", content.getJSONObject(0).getString("type"));
        Assert.assertEquals("识别图片", content.getJSONObject(0).getString("text"));
        JSONObject image = content.getJSONObject(1);
        Assert.assertEquals("image", image.getString("type"));
        JSONObject source = image.getJSONObject("source");
        Assert.assertEquals("base64", source.getString("type"));
        Assert.assertEquals("image/webp", source.getString("media_type"));
        Assert.assertEquals("abc123", source.getString("data"));
    }

    @Test
    public void serializesToolResultsAsUserContentBlocks() throws Exception {
        AnthropicMessagesProtocol protocol = new AnthropicMessagesProtocol();
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new AssistantModelMessage("", "", Collections.singletonList(
                new ToolCall("toolu_1", "file_read", "{\"file_path\":\"app/build.gradle.kts\"}")
        )));
        messages.add(new ToolModelMessage("文件内容", "toolu_1", "file_read", false));

        JSONArray json = protocol.messagesJsonForTest(messages);

        JSONObject assistant = json.getJSONObject(0);
        Assert.assertEquals("assistant", assistant.getString("role"));
        JSONObject toolUse = assistant.getJSONArray("content").getJSONObject(0);
        Assert.assertEquals("tool_use", toolUse.getString("type"));
        Assert.assertEquals("toolu_1", toolUse.getString("id"));
        Assert.assertEquals("app/build.gradle.kts", toolUse.getJSONObject("input").getString("file_path"));

        JSONObject user = json.getJSONObject(1);
        Assert.assertEquals("user", user.getString("role"));
        JSONObject toolResult = user.getJSONArray("content").getJSONObject(0);
        Assert.assertEquals("tool_result", toolResult.getString("type"));
        Assert.assertEquals("toolu_1", toolResult.getString("tool_use_id"));
        Assert.assertEquals("文件内容", toolResult.getString("content"));
    }

    @Test
    public void coalescesConsecutiveToolResultsIntoOneUserMessage() throws Exception {
        AnthropicMessagesProtocol protocol = new AnthropicMessagesProtocol();
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new UserModelMessage("继续"));
        messages.add(new ToolModelMessage("A", "toolu_a", "file_read", false));
        messages.add(new ToolModelMessage("B", "toolu_b", "glob", true));

        JSONArray json = protocol.messagesJsonForTest(messages);

        Assert.assertEquals(2, json.length());
        JSONObject user = json.getJSONObject(1);
        Assert.assertEquals("user", user.getString("role"));
        JSONArray content = user.getJSONArray("content");
        Assert.assertEquals(2, content.length());
        Assert.assertEquals("toolu_a", content.getJSONObject(0).getString("tool_use_id"));
        Assert.assertTrue(content.getJSONObject(1).getBoolean("is_error"));
    }
}
