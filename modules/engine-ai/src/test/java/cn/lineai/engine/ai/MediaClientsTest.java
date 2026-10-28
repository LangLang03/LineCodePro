package cn.lineai.engine.ai;

import cn.lineai.core.model.ModelConfig;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MediaClientsTest {

    private ModelConfig model(ModelConfig.Protocol protocol) {
        return ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "https://example.com/v1")
                .protocol(protocol).build();
    }

    @Test public void imageGenerationReadsBase64OrUrl() {
        assertArrayEquals("abc".getBytes(),
                ImageGenerationClient.extractBase64("{\"data\":[{\"b64_json\":\"YWJj\"}]}"));
        assertNull(ImageGenerationClient.extractBase64("{\"data\":[]}"));
        assertEquals("https://cdn.example.com/a.png",
                ImageGenerationClient.extractUrl("{\"data\":[{\"url\":\"https://cdn.example.com/a.png\"}]}"));
        assertEquals("", ImageGenerationClient.extractUrl("{}"));
    }

    @Test public void visionParsesEachProtocol() throws Exception {
        assertEquals("A cat", VisionClient.parse(model(ModelConfig.Protocol.OPENAI_COMPATIBLE),
                "{\"choices\":[{\"message\":{\"content\":\"A cat\"}}]}"));
        assertEquals("A dog", VisionClient.parse(model(ModelConfig.Protocol.ANTHROPIC_MESSAGES),
                "{\"content\":[{\"type\":\"text\",\"text\":\"A dog\"}]}"));
        assertEquals("A bird", VisionClient.parse(model(ModelConfig.Protocol.CODEX_RESPONSES),
                "{\"output_text\":\"A bird\"}"));
        assertEquals("Nested", VisionClient.parse(model(ModelConfig.Protocol.CODEX_RESPONSES),
                "{\"output\":[{\"content\":[{\"text\":\"Nested\"}]}]}"));
    }
}
