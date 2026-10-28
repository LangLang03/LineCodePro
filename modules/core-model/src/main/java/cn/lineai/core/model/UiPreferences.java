package cn.lineai.core.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class UiPreferences {
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
        KEEP_ALIVE_WAKE_LOCK("keepAlive.wakeLock", "false", "true"),
        KEEP_ALIVE_FOREGROUND("keepAlive.foreground", "false", "true"),
        KEEP_ALIVE_FAKE_AUDIO("keepAlive.fakeAudio", "false", "true");

        public final String key;
        public final String fallback;
        private final java.util.List<String> allowed;

        Field(String key, String fallback, String... alternatives) {
            this.key = key;
            this.fallback = fallback;
            java.util.List<String> values = new java.util.ArrayList<>();
            values.add(fallback);
            values.addAll(Arrays.asList(alternatives));
            this.allowed = Collections.unmodifiableList(values);
        }

        public boolean accepts(String value) {
            return value != null && allowed.contains(value);
        }
    }

    private final Map<Field, String> values;

    private UiPreferences(Map<Field, String> values) {
        this.values = Collections.unmodifiableMap(new EnumMap<>(values));
    }

    public static UiPreferences from(Map<String, String> stored) {
        Map<Field, String> values = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            String value = stored.get(field.key);
            values.put(field, field.accepts(value) ? value : field.fallback);
        }
        return new UiPreferences(values);
    }

    public static UiPreferences defaults() {
        return from(Collections.emptyMap());
    }

    public String get(Field field) {
        return values.get(field);
    }

    public UiPreferences with(Field field, String value) {
        if (!field.accepts(value)) throw new IllegalArgumentException("Invalid preference: " + field);
        Map<Field, String> next = new EnumMap<>(values);
        next.put(field, value);
        return new UiPreferences(next);
    }

    public boolean enterSends() { return "send".equals(get(Field.ENTER_BEHAVIOR)); }
    public boolean codeWrap() { return booleanOf(Field.CODE_WRAP); }
    public boolean processAutoExpand() { return booleanOf(Field.PROCESS_AUTO_EXPAND); }
    public boolean externalBrowser() { return "external".equals(get(Field.BROWSER_MODE)); }
    public boolean thinkingScroll() { return booleanOf(Field.THINKING_SCROLL); }
    public boolean thinkingAutoExpand() { return booleanOf(Field.THINKING_AUTO_EXPAND); }
    public boolean preserveReasoning() { return booleanOf(Field.PRESERVE_REASONING); }
    public boolean learningMode() { return booleanOf(Field.LEARNING_MODE); }
    public boolean softCompaction() { return booleanOf(Field.SOFT_COMPACTION); }
    public boolean minimalMode() { return booleanOf(Field.MINIMAL_MODE); }
    public boolean allowAnyHttp() { return booleanOf(Field.ALLOW_ANY_HTTP); }
    public boolean browserJavaScript() { return booleanOf(Field.BROWSER_JAVASCRIPT); }
    public boolean bypassPathProtection() { return booleanOf(Field.BYPASS_PATH_PROTECTION); }
    public boolean keepAliveWakeLock() { return booleanOf(Field.KEEP_ALIVE_WAKE_LOCK); }
    public boolean keepAliveForeground() { return booleanOf(Field.KEEP_ALIVE_FOREGROUND); }
    public boolean keepAliveFakeAudio() { return booleanOf(Field.KEEP_ALIVE_FAKE_AUDIO); }

    private boolean booleanOf(Field field) {
        return Boolean.parseBoolean(get(field));
    }

    @Override public boolean equals(Object other) {
        return other instanceof UiPreferences && values.equals(((UiPreferences) other).values);
    }

    @Override public int hashCode() { return values.hashCode(); }
}
