package cn.lineai.data.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class LineCodeImportMapperTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void importsInlineAsyncStorageBackupShape() throws Exception {
        String entries = "["
                + entry("@lineai_models", "[{"
                + "\"id\":\"m1\","
                + "\"name\":\"Claude\","
                + "\"provider\":\"anthropic\","
                + "\"providerLabel\":\"Claude\","
                + "\"baseUrl\":\"https://api.anthropic.com\","
                + "\"apiKey\":\"sk-test\","
                + "\"modelId\":\"claude-sonnet-4\""
                + "}]") + ","
                + entry("@lineai_selected_model", "m1") + ","
                + entry("@lineai_current_conversation", "c1") + ","
                + entry("@lineai_conversation_list", "[{"
                + "\"id\":\"c1\","
                + "\"title\":\"迁移测试\","
                + "\"createdAt\":100,"
                + "\"updatedAt\":200"
                + "}]") + ","
                + entry("@lineai_conv_c1", "{"
                + "\"id\":\"c1\","
                + "\"title\":\"迁移测试\","
                + "\"createdAt\":100,"
                + "\"updatedAt\":200,"
                + "\"messages\":[{"
                + "\"id\":\"u1\","
                + "\"role\":\"user\","
                + "\"content\":\"你好\","
                + "\"timestamp\":101"
                + "},{"
                + "\"id\":\"a1\","
                + "\"role\":\"assistant\","
                + "\"content\":\"收到\","
                + "\"reasoningContent\":\"先理解需求\","
                + "\"timestamp\":102"
                + "}]"
                + "}") + ","
                + entry("@lineai_reasoning_effort", "high")
                + "]";

        ImportedLineCodeData data = new LineCodeImportMapper().fromAsyncStorageJson(entries, null);

        assertEquals("m1", data.getSelectedModelId());
        assertEquals("c1", data.getCurrentConversationId());
        assertEquals("high", data.getSettings().get("@lineai_reasoning_effort"));

        ModelConfig model = data.getModels().get(0);
        assertEquals(ModelProtocolType.ANTHROPIC_MESSAGES, model.getProtocolType());
        assertEquals("claude-sonnet-4", model.getModelId());

        ConversationRecord conversation = data.getConversations().get(0);
        assertEquals("迁移测试", conversation.getTitle());
        assertEquals(2, conversation.getMessages().size());
        assertEquals(ChatMessage.Role.USER, conversation.getMessages().get(0).getRole());
        assertEquals("先理解需求", conversation.getMessages().get(1).getReasoningContent());
        assertFalse(conversation.getMessages().get(1).isStreaming());
    }

    @Test
    public void importsFileConversationManifest() throws Exception {
        File payload = temporaryFolder.newFolder("payload");
        File conversations = new File(payload, "conversations");
        assertFalse(conversations.exists());
        conversations.mkdirs();
        write(new File(conversations, "c2.json"), "{"
                + "\"id\":\"c2\","
                + "\"title\":\"文件聊天\","
                + "\"createdAt\":300,"
                + "\"updatedAt\":400,"
                + "\"messages\":[{\"id\":\"u2\",\"role\":\"user\",\"content\":\"from file\",\"timestamp\":301}]"
                + "}");

        String entries = "["
                + entry("@lineai_conversation_list", "[{\"id\":\"c2\",\"title\":\"文件聊天\",\"createdAt\":300,\"updatedAt\":400}]")
                + ","
                + entry("@lineai_conv_c2", "{\"storage\":\"file\",\"schemaVersion\":2,\"id\":\"c2\",\"fileName\":\"c2.json\",\"size\":10,\"updatedAt\":400,\"messageCount\":1}")
                + "]";

        ImportedLineCodeData data = new LineCodeImportMapper().fromAsyncStorageJson(entries, payload);

        assertEquals(1, data.getConversations().size());
        assertEquals("文件聊天", data.getConversations().get(0).getTitle());
        assertEquals("from file", data.getConversations().get(0).getMessages().get(0).getContent());
    }

    private static String entry(String key, String value) {
        return "{\"key\":\"" + escape(key) + "\",\"value\":\"" + escape(value) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }
}
