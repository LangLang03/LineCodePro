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

    public Project(String id, String name, String path, boolean selected,
                   long createdAt, long updatedAt) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.selected = selected;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Project selected(boolean value) {
        return new Project(id, name, path, value, createdAt, updatedAt);
    }

    public static List<String> ids(List<Project> projects) {
        List<String> result = new ArrayList<>();
        for (Project project : projects) result.add(project.id);
        return result;
    }
}
