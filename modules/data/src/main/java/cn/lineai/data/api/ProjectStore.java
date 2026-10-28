package cn.lineai.data.api;

import java.io.File;
import java.util.List;

import cn.lineai.core.model.Project;

/** Workspace projects and the currently selected one. */
public interface ProjectStore {

    List<Project> list();

    Project selected();

    Project ensureDefault(File directory);

    Project create(String name, String path);

    void select(String id);

    void delete(String id);
}
