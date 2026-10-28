package cn.lineai.runtime;

import android.app.Application;

/** Holds the application graph for the rewritten shell. */
public final class NextApp extends Application {

    private AppGraph graph;

    @Override
    public void onCreate() {
        super.onCreate();
        graph = AppGraph.create(this);
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
