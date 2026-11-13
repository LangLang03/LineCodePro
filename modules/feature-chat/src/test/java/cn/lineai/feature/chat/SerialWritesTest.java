package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** A failed write (e.g. the conversation vanished mid-turn) must not stall the drain. */
public class SerialWritesTest {

    @Test public void aFailedWriteDoesNotBlockLaterWrites() {
        final List<String> done = new ArrayList<>();
        SerialWrites writes = new SerialWrites(Scheduler.immediate());
        writes.submit(() -> { throw new IllegalStateException("conversation vanished"); });
        writes.submit(() -> done.add("second"));
        assertEquals(1, done.size());
    }

    @Test public void writesStillRunSequentiallyAfterAFailure() {
        final List<String> order = new ArrayList<>();
        SerialWrites writes = new SerialWrites(Scheduler.immediate());
        writes.submit(() -> order.add("first"));
        writes.submit(() -> { throw new IllegalStateException("fk violation"); });
        writes.submit(() -> order.add("third"));
        assertEquals(2, order.size());
        assertEquals("first", order.get(0));
        assertEquals("third", order.get(1));
    }
}
