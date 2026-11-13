package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.MemoryItem;

/** Long-term memories grouped by scope. */
public interface MemoryStore {

    List<MemoryItem> list(String projectId);

    void save(MemoryItem memory);

    /** Updates scope/content of one memory, preserving its other fields. */
    void update(String id, String scope, String content);

    void delete(String id);

    void deleteAll(List<String> ids);
}
