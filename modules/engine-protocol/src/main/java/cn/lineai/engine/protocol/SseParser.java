package cn.lineai.engine.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental SSE reader.
 *
 * <p>Feed one line at a time; completed events are returned. Blank lines dispatch, multi-line
 * {@code data:} fields are joined with a newline, comments are ignored. No threading and no IO —
 * the caller owns the socket.</p>
 */
public final class SseParser {

    private final StringBuilder data = new StringBuilder();
    private boolean hasData;

    /** Returns the payload of every event completed by this line (usually zero or one). */
    public List<String> feed(String line) {
        List<String> events = new ArrayList<>(1);
        if (line == null) {
            return events;
        }
        if (line.isEmpty()) {
            dispatch(events);
            return events;
        }
        if (line.charAt(0) == ':') {
            return events;
        }
        if (line.startsWith("data:")) {
            String value = line.substring(5);
            if (!value.isEmpty() && value.charAt(0) == ' ') {
                value = value.substring(1);
            }
            if (hasData) {
                data.append('\n');
            }
            data.append(value);
            hasData = true;
        }
        return events;
    }

    /** Flushes a trailing event that was not terminated by a blank line. */
    public List<String> finish() {
        List<String> events = new ArrayList<>(1);
        dispatch(events);
        return events;
    }

    private void dispatch(List<String> events) {
        if (!hasData) {
            return;
        }
        String payload = data.toString();
        data.setLength(0);
        hasData = false;
        if (!payload.isEmpty()) {
            events.add(payload);
        }
    }
}
