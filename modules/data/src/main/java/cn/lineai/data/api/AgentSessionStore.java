package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.AgentSessionRecord;

/**
 * Persistence of sub agent sessions. Implementations are synchronous; callers own the
 * scheduler.
 */
public interface AgentSessionStore {

    /**
     * Inserts or replaces one session record.
     *
     * @param record the record to persist
     */
    void save(AgentSessionRecord record);

    /**
     * Lists every persisted session, oldest first.
     *
     * @return all session records
     */
    List<AgentSessionRecord> list();

    /**
     * Returns one session by id.
     *
     * @param id session id
     * @return the record, or null when unknown
     */
    AgentSessionRecord find(String id);

    /**
     * Deletes one session record; the transcript conversation is left untouched.
     *
     * @param id session id
     */
    void delete(String id);
}
