package cn.lineai.engine.prompt;

/** Persistence hook for user-edited templates. */
public interface PromptOverrides {

    String get(String id, String fallback);

    void put(String id, String value);

    void remove(String id);

    static PromptOverrides none() {
        return new PromptOverrides() {
            @Override public String get(String id, String fallback) { return fallback; }
            @Override public void put(String id, String value) { }
            @Override public void remove(String id) { }
        };
    }
}
