package cn.lineai.tool.api;

import java.util.List;

/**
 * Registry of background shell tasks started by the {@code shell} tool.
 *
 * <p>A task is a shell command that outlives the tool call that started it. The tool returns a
 * task id immediately; {@code shell_output} reads (optionally blocking for) its output and
 * {@code shell_kill} terminates it. Completed-but-unreported tasks are surfaced once through
 * {@link #drainCompleted()} so the agent loop can inject a hidden user message.</p>
 */
public interface BackgroundTasks {

    /** Starts a command in the background and returns its task id, e.g. {@code bg-3}. */
    String start(String command, String cwd, boolean remote);

    /**
     * Output snapshot plus status of one task; when {@code block} is true, waits up to
     * {@code timeoutMs} for the task to finish first.
     */
    String output(String id, boolean block, int timeoutMs);

    /** Terminates one task; returns a human-readable outcome for the model. */
    String kill(String id);

    /**
     * Reminder texts for tasks that finished since the last drain and were never reported to the
     * model; each task is reported at most once.
     */
    List<String> drainCompleted();

    /** Kills local task processes; called when the application is closed. */
    void shutdown();
}
