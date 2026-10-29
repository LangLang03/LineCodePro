package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.SettingsStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContextCompactorTest {

    private ModelConfig model(boolean enabled) {
        return ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "https://example.com/v1")
                .context(1000, 2048)
                .compression(enabled, true, "")
                .build();
    }

    private final Map<String, String> values = new HashMap<>();

    private SettingsStore settings() {
        return new SettingsStore() {
            public String get(String key, String fallback) {
                return values.containsKey(key) ? values.get(key) : fallback;
            }
            public int getInt(String key, int fallback) { return fallback; }
            public void put(String key, String value) { values.put(key, value); }
            public void remove(String key) { values.remove(key); }
            public void invalidate() { }
            public Map<String, String> all() { return values; }
        };
    }

    private List<Message> messages(int count) {
        return messages(count, 200);
    }

    private List<Message> messages(int count, int chars) {
        List<Message> list = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < chars; i++) text.append('x');
        for (int i = 0; i < count; i++) {
            list.add(Message.builder().id("m" + i).conversationId("c").seq(i + 1)
                    .role(i % 2 == 0 ? Role.USER : Role.ASSISTANT)
                    .addBlock(ContentBlock.text(i + ":" + text))
                    .build());
        }
        return list;
    }

    @Test public void compressesOldMessagesAndKeepsTail() {
        final List<String> prompts = new ArrayList<>();
        ContextCompactor.Result result = ContextCompactor.prepare(model(true), null, settings(),
                "c", messages(20), true, (model, prompt) -> {
                    prompts.add(prompt);
                    return "SUMMARY";
                });
        assertEquals("SUMMARY", result.summary);
        assertEquals(16, result.history.size());
        assertEquals("SUMMARY", values.get(ContextCompactor.key("c")));
        assertTrue(prompts.get(0).contains("[user] 0:"));
    }

    @Test public void softCompactionUsesHalfContextThreshold() {
        ContextCompactor.Result soft = ContextCompactor.prepare(model(true), null, settings(),
                "c", messages(17, 120), true, (model, prompt) -> "SUMMARY");
        assertEquals("SUMMARY", soft.summary);
        values.clear();
        ContextCompactor.Result hard = ContextCompactor.prepare(model(true), null, settings(),
                "c", messages(17, 120), false, (model, prompt) -> "SUMMARY");
        assertEquals("", hard.summary);
        assertEquals(17, hard.history.size());
    }

    @Test public void disabledCompressionKeepsEverything() {
        ContextCompactor.Result result = ContextCompactor.prepare(model(false), null, settings(),
                "c", messages(20), true, (model, prompt) -> "SUMMARY");
        assertEquals("", result.summary);
        assertEquals(20, result.history.size());
        assertTrue(values.isEmpty());
    }

    @Test public void compressorFailureFallsBackToFullHistory() {
        ContextCompactor.Result result = ContextCompactor.prepare(model(true), null, settings(),
                "c", messages(20), true, (model, prompt) -> {
                    throw new IllegalStateException("offline");
                });
        assertEquals("", result.summary);
        assertEquals(20, result.history.size());
    }

    @Test public void shortHistoryIgnoresStoredSummary() {
        values.put(ContextCompactor.key("c"), "OLD");
        ContextCompactor.Result result = ContextCompactor.prepare(model(true), null, settings(),
                "c", messages(3), true, (model, prompt) -> "NEW");
        assertEquals(3, result.history.size());
        assertEquals("", result.summary);
    }

    @Test public void manualCompactionIgnoresAutomaticThresholds() throws Exception {
        assertTrue(ContextCompactor.compactNow(model(false), null, settings(), "c", messages(20),
                (compressor, prompt) -> "MANUAL"));
        assertEquals("MANUAL", values.get(ContextCompactor.key("c")));
    }

    @Test public void manualCompactionSkipsShortConversations() throws Exception {
        assertFalse(ContextCompactor.compactNow(model(false), null, settings(), "c", messages(5),
                (compressor, prompt) -> "NEVER"));
        assertTrue(values.isEmpty());
    }
}
