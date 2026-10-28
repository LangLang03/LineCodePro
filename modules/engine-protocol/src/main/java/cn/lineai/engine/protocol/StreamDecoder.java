package cn.lineai.engine.protocol;

/** Per-stream payload decoder; implementations may keep minimal assembly state. */
public interface StreamDecoder {

    StreamEvent decode(String payload);
}
