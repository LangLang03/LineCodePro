package cn.lineai.engine.prompt;

import android.app.Application;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PromptCatalogTest {

    @Test public void everyTemplateAssetExistsAndIsNonEmpty() {
        AssetPromptCatalog catalog = new AssetPromptCatalog(
                RuntimeEnvironment.getApplication().getAssets());
        for (PromptId prompt : PromptId.values()) {
            assertFalse("empty prompt " + prompt.id, catalog.text(prompt).trim().isEmpty());
        }
    }

    @Test public void registryMatchesThePackagedAssets() throws IOException {
        String[] files = RuntimeEnvironment.getApplication().getAssets().list("prompts");
        assertNotNull(files);
        java.util.Set<String> actual = new java.util.HashSet<>(java.util.Arrays.asList(files));
        actual.remove("manifest.json");
        for (PromptId prompt : PromptId.values()) {
            assertTrue("missing asset " + prompt.file, actual.contains(prompt.file));
            actual.remove(prompt.file);
        }
        assertTrue("unused assets " + actual, actual.isEmpty());
    }

    @Test public void rendererRejectsMissingAndUnknownPlaceholders() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("MODEL_ID", "id");
        values.put("MODEL_NAME", "name");
        values.put("MODEL_PROVIDER", "provider");
        try {
            PromptRenderer.render(PromptId.MODEL_IDENTITY, "{{MODEL_ID}} {{MODEL_PROTOCOL}}", values);
            fail("expected missing argument");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("MODEL_PROTOCOL"));
        }
        values.put("MODEL_PROTOCOL", "OPENAI");
        values.put("EXTRA", "x");
        try {
            PromptRenderer.render(PromptId.MODEL_IDENTITY, "{{EXTRA}}", values);
            fail("expected unknown placeholder");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("EXTRA"));
        }
    }

    @Test public void rendererAllowsBraceContentInValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("MODEL_ID", "{{id}}");
        values.put("MODEL_NAME", "name");
        values.put("MODEL_PROVIDER", "provider");
        values.put("MODEL_PROTOCOL", "OPENAI");
        assertEquals("{{id}}", PromptRenderer.render(
                PromptId.MODEL_IDENTITY, "{{MODEL_ID}}", values));
    }

    @Test public void rendererRejectsMalformedTemplatePlaceholders() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("MODEL_ID", "id");
        values.put("MODEL_NAME", "name");
        values.put("MODEL_PROVIDER", "provider");
        values.put("MODEL_PROTOCOL", "OPENAI");
        try {
            PromptRenderer.render(PromptId.MODEL_IDENTITY, "{{MODEL_ID}", values);
            fail("expected malformed placeholder");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Malformed"));
        }
    }

    @Test public void storeRejectsUnknownPlaceholdersOnSave() {
        Map<PromptId, String> texts = new EnumMap<>(PromptId.class);
        texts.put(PromptId.TONE_CODING, "default {{UNKNOWN}}");
        PromptStore store = new PromptStore(PromptCatalog.of(texts), PromptOverrides.none());
        try {
            store.save(PromptId.TONE_CODING, "custom {{UNKNOWN}}");
            fail("expected unknown placeholder");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("UNKNOWN"));
        }
    }

    @Test public void storeOverlaySupportsSaveResetAndCustomisationFlag() {
        Map<PromptId, String> texts = new EnumMap<>(PromptId.class);
        texts.put(PromptId.TONE_CODING, "default");
        Memory overrides = new Memory();
        PromptStore store = new PromptStore(PromptCatalog.of(texts), overrides);
        assertFalse(store.customized(PromptId.TONE_CODING));
        store.save(PromptId.TONE_CODING, "custom");
        assertEquals("custom", store.current(PromptId.TONE_CODING));
        assertTrue(store.customized(PromptId.TONE_CODING));
        assertEquals("custom", overrides.values.get("prompt.toneCoding"));
        store.reset(PromptId.TONE_CODING);
        assertEquals("default", store.current(PromptId.TONE_CODING));
        assertFalse(store.customized(PromptId.TONE_CODING));
    }

    @Test public void assemblerRendersToneIdentityAndMode() {
        AssetPromptCatalog catalog = new AssetPromptCatalog(
                RuntimeEnvironment.getApplication().getAssets());
        SystemPromptAssembler assembler = new SystemPromptAssembler(
                new PromptStore(catalog, PromptOverrides.none()));
        String coding = assembler.assemble("deepseek-chat", "DeepSeek Chat", "custom",
                "OPENAI_COMPATIBLE", "coding");
        assertTrue(coding.contains("Communication Tone: Coding Mode"));
        assertTrue(coding.contains("deepseek-chat"));
        assertTrue(coding.contains("Current mode: Agent"));
        assertFalse(coding.contains("{{"));
        String chat = assembler.assemble("m", "M", "p", "ANTHROPIC_MESSAGES", "chat");
        assertTrue(chat.contains("Communication Tone: Chat Mode"));
    }

    static final class Memory implements PromptOverrides {
        final Map<String, String> values = new LinkedHashMap<>();
        public String get(String id, String fallback) { return values.getOrDefault(id, fallback); }
        public void put(String id, String value) { values.put(id, value); }
        public void remove(String id) { values.remove(id); }
    }
}
