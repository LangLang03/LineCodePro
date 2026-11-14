package cn.lineai.engine.protocol;

/**
 * Per-stream payload decoder; implementations may keep minimal assembly state.
 *
 * <p>One decoder instance must be used for exactly one HTTP response: implementations that merge
 * tool-call fragments key their state by index, and sharing a decoder across streams would corrupt
 * that state.</p>
 */
public interface StreamDecoder {

    /**
     * Decodes one complete SSE payload into a stream event.
     *
     * @param payload data field text of one SSE event, as produced by {@link SseParser}
     * @return the decoded event, or {@code null} when the payload carries nothing user-visible
     *         (keep-alive, terminal marker)
     * @throws IllegalStateException when the provider reports a protocol-level error event or the
     *         payload is not valid JSON for the codec's wire format
     */
    StreamEvent decode(String payload);
}
