package cn.lineai.core.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable, validated user preferences; unknown stored values fall back to documented defaults. */
public final class UiPreferences {
    /** A single preference: storage key, accepted values and fallback. */
    public enum Field {
        ENTER_BEHAVIOR("input.enterKeyBehavior", "send", "newline"),
        CODE_WRAP("output.codeWrap", "false", "true"),
        PROCESS_AUTO_EXPAND("output.processAutoExpand", "false", "true"),
        BROWSER_MODE("output.browserMode", "builtin", "external"),
        TONE("ai.tone", "coding", "chat"),
        REASONING("ai.reasoning", "medium", "off", "auto", "low", "high", "max"),
        THINKING_SCROLL("ai.thinkingScroll", "true", "false"),
        THINKING_AUTO_EXPAND("ai.thinkingAutoExpand", "false", "true"),
        PRESERVE_REASONING("ai.preserveReasoning", "false", "true"),
        LEARNING_MODE("ai.learningMode", "false", "true"),
        SOFT_COMPACTION("ai.softCompaction", "true", "false"),
        MINIMAL_MODE("ai.minimalMode", "false", "true"),
        ALLOW_ANY_HTTP("output.allowAnyHttp", "false", "true"),
        BROWSER_JAVASCRIPT("output.browserJavaScript", "false", "true"),
        BYPASS_PATH_PROTECTION("output.bypassPathProtection", "false", "true"),
        TRUST_EXTENSION_SIGNATURES("security.trustExtensionSignatures", "false", "true"),
        KEEP_ALIVE_WAKE_LOCK("keepAlive.wakeLock", "false", "true"),
        KEEP_ALIVE_FOREGROUND("keepAlive.foreground", "false", "true"),
        KEEP_ALIVE_FAKE_AUDIO("keepAlive.fakeAudio", "false", "true");

        public final String key;
        public final String fallback;
        private final java.util.List<String> allowed;

        /**
         * Defines one preference.
         * @param key storage key
         * @param fallback value used when nothing valid is stored
         * @param alternatives further accepted values after the fallback
         */
        Field(String key, String fallback, String... alternatives) {
            this.key = key;
            this.fallback = fallback;
            java.util.List<String> values = new java.util.ArrayList<>();
            values.add(fallback);
            values.addAll(Arrays.asList(alternatives));
            this.allowed = Collections.unmodifiableList(values);
        }

        /**
         * Returns whether {@code value} is accepted for this field.
         * @param value candidate value, may be null
         * @return true when the value is one of the accepted values
         */
        public boolean accepts(String value) {
            return value != null && allowed.contains(value);
        }
    }

    private final Map<Field, String> values;

    /** Creates an instance owning an unmodifiable copy of {@code values}. */
    private UiPreferences(Map<Field, String> values) {
        this.values = Collections.unmodifiableMap(new EnumMap<>(values));
    }

    /**
     * Validates stored raw values into a preferences instance.
     * @param stored stored key/value pairs, may be null entries or missing keys
     * @return an instance where every field holds an accepted value
     */
    public static UiPreferences from(Map<String, String> stored) {
        Map<Field, String> values = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            String value = stored.get(field.key);
            values.put(field, field.accepts(value) ? value : field.fallback);
        }
        return new UiPreferences(values);
    }

    /** Returns the instance holding every field's fallback value. */
    public static UiPreferences defaults() {
        return from(Collections.emptyMap());
    }

    /**
     * Returns the stored value for {@code field}.
     * @param field preference to read
     * @return the accepted value currently associated with the field
     */
    public String get(Field field) {
        return values.get(field);
    }

    /**
     * Returns a copy with one field replaced.
     * @param field preference to change
     * @param value new value, must be accepted by the field
     * @return a new immutable instance
     * @throws IllegalArgumentException when the field does not accept the value
     */
    public UiPreferences with(Field field, String value) {
        if (!field.accepts(value)) throw new IllegalArgumentException("Invalid preference: " + field);
        Map<Field, String> next = new EnumMap<>(values);
        next.put(field, value);
        return new UiPreferences(next);
    }

    /** Returns whether Enter sends the message instead of inserting a newline. */
    public boolean enterSends() { return "send".equals(get(Field.ENTER_BEHAVIOR)); }
    /** Returns whether code blocks wrap long lines. */
    public boolean codeWrap() { return booleanOf(Field.CODE_WRAP); }
    /** Returns whether running-process output auto-expands. */
    public boolean processAutoExpand() { return booleanOf(Field.PROCESS_AUTO_EXPAND); }
    /** Returns whether links open in an external browser. */
    public boolean externalBrowser() { return "external".equals(get(Field.BROWSER_MODE)); }
    /** Returns whether reasoning panes auto-scroll while streaming. */
    public boolean thinkingScroll() { return booleanOf(Field.THINKING_SCROLL); }
    /** Returns whether reasoning panes start expanded. */
    public boolean thinkingAutoExpand() { return booleanOf(Field.THINKING_AUTO_EXPAND); }
    /** Returns whether reasoning survives into follow-up requests. */
    public boolean preserveReasoning() { return booleanOf(Field.PRESERVE_REASONING); }
    /** Returns whether learning-mode explanations are enabled. */
    public boolean learningMode() { return booleanOf(Field.LEARNING_MODE); }
    /** Returns whether soft context compaction is enabled. */
    public boolean softCompaction() { return booleanOf(Field.SOFT_COMPACTION); }
    /** Returns whether the minimal, low-chrome UI mode is enabled. */
    public boolean minimalMode() { return booleanOf(Field.MINIMAL_MODE); }
    /** Returns whether plain HTTP to arbitrary hosts is permitted. */
    public boolean allowAnyHttp() { return booleanOf(Field.ALLOW_ANY_HTTP); }
    /** Returns whether the built-in browser executes JavaScript. */
    public boolean browserJavaScript() { return booleanOf(Field.BROWSER_JAVASCRIPT); }
    /** Returns whether workspace path protection may be bypassed. */
    public boolean bypassPathProtection() { return booleanOf(Field.BYPASS_PATH_PROTECTION); }
    /** Returns whether extension signature checks are relaxed. */
    public boolean trustExtensionSignatures() { return booleanOf(Field.TRUST_EXTENSION_SIGNATURES); }
    /** Returns whether the wake-lock keep-alive strategy is enabled. */
    public boolean keepAliveWakeLock() { return booleanOf(Field.KEEP_ALIVE_WAKE_LOCK); }
    /** Returns whether the foreground-service keep-alive strategy is enabled. */
    public boolean keepAliveForeground() { return booleanOf(Field.KEEP_ALIVE_FOREGROUND); }
    /** Returns whether the silent-audio keep-alive strategy is enabled. */
    public boolean keepAliveFakeAudio() { return booleanOf(Field.KEEP_ALIVE_FAKE_AUDIO); }

    /** Interprets the stored value of {@code field} as a boolean. */
    private boolean booleanOf(Field field) {
        return Boolean.parseBoolean(get(field));
    }

    /** Preferences are equal when every field value matches. */
    @Override public boolean equals(Object other) {
        return other instanceof UiPreferences && values.equals(((UiPreferences) other).values);
    }

    /** Hashes the field-value map. */
    @Override public int hashCode() { return values.hashCode(); }
}
