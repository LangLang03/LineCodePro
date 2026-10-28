package cn.lineai.engine.protocol;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImageWireFormatTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ChatRequest request() throws Exception {
        File image = temporary.newFile("view.png");
        Files.write(image.toPath(), "fakepng".getBytes(StandardCharsets.UTF_8));
        Message user = Message.builder().id("u").conversationId("c").seq(1).role(Role.USER)
                .addBlock(ContentBlock.text("what is wrong here?"))
                .addBlock(ContentBlock.image(image.getAbsolutePath()))
                .build();
        return ChatRequest.builder().model("m")
                .messages(Collections.singletonList(user)).build();
    }

    @Test public void openAiSendsImageUrlParts() throws Exception {
        JSONObject body = new JSONObject(OpenAiChatCodec.requestBody(request()));
        JSONArray content = body.getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content");
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertTrue(content.getJSONObject(1).getJSONObject("image_url").getString("url")
                .startsWith("data:image/png;base64,"));
    }

    @Test public void anthropicSendsBase64ImageSource() throws Exception {
        JSONObject body = new JSONObject(AnthropicCodec.requestBody(request()));
        JSONArray content = body.getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content");
        assertEquals("image", content.getJSONObject(1).getString("type"));
        JSONObject source = content.getJSONObject(1).getJSONObject("source");
        assertEquals("base64", source.getString("type"));
        assertEquals("image/png", source.getString("media_type"));
        assertEquals("ZmFrZXBuZw==", source.getString("data"));
    }

    @Test public void codexSendsInputImage() throws Exception {
        JSONObject body = new JSONObject(CodexCodec.requestBody(request(), "https://api.openai.com/v1"));
        JSONArray content = body.getJSONArray("input").getJSONObject(0).getJSONArray("content");
        assertEquals("input_text", content.getJSONObject(0).getString("type"));
        assertEquals("input_image", content.getJSONObject(1).getString("type"));
        assertTrue(content.getJSONObject(1).getString("image_url")
                .startsWith("data:image/png;base64,"));
    }
}
