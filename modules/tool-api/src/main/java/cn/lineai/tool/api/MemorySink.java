package cn.lineai.tool.api;

/** Persists one durable memory entry without exposing the data layer to tools. */
public interface MemorySink {

    void save(String scope, String title, String content);
}
