package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.MessageStore;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.SettingsStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The context window must follow the selected model instead of the legacy settings key. */
public class ChatControllerContextTest {

    private final Map<String, String> values = new HashMap<>();
    private final List<Message> loaded = new ArrayList<>();
    private ModelConfig selected;

    @Test public void maxTokensUsesTheSelectedModelContext() {
        selected = model(200000);
        values.put(ChatController.KEY_CONTEXT_SIZE, "64000");
        assertEquals(200000, controller().maxTokens());
    }

    @Test public void maxTokensFallsBackToSettingsWithoutAModel() {
        values.put(ChatController.KEY_CONTEXT_SIZE, "64000");
        assertEquals(64000, controller().maxTokens());
    }

    @Test public void maxTokensFallsBackWhenTheModelHasNoContext() {
        selected = model(ModelConfig.CONTEXT_SIZE_UNSET);
        values.put(ChatController.KEY_CONTEXT_SIZE, "96000");
        assertEquals(96000, controller().maxTokens());
    }

    @Test public void openingAConversationUsesTheModelContextForThePercent() {
        selected = model(1000);
        loaded.add(message("c1", 400));
        ChatController controller = controller();
        controller.open("c1");
        assertEquals(10, controller.state().contextPercent);
    }

    @Test public void openingAConversationFallsBackToSettingsContext() {
        values.put(ChatController.KEY_CONTEXT_SIZE, "2000");
        loaded.add(message("c1", 400));
        ChatController controller = controller();
        controller.open("c1");
        assertEquals(5, controller.state().contextPercent);
    }

    private ChatController controller() {
        return new ChatController(null, messageStore(), modelStore(), settings(), null,
                Scheduler.immediate(), Clock.fixed(1L), null, null, null, null);
    }

    private ModelConfig model(int context) {
        return ModelConfig.builder().identity("m", "Model")
                .endpoint("p", "model", "https://example.com/v1")
                .context(context, 4096)
                .build();
    }

    private Message message(String conversationId, int characters) {
        StringBuilder text = new StringBuilder(characters);
        for (int index = 0; index < characters; index++) text.append('a');
        return Message.builder().id("m1").conversationId(conversationId).seq(1).role(Role.USER)
                .blocks(Collections.singletonList(ContentBlock.text(text.toString())))
                .build();
    }

    private SettingsStore settings() {
        return new SettingsStore() {
            @Override public String get(String key, String fallback) {
                return values.containsKey(key) ? values.get(key) : fallback;
            }

            @Override public int getInt(String key, int fallback) {
                String value = values.get(key);
                return value == null ? fallback : Integer.parseInt(value);
            }

            @Override public void put(String key, String value) { values.put(key, value); }

            @Override public void remove(String key) { values.remove(key); }

            @Override public void invalidate() { }

            @Override public Map<String, String> all() { return values; }
        };
    }

    private ModelStore modelStore() {
        return new ModelStore() {
            @Override public List<ModelConfig> list() {
                return selected == null ? Collections.<ModelConfig>emptyList()
                        : Collections.singletonList(selected);
            }

            @Override public ModelConfig find(String id) { return selected; }

            @Override public ModelConfig selected() { return selected; }

            @Override public void save(ModelConfig model) { selected = model; }

            @Override public void select(String id) { }

            @Override public void delete(List<String> ids) { }

            @Override public void seedFromSettings(SettingsStore settings) { }
        };
    }

    private MessageStore messageStore() {
        return new MessageStore() {
            @Override public List<Message> load(String conversationId) {
                return new ArrayList<>(loaded);
            }

            @Override public void append(Message message) { }

            @Override public void update(Message message) { }

            @Override public void delete(String messageId) { }

            @Override public int count(String conversationId) { return loaded.size(); }
        };
    }
}
