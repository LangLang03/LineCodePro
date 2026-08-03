package cn.lineai.mvp;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ChatMessage;
import cn.lineai.mvp.agent.AgentExecutionController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;

public final class GenerationFlowControllerTest {
    @Test
    public void agentHostToolResultDispatchesToOuterControllerOnce() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        ToolMessageController toolMessages = new ToolMessageController(messages, new IncrementingIdProvider());
        GenerationFlowController controller = new GenerationFlowController(
                messages,
                new ChatSessionStore(),
                null,
                null,
                null,
                null,
                null,
                null,
                toolMessages,
                null,
                null,
                null,
                null,
                new MainThreadDispatcher(null, true),
                new BackgroundTaskRunner(),
                new FakeHost()
        );

        agentHost(controller).addOrReplaceToolResult(ToolResult.withReview("call_1", "agent", "done", false, "", "", ""));

        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(ChatMessage.Role.TOOL, messages.get(0).getRole());
        Assert.assertEquals("call_1", messages.get(0).getToolCallId());
        Assert.assertEquals("done", messages.get(0).getContent());
    }

    private static AgentExecutionController.Host agentHost(GenerationFlowController controller) {
        try {
            Field field = GenerationFlowController.class.getDeclaredField("agentHost");
            field.setAccessible(true);
            return (AgentExecutionController.Host) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class IncrementingIdProvider implements ToolMessageController.IdProvider {
        private int value;

        @Override
        public String nextId() {
            value++;
            return "tool_" + value;
        }
    }

    private static final class FakeHost implements GenerationFlowController.Host {
        @Override
        public String nextId() {
            return "id";
        }

        @Override
        public String projectPath() {
            return ".";
        }

        @Override
        public String projectSource() {
            return "";
        }

        @Override
        public String currentConversationId() {
            return "conversation";
        }

        @Override
        public String syncModePermission() {
            return "";
        }

        @Override
        public void persistCurrentConversation() {
        }

        @Override
        public void render() {
        }

        @Override
        public void stopGenerationKeepAlive() {
        }

        @Override
        public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
        }

        @Override
        public String formatRetryNotice(int attempt, int maxRetries, String error) {
            return "retry " + attempt + "/" + maxRetries + ": " + (error == null ? "" : error);
        }

        @Override
        public String formatModelFailed(String error) {
            return error == null ? "" : error;
        }
    }
}
