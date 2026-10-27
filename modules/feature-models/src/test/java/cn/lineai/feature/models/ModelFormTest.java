package cn.lineai.feature.models;

import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelFormTest {

    @Test public void parsesContextSizesWithUnits() {
        assertEquals(128000, ContextSizes.parse("128k"));
        assertEquals(1000000, ContextSizes.parse("1M"));
        assertEquals(1500, ContextSizes.parse("1.5k"));
        assertEquals(0, ContextSizes.parse(""));
        assertEquals(0, ContextSizes.parse("abc"));
        assertEquals("128K", ContextSizes.format(128000));
        assertEquals("1.5K", ContextSizes.format(1500));
    }

    @Test public void requiresModelIdKeyAndValidToolLimit() {
        ModelForm form = new ModelForm();
        assertEquals(ModelForm.Error.MISSING_ID, form.validateForSave());
        form.modelId = "m";
        assertEquals(ModelForm.Error.MISSING_KEY, form.validateForSave());
        form.apiKey = "k";
        form.toolCallLimit = "abc";
        assertEquals(ModelForm.Error.TOOL_RANGE, form.validateForSave());
        form.toolCallLimit = "-1";
        assertEquals(ModelForm.Error.NONE, form.validateForSave());
        form.toolCallLimit = "-2";
        assertEquals(ModelForm.Error.TOOL_RANGE, form.validateForSave());
    }

    @Test public void compressionRequiresModelIdWhenNotAutomatic() {
        ModelForm form = validForm();
        form.compressionEnabled = true;
        form.compressionAuto = false;
        assertEquals(ModelForm.Error.COMPRESSION_ID, form.validateForSave());
        form.compressionModelId = "compress";
        assertEquals(ModelForm.Error.NONE, form.validateForSave());
        form.compressionAuto = true;
        form.compressionModelId = "";
        assertEquals(ModelForm.Error.NONE, form.validateForSave());
    }

    @Test public void buildsModelWithEveryField() {
        ModelForm form = validForm();
        form.name = "DeepSeek";
        form.providerId = "deepseek";
        form.providerLabel = "DeepSeek";
        form.protocol = ModelConfig.Protocol.OPENAI_COMPATIBLE;
        form.baseUrl = "https://api.deepseek.com/v1";
        form.contextSize = "128k";
        form.toolCallLimit = "250";
        ModelConfig model = form.build();
        assertEquals("DeepSeek", model.displayName());
        assertEquals("deepseek", model.providerId);
        assertEquals("DeepSeek", model.providerLabel);
        assertEquals(128000, model.contextSize);
        assertEquals(250, model.toolCallLimit);
    }

    @Test public void presetPrefillsEndpointAndProtocol() {
        ModelPreset preset = ModelPreset.find("claude");
        ModelForm form = ModelForm.forPreset(preset);
        assertEquals(ModelConfig.Protocol.ANTHROPIC_MESSAGES, form.protocol);
        assertEquals("https://api.anthropic.com", form.baseUrl);
        assertEquals("claude", form.providerId);
    }

    @Test public void editingRoundTripsThroughForm() {
        ModelForm form = validForm();
        form.toolCallLimit = "300";
        form.compressionEnabled = true;
        form.compressionAuto = false;
        form.compressionModelId = "c1";
        ModelConfig original = form.build();
        ModelForm copy = ModelForm.from(original);
        assertEquals("300", copy.toolCallLimit);
        assertTrue(copy.compressionEnabled);
        assertFalse(copy.compressionAuto);
        assertEquals("c1", copy.compressionModelId);
        assertEquals(ModelForm.Error.NONE, copy.validateForSave());
    }

    private ModelForm validForm() {
        ModelForm form = new ModelForm();
        form.modelId = "model";
        form.apiKey = "key";
        form.baseUrl = "https://example.com/v1";
        return form;
    }
}
