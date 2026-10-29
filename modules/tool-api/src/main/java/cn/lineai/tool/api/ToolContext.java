package cn.lineai.tool.api;

import java.io.File;

/** Everything a tool may touch; no globals, no static context. */
public interface ToolContext {

    File workspaceRoot();

    /** Optional model for tools that need another completion (image understanding). */
    String modelId();

    /** Whether the action mutates files or state. */
    boolean mutationAllowed();

    /** True when the user opted out of workspace path containment. */
    default boolean pathProtectionBypassed() {
        return false;
    }

    /** Resolves a tool path against the workspace, honouring the bypass switch. */
    default File resolvePath(String relative) {
        return PathPolicy.resolve(workspaceRoot(), relative, pathProtectionBypassed());
    }

    /** Session TODO state; may be null in tests and headless contexts. */
    default TodoStore todos() {
        return null;
    }

    /** Durable memory sink; may be null in tests and headless contexts. */
    default MemorySink memory() {
        return null;
    }

    /** File-change recorder; may be null when diffs are not persisted. */
    default DiffSink diffs() {
        return null;
    }

    /** Phone actions; may be null when accessibility is unavailable. */
    default PhoneControl phone() {
        return null;
    }

    /** True when the selected workspace lives on a remote SSH host. */
    default boolean remoteWorkspace() {
        return false;
    }

    /** Remote working directory of the selected SSH workspace, or "" for the login directory. */
    default String remoteDirectory() {
        return "";
    }

    /** Runs one command on the remote host; only called when {@link #remoteWorkspace()}. */
    default String remoteExecute(String command, int timeoutMs) {
        throw new UnsupportedOperationException("remote execution is unavailable");
    }
}
