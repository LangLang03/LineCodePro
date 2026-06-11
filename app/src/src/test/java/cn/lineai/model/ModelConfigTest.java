package cn.lineai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class ModelConfigTest {
    @Test
    public void compressionModelConfigRoundTripsJson() throws Exception {
        ModelConfig model = new ModelConfig(
                "m1",
                "GPT",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://api.openai.com/v1",
                "sk-test",
                "gpt-5",
                200,
                true,
                false,
                "gpt-5-mini"
        );

        ModelConfig parsed = ModelConfig.fromJson(model.toJson());

        assertTrue(parsed.isCompressionModelEnabled());
        assertFalse(parsed.isCompressionModelAuto());
        assertEquals("gpt-5-mini", parsed.getCompressionModelId());
        assertEquals("gpt-5-mini", parsed.getEffectiveCompressionModelId());
    }

    @Test
    public void unsupportedProviderDisablesDedicatedCompression() throws Exception {
        ModelConfig model = new ModelConfig(
                "m1",
                "Claude",
                ModelProtocolType.ANTHROPIC_MESSAGES,
                "Anthropic",
                "https://api.anthropic.com",
                "sk-test",
                "claude-sonnet",
                200,
                true,
                false,
                "compact-model"
        );

        assertFalse(model.isCompressionModelEnabled());

        JSONObject legacy = new JSONObject()
                .put("id", "m2")
                .put("name", "Codex")
                .put("protocolType", "codex")
                .put("providerLabel", "Codex")
                .put("baseUrl", "https://api.openai.com/v1")
                .put("apiKey", "sk-test")
                .put("modelId", "codex")
                .put("compression_model_enabled", true)
                .put("compression_model_auto", true);

        ModelConfig parsed = ModelConfig.fromJson(legacy);
        assertTrue(parsed.isCompressionModelEnabled());
        assertTrue(parsed.isCompressionModelAuto());
        assertEquals("codex", parsed.getEffectiveCompressionModelId());
    }
}
