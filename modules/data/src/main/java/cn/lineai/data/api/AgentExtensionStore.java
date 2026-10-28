package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.AgentExtension;

/** CRUD for user-defined agent extensions. */
public interface AgentExtensionStore {

    List<AgentExtension> list();

    AgentExtension save(AgentExtension input);

    void setEnabled(String id, boolean enabled);

    void delete(String id);
}
