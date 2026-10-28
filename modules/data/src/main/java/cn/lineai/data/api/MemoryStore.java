package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.MemoryItem;

/** Long-term memories grouped by scope. */
public interface MemoryStore {

    List<MemoryItem> list(String projectId);

    void save(MemoryItem memory);

    void delete(String id);

    void deleteAll(List<String> ids);
}
