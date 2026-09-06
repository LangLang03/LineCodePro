package cn.lineai.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ContextCompactionServiceTest {

    private final ContextCompactionService service = new ContextCompactionService(
            null, null, null, null, new TokenUsageTracker());
    private final ContextManager contextManager = new ContextManager();

    @Test
    public void shouldCompactUsesObservedInputTokensAt80Percent() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, "hello", false));

        // 服务器观测到 8000/10000：达到 80% 硬触发
        assertTrue(service.shouldCompact(messages, 10000, contextManager, true, 8000));
        assertTrue(service.shouldCompact(messages, 10000, contextManager, true, 9999));
        assertFalse(service.shouldCompact(messages, 10000, contextManager, true, 7999));
    }

    @Test
    public void shouldCompactFallsBackToEstimateWhenNoObservedUsage() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, repeat("a", 4000), false));
        // 4000 字符 ≈ 1000 token + 8 开销，远低于 100000 * 0.8
        assertFalse(service.shouldCompact(messages, 100000, contextManager, true, 0));
    }

    @Test
    public void shouldCompactReturnsFalseWhenAllMessagesExcludedAfterCompaction() {
        // 模拟压缩后：旧消息标记 excludeFromContext，tracker reset（observed=0）。
        // 本地估算对 excludeFromContext 消息贡献 0，应远低于阈值，不触发压缩循环。
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", ChatMessage.Role.USER, repeat("a", 4000), false)
                .withExcludeFromContext(true));
        messages.add(new ChatMessage("m2", ChatMessage.Role.ASSISTANT, repeat("b", 4000), false)
                .withExcludeFromContext(true));
        assertFalse(service.shouldCompact(messages, 10000, contextManager, true, 0));
    }

    @Test
    public void shouldSoftCompactUsesObservedInputTokensIn50To80Band() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            messages.add(new ChatMessage("m" + i, ChatMessage.Role.USER, "content", false));
        }

        assertTrue(service.shouldSoftCompact(messages, 10000, contextManager, true, 5000));
        assertTrue(service.shouldSoftCompact(messages, 10000, contextManager, true, 7999));
        // 低于 50% 不触发
        assertFalse(service.shouldSoftCompact(messages, 10000, contextManager, true, 4999));
        // 达到 80% 交给硬触发
        assertFalse(service.shouldSoftCompact(messages, 10000, contextManager, true, 8000));
    }

    @Test
    public void selectRecentUserMessagesKeepsNewestWithinBudget() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("old", ChatMessage.Role.USER, repeat("x", 100), false));
        messages.add(new ChatMessage("assistant", ChatMessage.Role.ASSISTANT, repeat("y", 100), false));
        messages.add(new ChatMessage("recent1", ChatMessage.Role.USER, repeat("x", 100), false));
        messages.add(new ChatMessage("recent2", ChatMessage.Role.USER, repeat("x", 100), false));
        // 每条 user 消息约 8 + 25 = 33 token，预算 70 恰好容纳最近两条
        List<ChatMessage> selected = service.selectRecentUserMessages(messages, 70, contextManager);

        assertEquals(Arrays.asList("recent1", "recent2"),
                Arrays.asList(selected.get(0).getId(), selected.get(1).getId()));
        assertEquals(2, selected.size());
    }

    @Test
    public void selectRecentUserMessagesSkipsExcludedHiddenAndCompactBlocks() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("excluded", ChatMessage.Role.USER, repeat("x", 10), false)
                .withExcludeFromContext(true));
        messages.add(new ChatMessage("hidden", ChatMessage.Role.USER, "old summary", "", false, true, false)
                .withResponseInputItemJson("{}"));
        messages.add(ChatMessage.compactProgress("progress", ChatMessage.COMPACT_STATUS_DONE));
        messages.add(new ChatMessage("real", ChatMessage.Role.USER, repeat("x", 10), false));

        List<ChatMessage> selected = service.selectRecentUserMessages(messages, 10_000, contextManager);

        assertEquals(1, selected.size());
        assertEquals("real", selected.get(0).getId());
    }

    @Test
    public void glmCompactionUsesCompatibleLowReasoningInsteadOfOff() {
        ModelConfig glm = ModelConfig.builder(
                "glm", "GLM", ModelProtocolType.OPENAI_COMPATIBLE, "Zhipu",
                "https://open.bigmodel.cn/api/paas/v4", "key", "glm-5.2").build();

        assertEquals(AiBehaviorSettings.REASONING_LOW,
                ContextCompactionService.compactionRequestOptions(glm).getReasoningEffort());
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
