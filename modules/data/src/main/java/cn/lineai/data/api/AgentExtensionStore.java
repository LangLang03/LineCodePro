package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.AgentExtension;

/**
 * CRUD for user-defined agent extensions.
 *
 * <p>An agent extension bundles a prompt, a trigger and an allowed tool list; the store persists
 * them but does not interpret them. Implementations are synchronous; callers own the scheduler.</p>
 */
public interface AgentExtensionStore {

    /**
     * Lists all agent extensions regardless of enabled state, newest update first.
     *
     * @return every stored extension
     */
    List<AgentExtension> list();

    /**
     * Inserts or updates one extension, assigning an identifier and timestamps when missing.
     *
     * @param input extension to persist; an empty identifier requests insertion
     * @return the stored form of the extension including generated identity fields
     */
    AgentExtension save(AgentExtension input);

    /**
     * Toggles the enabled flag without rewriting the extension body.
     *
     * @param id extension identifier; unknown identifiers are ignored
     * @param enabled whether the extension may run
     */
    void setEnabled(String id, boolean enabled);

    /**
     * Removes the extension with the given identifier.
     *
     * @param id extension identifier; unknown identifiers are ignored
     */
    void delete(String id);
}
