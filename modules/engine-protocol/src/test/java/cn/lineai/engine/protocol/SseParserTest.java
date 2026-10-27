package cn.lineai.engine.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SseParserTest {

    @Test
    public void dispatchesOnBlankLine() {
        SseParser parser = new SseParser();
        assertTrue(parser.feed("data: {\"a\":1}").isEmpty());
        List<String> events = parser.feed("");
        assertEquals(1, events.size());
        assertEquals("{\"a\":1}", events.get(0));
    }

    @Test
    public void joinsMultiLineData() {
        SseParser parser = new SseParser();
        parser.feed("data: line1");
        parser.feed("data: line2");
        List<String> events = parser.feed("");
        assertEquals("line1\nline2", events.get(0));
    }

    @Test
    public void ignoresCommentsAndFields() {
        SseParser parser = new SseParser();
        parser.feed(": keep-alive");
        parser.feed("event: message");
        assertTrue(parser.feed("").isEmpty());
    }

    @Test
    public void finishFlushesTrailingEvent() {
        SseParser parser = new SseParser();
        parser.feed("data: tail");
        assertEquals("tail", parser.finish().get(0));
    }

    @Test
    public void parseReturnsNullForDoneMarker() {
        assertNull(OpenAiChatCodec.parse("[DONE]"));
    }

    @Test
    public void parseReadsContentAndUsage() {
        StreamEvent event = OpenAiChatCodec.parse(
                "{\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}");
        assertEquals("hi", event.text);
        assertEquals("stop", event.finishReason);
        assertEquals(3, event.promptTokens);
        assertEquals(4, event.completionTokens);
    }

    @Test
    public void parseReadsReasoning() {
        StreamEvent event = OpenAiChatCodec.parse(
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"think\"}}]}");
        assertEquals("think", event.reasoning);
    }

    @Test
    public void endpointAppendsChatPathOnce() {
        assertEquals("https://x/v1/chat/completions", OpenAiChatCodec.endpoint("https://x/v1/"));
        assertEquals("https://x/v1/chat/completions",
                OpenAiChatCodec.endpoint("https://x/v1/chat/completions"));
    }
}
