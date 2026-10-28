package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteMessageStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteMessageStore messages;
    private String conversationId;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "messages.db"));
        messages = new SqliteMessageStore(database);
        conversationId = new SqliteConversationStore(database, () -> 1L).create("t").id;
    }

    @After public void close() {
        database.close();
    }

    private Message message(long seq, Role role, String id) {
        return Message.builder()
                .id(id)
                .conversationId(conversationId)
                .seq(seq)
                .role(role)
                .status(Message.Status.COMPLETE)
                .addBlock(ContentBlock.text("text-" + seq))
                .times(1, 2)
                .build();
    }

    @Test public void appendsAndLoadsMessagesInSequence() {
        messages.append(message(1, Role.USER, "m1"));
        messages.append(message(2, Role.ASSISTANT, "m2"));
        List<Message> loaded = messages.load(conversationId);
        assertEquals(2, loaded.size());
        assertEquals("m1", loaded.get(0).id);
        assertEquals(Role.ASSISTANT, loaded.get(1).role);
        assertEquals("text-2", loaded.get(1).plainText());
        assertEquals(2, messages.count(conversationId));
    }

    @Test public void persistsAssistantToolCallsAndToolResults() {
        messages.append(Message.builder()
                .id("assistant")
                .conversationId(conversationId)
                .seq(1)
                .role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE)
                .addBlock(ContentBlock.text(""))
                .toolCalls(Arrays.asList(
                        new ToolCallRecord("call_1", "read_file", "{\"path\":\"a.txt\"}"),
                        new ToolCallRecord("call_2", "list_dir", "{}")))
                .times(1, 2)
                .build());
        messages.append(Message.builder()
                .id("tool")
                .conversationId(conversationId)
                .seq(2)
                .role(Role.TOOL)
                .status(Message.Status.COMPLETE)
                .tool("call_1", "read_file")
                .addBlock(ContentBlock.text("hello"))
                .times(1, 2)
                .build());

        List<Message> loaded = messages.load(conversationId);
        assertEquals(2, loaded.get(0).toolCalls.size());
        assertEquals("call_2", loaded.get(0).toolCalls.get(1).id);
        assertEquals("list_dir", loaded.get(0).toolCalls.get(1).name);
        assertEquals("{}", loaded.get(0).toolCalls.get(1).arguments);
        assertEquals(Role.TOOL, loaded.get(1).role);
        assertEquals("call_1", loaded.get(1).toolCallId);
        assertEquals("read_file", loaded.get(1).toolName);
    }

    @Test public void updateReplacesBlocksAndToolCalls() {
        messages.append(Message.builder()
                .id("m")
                .conversationId(conversationId)
                .seq(1)
                .role(Role.ASSISTANT)
                .status(Message.Status.STREAMING)
                .addBlock(ContentBlock.text("partial"))
                .toolCalls(Collections.singletonList(new ToolCallRecord("c1", "glob", "{}")))
                .times(1, 2)
                .build());
        messages.update(Message.builder()
                .id("m")
                .conversationId(conversationId)
                .seq(1)
                .role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE)
                .addBlock(ContentBlock.text("final"))
                .addBlock(ContentBlock.reasoning("why"))
                .times(1, 3)
                .build());
        Message loaded = messages.load(conversationId).get(0);
        assertEquals(Message.Status.COMPLETE, loaded.status);
        assertEquals("final", loaded.plainText());
        assertEquals("why", loaded.reasoningText());
        assertTrue(loaded.toolCalls.isEmpty());
    }
}
