package cn.lineai.ai;

public interface ModelStreamCallback {
    void onTextDelta(String delta);

    void onReasoningDelta(String delta);
}
