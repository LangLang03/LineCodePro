package cn.lineai.tool.agent;

import java.util.List;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.AgentEntry;

/**
 * Runtime behind the agent tool family: launches sub agents asynchronously, tracks their
 * live status and routes messages between the parent loop and its sub agents.
 *
 * <p>Implemented above the feature layer where each sub agent owns a real conversation and
 * a full tool loop; tools only see this interface. All methods return immediately —
 * completion is delivered to the parent loop through the runtime event inbox as a hidden
 * user message carrying only the sub agent's final output.</p>
 */
public interface AgentRuntime {

    /**
     * Starts a sub agent session bound to one local workspace directory and begins its first
     * turn.
     *
     * <p>The binding defines the session's {@code ToolContext.workspaceRoot()}: {@code "."} and
     * every relative path inside the session resolve against it, and the shell runs there by
     * default. The caller resolves and validates the directory before starting the session.
     * SSH and terminal-provider workspaces are not supported because their tools operate on a
     * remote host where a local directory has no meaning; they pass an empty binding and the
     * session keeps inheriting the parent workspace.</p>
     *
     * @param agent agent extension providing name, prompt and tool permissions
     * @param task task text handed to the agent as its first user message
     * @param persistent true for a messageable, revivable session; false for a
     *                   one-shot transient run
     * @param workspace canonical absolute local directory the session is bound to; empty to
     *                  inherit the parent workspace
     * @return the started entry (status running), or null when the runtime is unavailable
     */
    AgentEntry start(AgentExtension agent, String task, boolean persistent, String workspace);

    /**
     * Lists every known sub agent session with its live status.
     *
     * @return one entry per session, the parent (root) conversation excluded
     */
    List<AgentEntry> list();

    /**
     * Sends a message to a persistent sub agent: a finished agent is revived with a new
     * turn, a running agent receives the message as a queued event.
     *
     * @param agentId the sub agent to address; addressing the parent itself is rejected
     * @param message message body delivered as a user turn
     * @return a human-readable delivery report for the calling model
     */
    String send(String agentId, String message);

    /**
     * Blocks until any tracked sub agent changes status or the timeout elapses.
     *
     * @param timeoutMs maximum wait in milliseconds
     * @return the status snapshot at return time
     */
    List<AgentEntry> waitFor(long timeoutMs);
}
