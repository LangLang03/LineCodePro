package cn.lineai.mvp;

import android.app.Application;
import android.content.Context;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.prompt.MemoryPromptBuilder;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.context.ContextManager;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.repository.*;
import cn.lineai.data.service.ContextResourceProvider;
import cn.lineai.model.*;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.service.LearningContextService;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.workspace.WorkspacePaths;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public final class StablePromptPrefixTest {
    private final ChatSessionStore session = new ChatSessionStore();
    private final TodoStateStore todos = new TodoStateStore();
    private final ModelConfig model = ModelConfig.builder("test", "Model", ModelProtocolType.OPENAI_COMPATIBLE,
            "Custom", "https://example.invalid/v1", "", "model").toolCallLimit(2).build();
    private ModelPromptController controller;
    private LearningContextRepository memories;
    private AiBehaviorSettingsRepository behavior;
    private PromptTemplateRepository templates;
    private String project = "/workspace";

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        LineCodeDatabase database = LineCodeDatabase.getInstance(context);
        SettingsRepository settings = new SettingsRepository(database);
        behavior = new AiBehaviorSettingsRepository(settings);
        templates = new PromptTemplateRepository(new ContextResourceProvider(context), settings);
        WorkspacePaths paths = new WorkspacePaths(context);
        memories = new LearningContextRepository(database, paths, templates);
        ModelStore models = proxy(ModelStore.class, (method, args) -> method.equals("getSelectedModel") ? model : null);
        ExtensionStore extensions = proxy(ExtensionStore.class, (method, args) -> "## Installed extensions\nStable instructions.");
        ToolSettingsStore tools = proxy(ToolSettingsStore.class, (method, args) -> {
            if (method.equals("buildToolPrompt")) return "## Available tools\nfile_read";
            if (method.equals("getEnabledToolNames")) return Collections.singleton("file_read");
            return null;
        });
        controller = new ModelPromptController(session.mutableMessages(), session, behavior,
                new ChatModeRepository(settings), templates,
                new LearningContextService(memories, new MemoryPromptBuilder(paths, templates)),
                new ContextManager(), models, extensions, new SystemPromptProvider(context, templates),
                tools, new ToolRegistry(context), todos, new ModelPromptController.Host() {
                    public String syncModePermission() { return ChatMode.AGENT; }
                    public String projectPath() { return project; }
                    public String projectSource() { return "local"; }
                    public boolean isTerminalProviderExecutionMode() { return false; }
                });
        session.mutableMessages().add(new ChatMessage("u1", ChatMessage.Role.USER, "修复重连", false));
    }

    @Test public void memoryAndTodoChangesOnlyUpdateRequestTail() {
        List<ModelMessage> before = controller.buildModelMessages("修复重连");
        memories.saveMemory("memory", "user", "", "Use exponential backoff for reconnects.");
        todos.replace(Collections.singletonList(new TodoItem("检查重连", "in_progress")));
        List<ModelMessage> after = controller.buildModelMessages("检查重连");
        assertSamePrefix(before, after, before.size() - 1);
        assertFalse(after.get(0).getContent().contains("exponential backoff"));
        assertTrue(tail(after).contains("exponential backoff"));
        assertTrue(tail(after).contains("检查重连"));
        todos.clear();
        List<ModelMessage> cleared = controller.buildModelMessages("继续");
        assertSamePrefix(after, cleared, after.size() - 1);
        assertTrue(tail(cleared).contains("Current todo list: empty."));
        assertFalse(tail(cleared).contains("检查重连"));
    }

    @Test public void toolLoopsAndNewAttachmentsLeaveEarlierMessagesUnchanged() {
        session.mutableMessages().add(new ChatMessage("u2", ChatMessage.Role.USER, "看这个文件", false,
                Collections.singletonList(new InputAttachment("First.java", "/workspace/First.java", "local")))
                .withResponseInputItemJson("{\"type\":\"message\",\"role\":\"user\",\"content\":\"native input\"}"));
        List<ModelMessage> before = controller.buildModelMessages("看这个文件");
        assertTrue(before.stream().anyMatch(m -> m.getContent().contains("/workspace/First.java")));
        assertTrue(before.stream().anyMatch(m -> m.getRawInputJson().contains("native input")));
        session.mutableMessages().add(new ChatMessage("a1", ChatMessage.Role.ASSISTANT, "正在读取", false)
                .withToolCalls(Collections.singletonList(new ToolCall("call", "file_read", "{}")), false));
        session.mutableMessages().add(ChatMessage.toolResult("t1", "file content", "call", "file_read", false));
        List<ModelMessage> loop = controller.buildModelMessages("看这个文件", 1);
        assertSamePrefix(before, loop, before.size() - 1);
        assertEquals("assistant", loop.get(loop.size() - 3).getRole());
        assertEquals("tool", loop.get(loop.size() - 2).getRole());
        session.mutableMessages().add(new ChatMessage("u3", ChatMessage.Role.USER, "还有这个", false,
                Collections.singletonList(new InputAttachment("Second.java", "/workspace/Second.java", "local"))));
        List<ModelMessage> next = controller.buildModelMessages("还有这个");
        assertSamePrefix(loop, next, loop.size() - 1);
        assertFalse(next.get(0).getContent().contains("First.java"));
        assertTrue(next.stream().anyMatch(m -> m.getContent().contains("Second.java")));
    }

    @Test public void toolBudgetStillRemovesNativeToolsAndSignalsTheCurrentLimit() {
        List<ModelMessage> available = controller.buildModelMessages("继续", 0);
        List<ModelMessage> exhausted = controller.buildModelMessages("继续", 2);
        assertSamePrefix(available, exhausted, available.size() - 1);
        assertTrue(tail(exhausted).contains("当前没有可用工具"));
        assertFalse(controller.requestOptions(behavior.get(), model, 0).getTools().isEmpty());
        assertTrue(controller.requestOptions(behavior.get(), model, 2).getTools().isEmpty());
    }

    @Test public void explicitWorkspaceAndTemplateChangesRefreshThePrefix() {
        String first = controller.buildModelMessages("继续").get(0).getContent();
        project = "/new-workspace";
        assertNotEquals(first, controller.buildModelMessages("继续").get(0).getContent());
        templates.saveTemplate(PromptTemplateRepository.ID_SYSTEM_PROMPT, "Custom rules. {{TOOLS_CONTEXT}} {{TODO_STATE}}");
        assertTrue(controller.buildModelMessages("继续").get(0).getContent().startsWith("Custom rules."));
    }

    private static String tail(List<ModelMessage> messages) { return messages.get(messages.size() - 1).getContent(); }
    private static void assertSamePrefix(List<ModelMessage> first, List<ModelMessage> second, int count) {
        for (int i = 0; i < count; i++) {
            assertEquals("role " + i, first.get(i).getRole(), second.get(i).getRole());
            assertEquals("content " + i, first.get(i).getContent(), second.get(i).getContent());
            assertEquals("native input " + i, first.get(i).getRawInputJson(), second.get(i).getRawInputJson());
        }
    }
    private interface Answer { Object answer(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> answer.answer(method.getName(), args));
    }
}
