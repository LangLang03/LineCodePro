package cn.lineai.engine.protocol;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ToolCallParsingTest {

    @Test public void mergerAccumulatesFragmentsPerIndex() {
        ToolCallMerger merger = new ToolCallMerger();
        merger.accept(0, "call_1", "read_file", "{\"path\":");
        merger.accept(1, "call_2", "list_dir", "{\"path\":\"src\"}");
        merger.accept(0, "", "", "\"src/a.txt\"}");
        List<ToolCallDraft> calls = merger.completed();
        assertEquals(2, calls.size());
        assertEquals("read_file", calls.get(0).name);
        assertEquals("{\"path\":\"src/a.txt\"}", calls.get(0).arguments);
        assertEquals("list_dir", calls.get(1).name);
    }

    @Test public void openAiDeltaCarriesToolFragments() {
        StreamEvent event = OpenAiChatCodec.parse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\","
                        + "\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\"}}]}}]}");
        assertNotNull(event);
        assertEquals(1, event.calls.size());
        assertEquals("read_file", event.calls.get(0).name);
        assertEquals("{\"path\":", event.calls.get(0).arguments);
    }

    @Test public void anthropicToolUseStartAndJsonDelta() {
        StreamEvent start = AnthropicCodec.parse(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
                        + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"read_file\"}}");
        assertEquals("read_file", start.calls.get(0).name);
        StreamEvent delta = AnthropicCodec.parse(
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                        + "{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"a\\\"}\"}}");
        assertEquals("{\"path\":\"a\"}", delta.calls.get(0).arguments);
    }

    @Test public void codexDecoderMapsCallIdsToStableIndexes() {
        StreamDecoder decoder = CodexCodec.decoder();
        StreamEvent added = decoder.decode("{\"type\":\"response.output_item.added\",\"item\":"
                + "{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"read_file\"}}");
        StreamEvent delta = decoder.decode("{\"type\":\"response.function_call_arguments.delta\","
                + "\"call_id\":\"c1\",\"delta\":\"{\\\"path\\\":\"}");
        assertEquals(added.calls.get(0).index, delta.calls.get(0).index);
        assertEquals("read_file", added.calls.get(0).name);
    }
}
