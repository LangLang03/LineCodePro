package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.MemoryItem;

/**
 * Long-term memories grouped by scope.
 *
 * <p>A memory is either user scoped or project scoped; project scope memories carry the project
 * identifier. Implementations are synchronous; callers own the scheduler.</p>
 */
public interface MemoryStore {

    /**
     * Lists the memories visible in one project context: every non-project memory plus the
     * project-scoped memories of the given project, newest update first.
     *
     * @param projectId project whose scoped memories are included; empty selects no scoped memories
     * @return the visible memories for the context
     */
    List<MemoryItem> list(String projectId);

    /**
     * Inserts or replaces one memory, generating an identifier and updated timestamp when
     * inserting.
     *
     * @param memory memory to persist; an empty identifier requests insertion
     */
    void save(MemoryItem memory);

    /** Updates scope/content of one memory, preserving its other fields. */
    void update(String id, String scope, String content);

    /**
     * Deletes one memory.
     *
     * @param id memory identifier; unknown identifiers are ignored
     */
    void delete(String id);

    /**
     * Deletes several memories in one transaction so the removal is all-or-nothing.
     *
     * @param ids memory identifiers; null or empty leaves the store untouched
     */
    void deleteAll(List<String> ids);
}
