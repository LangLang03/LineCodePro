package cn.lineai.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * 验证 ChatUiState 的不可变快照契约（MVS §5.2 / §15.5）：
 * 1. 集合字段不可变（getMessages()/getAvailableModels() 修改抛 UnsupportedOperationException）
 * 2. 构造函数对输入做防御性拷贝（构造后修改输入不影响 State）
 * 3. 无 setter（编译期保证，这里验证字段读取一致性）
 */
public final class ChatUiStateTest {

    private static ChatMessage message(String id) {
        return new ChatMessage(id, ChatMessage.Role.USER, "content-" + id, false);
    }

    private static ModelConfig model(String id, int contextSize) {
        return ModelConfig.builder(id, id, ModelProtocolType.OPENAI_COMPATIBLE, "provider", "", "", id)
                .contextSize(contextSize)
                .build();
    }

    private static ChatUiState state(List<ChatMessage> messages) {
        return new ChatUiState(
                "project", "/repo", "model-label", "context-label", 50,
                false, true, messages);
    }

    @Test
    public void getMessagesReturnsUnmodifiableView() {
        ChatUiState state = state(Arrays.asList(message("m1"), message("m2")));

        try {
            state.getMessages().add(message("m3"));
            Assert.fail("getMessages() 应返回不可变列表");
        } catch (UnsupportedOperationException expected) {
            // 预期的不可变保护
        }
        Assert.assertEquals(2, state.getMessages().size());
    }

    @Test
    public void getAvailableModelsReturnsUnmodifiableView() {
        ChatUiState state = state(Arrays.asList(message("m1")));

        try {
            state.getAvailableModels().add(model("gpt-4", 128000));
            Assert.fail("getAvailableModels() 应返回不可变列表");
        } catch (UnsupportedOperationException expected) {
            // 预期的不可变保护
        }
        Assert.assertEquals(0, state.getAvailableModels().size());
    }

    @Test
    public void constructorDefensivelyCopiesMessagesInput() {
        List<ChatMessage> input = new ArrayList<>(Arrays.asList(message("m1")));
        ChatUiState state = state(input);

        // 构造后修改输入列表，State 不应受影响
        input.add(message("m2"));
        input.clear();

        Assert.assertEquals(1, state.getMessages().size());
        Assert.assertEquals("content-m1", state.getMessages().get(0).getContent());
    }

    @Test
    public void constructorDefensivelyCopiesAvailableModelsInput() {
        List<ModelConfig> input = new ArrayList<>();
        input.add(model("gpt-4", 128000));
        ChatUiState state = new ChatUiState(
                "project", "/repo", "model-label", "context-label", 50,
                false, true, true, true, true,
                OutputSettings.BROWSER_BUILTIN, InputSettings.ENTER_SEND,
                ChatMode.DEFAULT, "c1",
                Arrays.asList(message("m1")),
                "gpt-4", input);

        input.clear();
        input.add(model("claude", 200000));

        Assert.assertEquals(1, state.getAvailableModels().size());
        Assert.assertEquals("gpt-4", state.getAvailableModels().get(0).getModelId());
    }

    @Test
    public void constructorNormalizesNullsWithoutMutatingInput() {
        List<ChatMessage> input = new ArrayList<>();
        ChatUiState state = state(input);

        Assert.assertEquals("/repo", state.getProjectPath());
        Assert.assertEquals("", state.getConversationId());
        Assert.assertEquals(0, state.getMessages().size());
        Assert.assertTrue(state.getAvailableModels().isEmpty());
    }

    @Test
    public void fieldsAreStableAcrossReads() {
        ChatUiState state = state(Arrays.asList(message("m1")));

        // 同一 State 多次读取结果一致（无内部可变性泄漏）
        Assert.assertEquals(state.getProjectPath(), state.getProjectPath());
        Assert.assertEquals(state.getMessages(), state.getMessages());
        Assert.assertSame(state.getMessages(), state.getMessages());
    }
}
