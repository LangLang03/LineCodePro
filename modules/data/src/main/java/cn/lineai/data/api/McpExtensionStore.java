package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.McpExtension;

/**
 * CRUD and discovery for custom MCP servers.
 *
 * <p>The store persists connection data and the last known tool inventory; it does not perform
 * network discovery itself. Implementations are synchronous; callers own the scheduler.</p>
 */
public interface McpExtensionStore {

    /**
     * Lists all configured MCP servers regardless of enabled state, newest update first.
     *
     * @return every stored server configuration
     */
    List<McpExtension> list();

    /**
     * Inserts or updates one server configuration, assigning an identifier and timestamps when
     * missing.
     *
     * @param input configuration to persist; an empty identifier requests insertion
     * @return the stored form of the configuration including generated identity fields
     */
    McpExtension save(McpExtension input);

    /**
     * Toggles the enabled flag without rewriting the configuration.
     *
     * @param id server identifier; unknown identifiers are ignored
     * @param enabled whether the server may be contacted
     */
    void setEnabled(String id, boolean enabled);

    /**
     * Removes the server configuration with the given identifier.
     *
     * @param id server identifier; unknown identifiers are ignored
     */
    void delete(String id);
}
