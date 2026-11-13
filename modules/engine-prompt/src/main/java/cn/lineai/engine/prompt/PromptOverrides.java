package cn.lineai.engine.prompt;

/** Persistence hook for user-edited templates. */
public interface PromptOverrides {

    /**
     * Returns the stored override for a key.
     *
     * @param id override key
     * @param fallback value returned when no override is stored
     * @return stored value, or {@code fallback} when none exists
     */
    String get(String id, String fallback);

    /**
     * Stores an override, replacing any previous value for the key.
     *
     * @param id override key
     * @param value value to store
     */
    void put(String id, String value);

    /**
     * Removes the override for a key, restoring the packaged default.
     *
     * @param id override key
     */
    void remove(String id);

    /** Returns an override store that ignores every write and always yields the fallback. */
    static PromptOverrides none() {
        return new PromptOverrides() {
            @Override public String get(String id, String fallback) { return fallback; }
            @Override public void put(String id, String value) { }
            @Override public void remove(String id) { }
        };
    }
}
