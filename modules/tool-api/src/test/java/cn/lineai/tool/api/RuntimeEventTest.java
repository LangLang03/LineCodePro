package cn.lineai.tool.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Notice-text rendering rules of the runtime event hierarchy. */
public class RuntimeEventTest {

    @Test public void agentTurnCompletedCarriesTheFinalOutput() {
        RuntimeEvent.AgentTurnCompleted event = new RuntimeEvent.AgentTurnCompleted(
                "agent-1", "Explorer", false, "Found 3 matches.");
        String text = event.noticeText();
        assertTrue(text.contains("Sub agent Explorer [agent-1] finished."));
        assertTrue(text.contains("Final output:\nFound 3 matches."));
    }

    @Test public void failedTurnsReportTheFailureWithoutOutput() {
        RuntimeEvent.AgentTurnCompleted event = new RuntimeEvent.AgentTurnCompleted(
                "agent-1", "Explorer", true, "partial text");
        String text = event.noticeText();
        assertTrue(text.contains("Sub agent Explorer [agent-1] failed."));
        assertTrue(text.contains("partial text"));
    }

    @Test public void oversizedOutputIsTruncatedWithAPointer() {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i <= 32_000 / 8; i++) output.append("abcdefgh");
        RuntimeEvent.AgentTurnCompleted event = new RuntimeEvent.AgentTurnCompleted(
                "a", "A", false, output.toString());
        String text = event.noticeText();
        assertTrue(text.length() < output.length() + 500);
        assertTrue(text.contains("output truncated"));
        assertTrue(text.contains("[a]"));
    }

    @Test public void simpleEventsRenderTheirLine() {
        assertEquals("Message from sub agent [a]:\nhello",
                new RuntimeEvent.AgentMessage("a", "hello").noticeText());
        assertEquals("Sub agent [a] needs input: approval",
                new RuntimeEvent.AgentNeedsInput("a", "approval").noticeText());
        assertEquals("Sub agent [a] failed: boom",
                new RuntimeEvent.AgentFailed("a", "boom").noticeText());
        assertEquals("Workspace changed: build done",
                new RuntimeEvent.WorkspaceChanged("build done").noticeText());
        assertEquals("Message from user:\nhi",
                new RuntimeEvent.UserMessage("", "hi").noticeText());
        assertEquals("notice",
                new RuntimeEvent.ShellNotice("notice").noticeText());
    }
}
