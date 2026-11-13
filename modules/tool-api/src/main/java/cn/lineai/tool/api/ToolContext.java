package cn.lineai.tool.api;

import java.io.File;

/** Everything a tool may touch; no globals, no static context. */
public interface ToolContext {

    /**
     * Root directory all local file tools are contained in.
     *
     * @return the workspace root used by {@link PathPolicy} containment
     */
    File workspaceRoot();

    /** Optional model for tools that need another completion (image understanding).
     *
     * @return the model identifier delegated to, or an empty string when none is configured
     */
    String modelId();

    /** Whether the action mutates files or state.
     *
     * @return {@code true} when the current mode permits mutating tools to execute
     */
    boolean mutationAllowed();

    /** True when the user opted out of workspace path containment.
     *
     * @return {@code true} when {@link #resolvePath} skips containment checks
     */
    default boolean pathProtectionBypassed() {
        return false;
    }

    /**
     * Resolves a tool path against the workspace, honouring the bypass switch.
     *
     * @param relative tool-supplied path, absolute or relative
     * @return the canonical file to operate on
     * @throws SecurityException when containment is enabled and the path escapes the workspace
     */
    default File resolvePath(String relative) {
        return PathPolicy.resolve(workspaceRoot(), relative, pathProtectionBypassed());
    }

    /** Session TODO state; may be null in tests and headless contexts.
     *
     * @return the TODO store, or {@code null} when the session has none
     */
    default TodoStore todos() {
        return null;
    }

    /** Durable memory sink; may be null in tests and headless contexts.
     *
     * @return the memory sink, or {@code null} when persistence is unavailable
     */
    default MemorySink memory() {
        return null;
    }

    /** File-change recorder; may be null when diffs are not persisted.
     *
     * @return the diff sink, or {@code null} when diffs are not recorded
     */
    default DiffSink diffs() {
        return null;
    }

    /** Phone actions; may be null when accessibility is unavailable.
     *
     * @return the phone control, or {@code null} when the device cannot perform such actions
     */
    default PhoneControl phone() {
        return null;
    }

    /** Background shell task registry; may be null in tests and headless contexts.
     *
     * @return the background task registry, or {@code null} when unavailable
     */
    default BackgroundTasks backgrounds() {
        return null;
    }

    /** True when the selected workspace lives on a remote SSH host.
     *
     * @return {@code true} when shell commands must be routed to the remote host
     */
    default boolean remoteWorkspace() {
        return false;
    }

    /** Remote working directory of the selected SSH workspace, or "" for the login directory.
     *
     * @return the remote working directory; empty for the login directory
     */
    default String remoteDirectory() {
        return "";
    }

    /**
     * Runs one command on the remote host; only called when {@link #remoteWorkspace()}.
     *
     * @param command the shell command line to execute remotely
     * @param timeoutMs maximum wall-clock time to wait for completion
     * @return combined command output
     * @throws UnsupportedOperationException when the context has no remote transport
     */
    default String remoteExecute(String command, int timeoutMs) {
        throw new UnsupportedOperationException("remote execution is unavailable");
    }
}
