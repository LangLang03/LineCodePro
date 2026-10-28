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

    public AppGraph graph() {
        if (graph == null) {
            graph = AppGraph.create(this);
        }
        return graph;
    }

    @Override
    public void onTerminate() {
        if (graph != null) {
            graph.close();
            graph = null;
        }
        super.onTerminate();
    }
}
