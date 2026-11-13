package cn.lineai.tool.api;

/**
 * Persists one durable memory entry without exposing the data layer to tools.
 *
 * <p>The sink is deliberately narrow: tools describe what to remember and the host decides
 * storage, scoping and retention.</p>
 */
public interface MemorySink {

    /**
     * Saves one durable memory entry.
     *
     * @param scope memory namespace, e.g. user preferences versus project facts
     * @param title short human-readable label for the entry
     * @param content the remembered text
     */
    void save(String scope, String title, String content);
}
