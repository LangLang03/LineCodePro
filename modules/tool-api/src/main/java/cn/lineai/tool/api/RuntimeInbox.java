package cn.lineai.tool.api;

import java.util.List;

/**
 * The mailbox of one agent loop: where the shell registry, sub agents, file watchers and
 * the user hand asynchronous {@link RuntimeEvent}s to the loop.
 *
 * <p>Views are owner-scoped: an implementation hands out a view per agent so each loop
 * drains only its own events. Draining is destructive — an event is delivered exactly once,
 * at one of the loop's injection points (turn start, after a tool batch, or as a
 * keep-alive when the model would otherwise finish the turn).
 */
public interface RuntimeInbox {

    /**
     * Removes and returns all pending events for this view's owner, in posting order.
     *
     * @return the pending events; empty when nothing is queued
     */
    List<RuntimeEvent> drain();

    /**
     * Queues one event for this view's owner.
     *
     * @param event the event to deliver at the next injection point
     */
    void post(RuntimeEvent event);
}
