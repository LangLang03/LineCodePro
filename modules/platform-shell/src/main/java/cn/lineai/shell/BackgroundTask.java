package cn.lineai.shell;

/**
 * One background shell task and its lifecycle state, shared by the registry backends.
 * All field access happens under the owning registry's lock except {@link #killRequested},
 * which is volatile so the kill path can flag it before acquiring the lock.
 */
final class BackgroundTask {

    /** Lifecycle states; {@link #label} feeds the user-facing describe/kill messages. */
    enum Status {
        RUNNING, COMPLETED, FAILED, KILLED;

        String label() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Execution backend that owns the lifecycle mechanics of the task. */
    enum Backend { LOCAL, SSH, PROVIDER }

    /** Registry-assigned identifier, stable for the lifetime of the task. */
    final String id;
    /** Command line handed to the backend, without any wrapper scripts. */
    final String command;
    /** Backend selected at start time; immutable afterwards. */
    final Backend backend;
    /** Wall-clock start time, feeding the elapsed time shown in status output. */
    final long createdAt = System.currentTimeMillis();
    /** Current lifecycle state; only the registry lock holders may advance it. */
    Status status = Status.RUNNING;
    /** Exit code, or {@link Integer#MIN_VALUE} while unknown or not applicable. */
    int exitCode = Integer.MIN_VALUE;
    /** Ring-buffered output, trimmed by the registry to {@code MAX_CHARS}. */
    final StringBuilder output = new StringBuilder();
    /** Set once the ring buffer has dropped leading output. */
    boolean truncated;
    /** Set once the completion has been surfaced to the user or the drain loop. */
    boolean reported;
    /** Volatile kill flag: set by the kill path before it acquires the registry lock. */
    volatile boolean killRequested;
    // LOCAL
    /** Local backend only: the {@link Process} handle driving the task. */
    Process process;
    /** Local backend only: the one-shot daemon thread draining the process pipe. */
    Thread reader;
    // SSH
    /** SSH backend only: remote process id reported by the launch script. */
    int pid;
    /** SSH backend only: remote temp log path reported by the launch script. */
    String logPath;
    /** SSH and provider backends: byte offset of output already consumed. */
    long offset;
    /** Remote backends: timestamp of the last poll, driving the poll rate limit. */
    long lastPoll;
    // PROVIDER
    /** Provider backend only: provider-side handle, {@code <= 0} before start succeeds. */
    long handle;
    /** Provider backend only: sink receiving streamed output and completion. */
    BackgroundShellRegistry.OutputSink sink;

    /**
     * Creates a task in {@link Status#RUNNING} with no output and no exit code.
     *
     * @param id registry-assigned identifier
     * @param command the raw command line to execute
     * @param backend backend that will own the execution
     */
    BackgroundTask(String id, String command, Backend backend) {
        this.id = id;
        this.command = command;
        this.backend = backend;
    }
}
