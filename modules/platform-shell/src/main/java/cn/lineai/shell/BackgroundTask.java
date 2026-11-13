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

    enum Backend { LOCAL, SSH, PROVIDER }

    final String id;
    final String command;
    final Backend backend;
    final long createdAt = System.currentTimeMillis();
    Status status = Status.RUNNING;
    int exitCode = Integer.MIN_VALUE;
    final StringBuilder output = new StringBuilder();
    boolean truncated;
    boolean reported;
    volatile boolean killRequested;
    // LOCAL
    Process process;
    Thread reader;
    // SSH
    int pid;
    String logPath;
    long offset;
    long lastPoll;
    // PROVIDER
    long handle;
    BackgroundShellRegistry.OutputSink sink;

    BackgroundTask(String id, String command, Backend backend) {
        this.id = id;
        this.command = command;
        this.backend = backend;
    }
}
