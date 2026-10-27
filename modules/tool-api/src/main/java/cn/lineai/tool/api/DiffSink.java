package cn.lineai.tool.api;

/** Receives file snapshots from mutating tools. */
public interface DiffSink {

    void record(String path, String before, String after, boolean existedBefore);
}
