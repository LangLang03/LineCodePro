package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.List;

/** A workspace project: a named directory that tools operate inside. */
public final class Project {

    public static final String DEFAULT_ID = "default";

    public final String id;
    public final String name;
    public final String path;
    public final boolean selected;
    public final long createdAt;
    public final long updatedAt;

    /**
     * Creates a project, normalising null strings to empty.
     * @param id stable project identifier
     * @param name display name
     * @param path working directory the tools operate in
     * @param selected whether the project is currently active
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last modification timestamp in epoch milliseconds
     */
    public Project(String id, String name, String path, boolean selected,
                   long createdAt, long updatedAt) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.selected = selected;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Returns a copy with the selection flag replaced.
     * @param value new selection state
     * @return a new immutable project
     */
    public Project selected(boolean value) {
        return new Project(id, name, path, value, createdAt, updatedAt);
    }

    /**
     * Extracts project identifiers.
     * @param projects projects to read, may be empty
     * @return identifiers in iteration order
     */
    public static List<String> ids(List<Project> projects) {
        List<String> result = new ArrayList<>();
        for (Project project : projects) result.add(project.id);
        return result;
    }
}
