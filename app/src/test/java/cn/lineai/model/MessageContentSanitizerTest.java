package cn.lineai.model;

import cn.lineai.tool.ToolNames;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 内容净化器位于每次 render 的热路径上（上下文 token 估算会逐条调用），
 * 因此“无需改写”时必须原样返回，既不拷贝也不解析 JSON。
 */
public final class MessageContentSanitizerTest {
    @Test
    public void plainContentIsReturnedWithoutCopying() {
        ChatMessage user = new ChatMessage("u", ChatMessage.Role.USER, "long plain answer\nwith lines", false);

        assertSame(user.getContent(), MessageContentSanitizer.forModel(user));
    }

    @Test
    public void inlineDataImagesAreStillReplaced() {
        String content = "before ![shot](data:image/png;base64,QUJD) after";
        ChatMessage user = new ChatMessage("u", ChatMessage.Role.USER, content, false);

        String sanitized = MessageContentSanitizer.forModel(user);

        assertFalse(sanitized.contains("base64"));
        assertTrue(sanitized.contains("linecode-inline-image"));
        assertTrue(sanitized.startsWith("before "));
        assertTrue(sanitized.endsWith(" after"));
    }

    @Test
    public void ordinaryToolOutputSkipsJsonParsing() {
        String output = "src/main/java/App.java\n12 files changed\n";
        ChatMessage tool = ChatMessage.toolResult("t", output, "call-1", "shell_execute", false);

        assertSame(output, MessageContentSanitizer.forModel(tool));
    }

    @Test
    public void jsonWithoutLineCodeMarkersIsPassedThrough() {
        String output = "{\"status\":\"ok\",\"items\":[1,2,3]}";
        ChatMessage tool = ChatMessage.toolResult("t", output, "call-1", "web_search", false);

        assertSame(output, MessageContentSanitizer.toolContentForModel(tool));
    }

    @Test
    public void agentProgressStillCollapsesToModelContent() {
        String output = "{\"linecode_agent_progress\":true,\"model_content\":\"step summary\",\"output\":\"raw\"}";
        ChatMessage tool = ChatMessage.toolResult("t", output, "call-1", "agent", false);

        assertEquals("step summary", MessageContentSanitizer.toolContentForModel(tool));
    }

    @Test
    public void imageGenerationFallsBackForNonJsonOutput() {
        ChatMessage tool = ChatMessage.toolResult("t", "not json at all", "call-1",
                ToolNames.IMAGE_GENERATION, false);

        assertEquals("Image generated and displayed in conversation.",
                MessageContentSanitizer.toolContentForModel(tool));
    }

    @Test
    public void erroredImageGenerationKeepsRawContent() {
        String output = "provider failed";
        ChatMessage tool = ChatMessage.toolResult("t", output, "call-1", ToolNames.IMAGE_GENERATION, true);

        assertSame(output, MessageContentSanitizer.toolContentForModel(tool));
    }
}
