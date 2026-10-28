package cn.lineai.core.model;

/** One entry in the workspace file tree. */
public final class WorkspaceNode {

    public final String name;
    public final String path;
    public final boolean directory;

    public WorkspaceNode(String name, String path, boolean directory) {
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.directory = directory;
    }
}
