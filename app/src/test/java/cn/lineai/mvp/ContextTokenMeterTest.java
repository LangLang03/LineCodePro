package cn.lineai.mvp;

import cn.lineai.context.ContextManager;
import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 长对话渲染热路径的 token 估算记忆化：只重算实例发生变化的消息。 */
public final class ContextTokenMeterTest {
    @Test
    public void reusesEstimatesForUnchangedMessageInstances() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, "first", false));
        messages.add(new ChatMessage("m2", ChatMessage.Role.ASSISTANT, "second", false));
        ContextTokenMeter meter = new ContextTokenMeter(new ContextManager());

        int total = meter.total(messages, true);
        assertEquals(2, meter.computedCount());

        assertEquals(total, meter.total(messages, true));
        assertEquals("同一批消息实例不应重复估算", 2, meter.computedCount());

        messages.set(1, new ChatMessage("m2", ChatMessage.Role.ASSISTANT, "second edited", false));
        int next = meter.total(messages, true);
        assertEquals("只有尾部变化时只多估算一次", 3, meter.computedCount());
        assertTrue(next > total);
    }

    @Test
    public void matchesPlainEstimateForEveryFlag() {
        ContextManager manager = new ContextManager();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, "question", "reasoning", false));
        messages.add(new ChatMessage("m2", ChatMessage.Role.ASSISTANT, "answer", "", false));
        ContextTokenMeter meter = new ContextTokenMeter(manager);

        assertEquals(manager.estimateTokens(messages, true), meter.total(messages, true));
        assertEquals(manager.estimateTokens(messages, false), meter.total(messages, false));
        assertEquals(manager.estimateTokens(messages, true), meter.total(messages, true));
    }

    @Test
    public void shrinkingListDropsStaleTail() {
        ContextManager manager = new ContextManager();
        ContextTokenMeter meter = new ContextTokenMeter(manager);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, "one", false));
        messages.add(new ChatMessage("m2", ChatMessage.Role.ASSISTANT, "two", false));
        messages.add(new ChatMessage("m3", ChatMessage.Role.USER, "three", false));
        meter.total(messages, true);

        // 压缩会截断历史：缩短后的总数必须与全新估算一致，不能残留被移除了的旧条目。
        messages.remove(2);
        messages.remove(1);
        assertEquals(manager.estimateTokens(messages, true), meter.total(messages, true));
    }
}
