package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class ConversationResumeSanitizerTest {
    private static final String TERMINATED = "上次生成已中断。";

    @Test
    public void sanitizeStopsStreamingAssistantAndCompactMessages() throws Exception {
        ConversationRecord conversation = conversation(
                record("m1", ChatMessage.Role.ASSISTANT, "partial", true, false, "", "", ""),
                record("m2", ChatMessage.Role.ASSISTANT, "", true, false, "", "", "{\"compact_status\":\"running\"}"),
                record("m3", ChatMessage.Role.ASSISTANT, "", false, false, "", "", "{\"compact_status\":\"running\"}")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        Assert.assertTrue(result.changed());
        MessageRecord assistant = result.conversation().getMessages().get(0);
        MessageRecord compact = result.conversation().getMessages().get(1);
        MessageRecord nonStreamingCompact = result.conversation().getMessages().get(2);
        Assert.assertFalse(assistant.isStreaming());
        Assert.assertFalse(compact.isStreaming());
        Assert.assertFalse(nonStreamingCompact.isStreaming());
        Assert.assertEquals(ChatMessage.COMPACT_STATUS_ERROR, new JSONObject(compact.getRawJson()).optString("compact_status"));
        Assert.assertEquals(ChatMessage.COMPACT_STATUS_ERROR, new JSONObject(nonStreamingCompact.getRawJson()).optString("compact_status"));
    }

    @Test
    public void sanitizeReplacesPendingToolResult() throws Exception {
        ConversationRecord conversation = conversation(
                record("a1", ChatMessage.Role.ASSISTANT, "", false, false, "", "", toolCallsRaw("call_shell", "shell_execute")),
                record("t1", ChatMessage.Role.TOOL, "", false, false, "call_shell", "shell_execute", "{\"review_state\":\"pending\"}")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        MessageRecord tool = result.conversation().getMessages().get(1);
        Assert.assertTrue(result.changed());
        Assert.assertTrue(tool.isError());
        Assert.assertEquals(TERMINATED, tool.getContent());
        Assert.assertEquals("", new JSONObject(tool.getRawJson()).optString("review_state"));
    }

    @Test
    public void sanitizeAddsTerminatedResultForMissingToolResult() throws Exception {
        ConversationRecord conversation = conversation(
                record("a1", ChatMessage.Role.ASSISTANT, "", false, false, "", "", toolCallsRaw("call_read", "file_read"))
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        Assert.assertTrue(result.changed());
        Assert.assertEquals(2, result.conversation().getMessages().size());
        MessageRecord tool = result.conversation().getMessages().get(1);
        Assert.assertEquals(ChatMessage.Role.TOOL, tool.getRole());
        Assert.assertEquals("call_read", tool.getToolCallId());
        Assert.assertTrue(tool.isError());
        Assert.assertEquals(TERMINATED, tool.getContent());
    }

    @Test
    public void sanitizeAgentProgressPendingStatus() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_progress", true)
                .put("status", "pending")
                .put("output", "")
                .put("model_content", "")
                .put("tool_calls", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "nested_shell")
                                .put("name", "shell_execute")
                                .put("arguments", "{}")
                                .put("result", new JSONObject()
                                        .put("content", "")
                                        .put("is_error", false)
                                        .put("review_state", "pending")))
                        .put(new JSONObject()
                                .put("id", "nested_read")
                                .put("name", "file_read")
                                .put("arguments", "{}")));
        ConversationRecord conversation = conversation(
                record("t1", ChatMessage.Role.TOOL, progress.toString(), false, false, "agent_call", "agent", "")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        JSONObject sanitized = new JSONObject(result.conversation().getMessages().get(0).getContent());
        JSONObject nestedResult = sanitized.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("result");
        JSONObject missingNestedResult = sanitized.getJSONArray("tool_calls").getJSONObject(1).getJSONObject("result");
        Assert.assertTrue(result.changed());
        Assert.assertEquals("error", sanitized.optString("status"));
        Assert.assertTrue(sanitized.optBoolean("error"));
        Assert.assertEquals(TERMINATED, sanitized.optString("output"));
        Assert.assertEquals(TERMINATED, nestedResult.optString("content"));
        Assert.assertTrue(nestedResult.optBoolean("is_error"));
        Assert.assertEquals("", nestedResult.optString("review_state"));
        Assert.assertEquals(TERMINATED, missingNestedResult.optString("content"));
        Assert.assertTrue(missingNestedResult.optBoolean("is_error"));
    }

    @Test
    public void sanitizePipelineProgressRunningAgents() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_pipeline_progress", true)
                .put("status", "running")
                .put("running", 1)
                .put("failed", 0)
                .put("agents", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "agent_1")
                                .put("status", "running")
                                .put("output", "")
                                .put("error", false)
                                .put("tool_calls", new JSONArray()))
                        .put(new JSONObject()
                                .put("id", "agent_2")
                                .put("status", "waiting")
                                .put("output", "")
                                .put("error", false)
                                .put("tool_calls", new JSONArray())));
        ConversationRecord conversation = conversation(
                record("t1", ChatMessage.Role.TOOL, progress.toString(), false, false, "pipeline_call", "agent_pipeline", "")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        JSONObject sanitized = new JSONObject(result.conversation().getMessages().get(0).getContent());
        JSONArray agents = sanitized.getJSONArray("agents");
        Assert.assertTrue(result.changed());
        Assert.assertEquals("error", sanitized.optString("status"));
        Assert.assertEquals(0, sanitized.optInt("running"));
        Assert.assertEquals(2, sanitized.optInt("failed"));
        Assert.assertEquals("error", agents.getJSONObject(0).optString("status"));
        Assert.assertEquals("error", agents.getJSONObject(1).optString("status"));
    }

    @Test
    public void sanitizeRunningToolResultWithNonEmptyContent() throws Exception {
        ConversationRecord conversation = conversation(
                record("a1", ChatMessage.Role.ASSISTANT, "", false, false, "", "", toolCallsRaw("call_read", "file_read")),
                record("t1", ChatMessage.Role.TOOL, "partial output", false, false, "call_read", "file_read", "{\"review_state\":\"running\"}")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        MessageRecord tool = result.conversation().getMessages().get(1);
        Assert.assertTrue(result.changed());
        Assert.assertTrue(tool.isError());
        Assert.assertEquals(TERMINATED, tool.getContent());
        Assert.assertEquals("", new JSONObject(tool.getRawJson()).optString("review_state"));
    }

    @Test
    public void sanitizePendingToolResultWithEmptyContent() throws Exception {
        ConversationRecord conversation = conversation(
                record("a1", ChatMessage.Role.ASSISTANT, "", false, false, "", "", toolCallsRaw("call_shell", "shell_execute")),
                record("t1", ChatMessage.Role.TOOL, "", false, false, "call_shell", "shell_execute", "{\"review_state\":\"pending\"}")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        MessageRecord tool = result.conversation().getMessages().get(1);
        Assert.assertTrue(result.changed());
        Assert.assertTrue(tool.isError());
        Assert.assertEquals(TERMINATED, tool.getContent());
        Assert.assertEquals("", new JSONObject(tool.getRawJson()).optString("review_state"));
    }

    @Test
    public void sanitizeAcceptedToolResultWithEmptyContent() throws Exception {
        ConversationRecord conversation = conversation(
                record("a1", ChatMessage.Role.ASSISTANT, "", false, false, "", "", toolCallsRaw("call_shell", "shell_execute")),
                record("t1", ChatMessage.Role.TOOL, "", false, false, "call_shell", "shell_execute", "{\"review_state\":\"accepted\"}")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        MessageRecord tool = result.conversation().getMessages().get(1);
        Assert.assertTrue(result.changed());
        Assert.assertTrue(tool.isError());
        Assert.assertEquals(TERMINATED, tool.getContent());
        Assert.assertEquals("", new JSONObject(tool.getRawJson()).optString("review_state"));
    }

    @Test
    public void sanitizeNestedAgentRunningToolCall() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_progress", true)
                .put("status", "running")
                .put("output", "")
                .put("model_content", "")
                .put("tool_calls", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "nested_shell")
                                .put("name", "shell_execute")
                                .put("arguments", "{}")
                                .put("result", new JSONObject()
                                        .put("content", "partial")
                                        .put("is_error", false)
                                        .put("review_state", "running"))));
        ConversationRecord conversation = conversation(
                record("t1", ChatMessage.Role.TOOL, progress.toString(), false, false, "agent_call", "agent", "")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        JSONObject sanitized = new JSONObject(result.conversation().getMessages().get(0).getContent());
        JSONObject nestedResult = sanitized.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("result");
        Assert.assertTrue(result.changed());
        Assert.assertEquals("error", sanitized.optString("status"));
        Assert.assertTrue(sanitized.optBoolean("error"));
        Assert.assertEquals(TERMINATED, nestedResult.optString("content"));
        Assert.assertTrue(nestedResult.optBoolean("is_error"));
        Assert.assertEquals("", nestedResult.optString("review_state"));
    }

    @Test
    public void sanitizePipelineProgressNestedRunningToolCall() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_pipeline_progress", true)
                .put("status", "running")
                .put("running", 1)
                .put("failed", 0)
                .put("agents", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "agent_1")
                                .put("status", "running")
                                .put("output", "")
                                .put("error", false)
                                .put("tool_calls", new JSONArray()
                                        .put(new JSONObject()
                                                .put("id", "nested_shell")
                                                .put("name", "shell_execute")
                                                .put("arguments", "{}")
                                                .put("result", new JSONObject()
                                                        .put("content", "partial")
                                                        .put("is_error", false)
                                                        .put("review_state", "running"))))));
        ConversationRecord conversation = conversation(
                record("t1", ChatMessage.Role.TOOL, progress.toString(), false, false, "pipeline_call", "agent_pipeline", "")
        );

        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(conversation, TERMINATED);

        JSONObject sanitized = new JSONObject(result.conversation().getMessages().get(0).getContent());
        JSONArray agents = sanitized.getJSONArray("agents");
        JSONObject nestedResult = agents.getJSONObject(0).getJSONArray("tool_calls").getJSONObject(0).getJSONObject("result");
        Assert.assertTrue(result.changed());
        Assert.assertEquals("error", sanitized.optString("status"));
        Assert.assertEquals(0, sanitized.optInt("running"));
        Assert.assertEquals(1, sanitized.optInt("failed"));
        Assert.assertEquals("error", agents.getJSONObject(0).optString("status"));
        Assert.assertEquals(TERMINATED, nestedResult.optString("content"));
        Assert.assertTrue(nestedResult.optBoolean("is_error"));
        Assert.assertEquals("", nestedResult.optString("review_state"));
    }

    private ConversationRecord conversation(MessageRecord... records) {
        return new ConversationRecord("c1", "title", "project", 100L, 120L, true, "", Arrays.asList(records));
    }

    private MessageRecord record(
            String id,
            ChatMessage.Role role,
            String content,
            boolean streaming,
            boolean error,
            String toolCallId,
            String toolName,
            String rawJson
    ) {
        return new MessageRecord(id, role, content, "", 100L, streaming, false, false, toolCallId, toolName, error, rawJson);
    }

    private String toolCallsRaw(String id, String name) throws Exception {
        return new JSONObject()
                .put("tool_calls", new JSONArray()
                        .put(new JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("arguments", "{}")))
                .toString();
    }
}
