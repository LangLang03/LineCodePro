package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import cn.lineai.tool.api.RuntimeEvent;
import cn.lineai.tool.api.RuntimeInbox;

/**
 * Owner-scoped runtime event bus behind the {@link RuntimeInbox} views handed to agent
 * loops: the shell registry, sub-agent sessions and the user queue events under an owner
 * key, and each loop drains only its own queue.
 *
 * <p>Queue keys are stable identifiers — {@link #ROOT_OWNER} for the root conversation and
 * {@code "agent:<id>"}-style keys for sub agents — so routing survives conversation switches
 * and an idle root can be woken by events that arrive after its turn ended.
 */
public final class AgentEventBus {

    /** Owner key of the root conversation's inbox. */
    public static final String ROOT_OWNER = "root";

    private final Map<String, ConcurrentLinkedQueue<RuntimeEvent>> queues =
            new ConcurrentHashMap<>();

    /**
     * Returns the inbox view of one owner: draining it consumes exactly this owner's queue.
     *
     * @param owner stable owner key ({@link #ROOT_OWNER} or an agent key)
     * @return a view over that owner's queue
     */
    public RuntimeInbox view(String owner) {
        return new View(owner);
    }

    /**
     * Queues one event for an owner.
     *
     * @param owner stable owner key
     * @param event the event to deliver at that owner's next injection point
     */
    public void post(String owner, RuntimeEvent event) {
        if (owner == null || event == null) {
            return;
        }
        queue(owner).add(event);
    }

    /** Removes and returns one owner's queued events. */
    List<RuntimeEvent> drain(String owner) {
        List<RuntimeEvent> drained = new ArrayList<>();
        RuntimeEvent event;
        while ((event = queue(owner).poll()) != null) {
            drained.add(event);
        }
        return drained;
    }

    private ConcurrentLinkedQueue<RuntimeEvent> queue(String owner) {
        return queues.computeIfAbsent(owner, key -> new ConcurrentLinkedQueue<>());
    }

    /** One owner's destructive view over the shared queues. */
    private final class View implements RuntimeInbox {
        private final String owner;

        View(String owner) {
            this.owner = owner;
        }

        @Override public List<RuntimeEvent> drain() {
            return AgentEventBus.this.drain(owner);
        }

        @Override public void post(RuntimeEvent event) {
            AgentEventBus.this.post(owner, event);
        }
    }
}
