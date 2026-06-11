package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FileTreeNode {
    private final String name;
    private final String path;
    private final boolean directory;
    private final boolean expanded;
    private final List<FileTreeNode> children;

    public FileTreeNode(String name, String path, boolean directory, boolean expanded, List<FileTreeNode> children) {
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.directory = directory;
        this.expanded = expanded;
        this.children = Collections.unmodifiableList(new ArrayList<>(children == null ? Collections.emptyList() : children));
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public List<FileTreeNode> getChildren() {
        return children;
    }
}
