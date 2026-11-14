package cn.lineai.core.model;

/** One entry in the workspace file tree. */
public final class WorkspaceNode {

    public final String name;
    public final String path;
    public final boolean directory;

    /**
     * Creates a node, normalising null strings to empty.
     * @param name display name
     * @param path absolute path within the workspace
     * @param directory whether the node is a directory
     */
    public WorkspaceNode(String name, String path, boolean directory) {
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.directory = directory;
    }
}
