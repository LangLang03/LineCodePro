package cn.lineai.mvp;

import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.ui.model.ProcessingDuration;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProcessingStateTest {
    @Test public void retriesShareTheClockAndFinishingStopsItOnce() {
        ChatSessionStore store = new ChatSessionStore();
        store.setStreaming(true);
        ChatMessage first = store.withProcessingTimes(new ChatMessage("a", ChatMessage.Role.ASSISTANT, "Checking", true));
        store.mutableMessages().add(first);
        ChatMessage retry = store.withProcessingTimes(ChatMessage.retryNotice("retry", "Retry 2/3"));
        store.mutableMessages().add(retry);
        long started = first.getProcessingStartedAt();
        assertTrue(started > 0);
        assertEquals(started, retry.getProcessingStartedAt());
        store.finishProcessing(started + 186000);
        store.finishProcessing(started + 900000);
        store.setStreaming(false);
        for (ChatMessage message : store.messages()) assertEquals(started + 186000, message.getProcessingFinishedAt());
        ChatMessage finalAnswer = store.withProcessingTimes(new ChatMessage("final", ChatMessage.Role.ASSISTANT, "Done", true));
        assertEquals(started + 186000, finalAnswer.getProcessingFinishedAt());
    }

    @Test public void stoppingGenerationFreezesItsProcessingClock() {
        ChatSessionStore store = new ChatSessionStore();
        store.setStreaming(true);
        store.mutableMessages().add(store.withProcessingTimes(new ChatMessage("a", ChatMessage.Role.ASSISTANT, "", true)));
        store.setStreaming(false);
        long end = store.messages().get(0).getProcessingFinishedAt();
        assertTrue(end >= store.messages().get(0).getProcessingStartedAt());
        store.finishProcessing(end + 60000);
        assertEquals(end, store.messages().get(0).getProcessingFinishedAt());
    }

    @Test public void processingTimesAndRetryIdentitySurviveUpdatesAndStorage() {
        ChatMessage message = ChatMessage.retryNotice("retry", "Retry 2/3: connection reset")
                .withProcessingTimes(1000, 187000)
                .withContent("Retry 2/3: connection reset", "", false)
                .withToolCalls(Collections.<ToolCall>emptyList(), false)
                .withToolResults(Collections.emptyList())
                .withExcludeFromContext(true)
                .withResponseInputItemJson("{}");
        ChatMessage restored = record(message).toChatMessage();
        assertEquals(1000, restored.getProcessingStartedAt());
        assertEquals(187000, restored.getProcessingFinishedAt());
        assertTrue(restored.isRetryNotice());
        assertTrue(restored.isError());
        assertEquals(message.getContent(), restored.getContent());
        assertEquals("3m 6s", ProcessingDuration.format(restored.getProcessingFinishedAt() - restored.getProcessingStartedAt()));
    }

    @Test public void loadingAnInterruptedClockFreezesAtItsLastSavedObservation() {
        MessageRecord record = new MessageRecord("a", ChatMessage.Role.ASSISTANT, "", "", 4000, false,
                false, false, "", "", false,
                "{\"processing_started_at\":1000,\"processing_finished_at\":0,\"processing_observed_at\":4000}");
        assertEquals(4000, record.toChatMessage().getProcessingFinishedAt());
    }

    @Test public void durationFormatsSecondsMinutesAndHours() {
        assertEquals("0s", ProcessingDuration.format(-1));
        assertEquals("59s", ProcessingDuration.format(59999));
        assertEquals("1m 0s", ProcessingDuration.format(60000));
        assertEquals("1h 1m 1s", ProcessingDuration.format(3661000));
    }

    @Test public void incompleteLeofDoesNotStopProcessing() {
        assertFalse(StreamingRenderController.hasProcessingEndMarker("checking <L"));
        assertFalse(StreamingRenderController.hasProcessingEndMarker("checking <LEOF \"done\""));
        assertFalse(StreamingRenderController.hasProcessingEndMarker("<L anything\">"));
        assertTrue(StreamingRenderController.hasProcessingEndMarker("checking <LEOF \"done\">Answer"));
    }

    private MessageRecord record(ChatMessage message) {
        return new MessageRecord(message.getId(), message.getRole(), message.getContent(), message.getReasoningContent(),
                200000, false, message.isHidden(), message.isExcludeFromContext(), message.getToolCallId(),
                message.getToolName(), message.isError(), ConversationPersistenceController.messageRawJson(message));
    }
}
