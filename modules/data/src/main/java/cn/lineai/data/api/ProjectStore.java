package cn.lineai.data.api;

import java.io.File;
import java.util.List;

import cn.lineai.core.model.Project;

/**
 * Workspace projects and the currently selected one.
 *
 * <p>Exactly one project is selected at any time; selection is a transactional swap so no
 * intermediate state has zero or two selected projects. Implementations are synchronous; callers
 * own the scheduler.</p>
 */
public interface ProjectStore {

    /**
     * Lists the known projects, newest update first.
     *
     * @return every stored project
     */
    List<Project> list();

    /**
     * Returns the currently selected project.
     *
     * @return the selected project or null when none is selected
     */
    Project selected();

    /**
     * Guarantees a usable selection: keeps the current selection, or selects the first existing
     * project, or creates the default project bound to the given directory.
     *
     * @param directory workspace directory used when the default project must be created
     * @return the project that is selected after the call
     */
    Project ensureDefault(File directory);

    /**
     * Creates a project and selects it.
     *
     * @param name display name; blank names are rejected
     * @param path workspace directory path
     * @return the created project, marked selected
     */
    Project create(String name, String path);

    /**
     * Makes the given project the single selected one.
     *
     * @param id project identifier; unknown identifiers are ignored
     */
    void select(String id);

    /**
     * Deletes a project. The built-in default project cannot be deleted; deleting the selected
     * project selects the most recently updated remaining one.
     *
     * @param id project identifier; unknown identifiers are ignored
     */
    void delete(String id);
}
