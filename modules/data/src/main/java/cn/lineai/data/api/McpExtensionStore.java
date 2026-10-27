package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.McpExtension;

/** CRUD and discovery for custom MCP servers. */
public interface McpExtensionStore {

    List<McpExtension> list();

    McpExtension save(McpExtension input);

    void setEnabled(String id, boolean enabled);

    void delete(String id);
}
