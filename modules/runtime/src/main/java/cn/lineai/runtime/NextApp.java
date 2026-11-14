package cn.lineai.runtime;

import android.app.Application;

/** Holds the application graph for the rewritten shell. */
public final class NextApp extends Application {

    private AppGraph graph;

    @Override
    public void onCreate() {
        super.onCreate();
        if (isCrashProcess()) return;
        CrashHandler.install(this);
        graph = AppGraph.create(this);
    }

    /**
     * Detects the {@code :crash} process so it never builds the graph: the crash screen
     * only reads files, and a partial graph there would duplicate the database locks.
     */
    private boolean isCrashProcess() {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/self/cmdline"));
            String name = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            int end = name.indexOf('\0');
            if (end >= 0) name = name.substring(0, end);
            return name.endsWith(":crash");
        } catch (Exception error) {
            return false;
        }
    }

    /**
     * Returns the application graph, rebuilding it lazily when it was never created or was
     * terminated, so tool contexts on secondary processes still resolve dependencies.
     *
     * @return the live application graph
     */
    public AppGraph graph() {
        if (graph == null) {
            graph = AppGraph.create(this);
        }
        return graph;
    }

    /** Closes the graph, releasing the database and scheduler; not called on real devices. */
    @Override
    public void onTerminate() {
        if (graph != null) {
            graph.close();
            graph = null;
        }
        super.onTerminate();
    }
}
