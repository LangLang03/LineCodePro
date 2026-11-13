package cn.lineai.tool.api;

/**
 * Supplies the current tool set; rebuilt when extensions change.
 *
 * <p>Consumers query the catalog per session instead of caching a registry, so installed
 * extensions and workspace changes are reflected without restart.</p>
 */
public interface ToolCatalog {

    /**
     * Returns the registry matching the currently installed extensions and active workspace.
     *
     * @return the current name-indexed tool set
     */
    ToolRegistry registry();
}
