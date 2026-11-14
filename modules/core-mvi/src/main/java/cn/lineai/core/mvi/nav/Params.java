package cn.lineai.core.mvi.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Implementations must be immutable and encode all identity-bearing fields. */
public interface Params {
    /**
     * Serialises this instance into transport-safe arguments.
     * @return deeply immutable arguments carrying every identity-bearing field
     */
    Args toArgs();

    /** Params for screens that take no arguments. */
    enum Empty implements Params {
        /** The single instance. */
        INSTANCE;

        /** Returns an empty argument set. */
        @Override
        public Args toArgs() {
            return Args.empty();
        }

        /**
         * Decodes empty params, rejecting non-empty arguments.
         * @param args arguments previously produced by {@link #toArgs()}
         * @return {@link #INSTANCE}
         * @throws IllegalArgumentException when {@code args} contains entries
         */
        public static Empty fromArgs(Args args) {
            if (!Objects.requireNonNull(args, "args").asMap().isEmpty()) {
                throw new IllegalArgumentException("Empty params cannot contain arguments");
            }
            return INSTANCE;
        }
    }

    /** Deeply immutable string-keyed maps/lists with scalar values; nulls are rejected. */
    final class Args implements Params {
        private final Map<String, Object> values;

        /**
         * Creates an argument set by deep-freezing {@code values}.
         * @param values entries to freeze; keys must be strings and values must be supported types
         */
        public Args(Map<String, ?> values) {
            this.values = freezeMap(Objects.requireNonNull(values, "values"));
        }

        /** Returns an argument set with no entries. */
        public static Args empty() {
            return new Args(Collections.emptyMap());
        }

        /**
         * Creates an argument set with a single entry.
         * @param key entry key, never null
         * @param value entry value, never null and of a supported type
         * @return a one-entry argument set
         */
        public static Args of(String key, Object value) {
            return new Args(Collections.singletonMap(
                    Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")));
        }

        /** Returns this instance; args are already serialised. */
        @Override
        public Args toArgs() {
            return this;
        }

        /** Returns the frozen entries; callers must not mutate the returned map. */
        public Map<String, Object> asMap() {
            return values;
        }

        /**
         * Reads an entry without a type check.
         * @param key entry key, never null
         * @return the associated value, or null when absent
         */
        public Object get(String key) {
            return values.get(Objects.requireNonNull(key, "key"));
        }

        /**
         * Reads a string entry.
         * @param key entry key, never null
         * @return the associated string value
         * @throws IllegalArgumentException when the entry is missing or not a string
         */
        public String requireString(String key) {
            Object value = get(key);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Expected string argument: " + key);
            }
            return (String) value;
        }

        /**
         * Reads a nested argument set.
         * @param key entry key, never null
         * @return the associated map as a frozen {@code Args} value
         * @throws IllegalArgumentException when the entry is missing or not a map
         */
        public Args requireArgs(String key) {
            Object value = get(key);
            if (!(value instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Expected map argument: " + key);
            }
            return new Args(freezeMap((Map<?, ?>) value));
        }

        /** Validates string keys and deep-freezes every entry of {@code source}. */
        private static Map<String, Object> freezeMap(Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("Argument keys must be strings");
                }
                result.put((String) entry.getKey(), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }

        /** Returns a frozen scalar or container; unsupported types are rejected. */
        private static Object freeze(Object value) {
            Objects.requireNonNull(value, "argument value");
            if (value instanceof String || value instanceof Boolean || value instanceof Byte
                    || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double) {
                return value;
            }
            if (value instanceof Args) {
                return ((Args) value).asMap();
            }
            if (value instanceof Map<?, ?>) {
                return freezeMap((Map<?, ?>) value);
            }
            if (value instanceof List<?>) {
                List<Object> result = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    result.add(freeze(item));
                }
                return Collections.unmodifiableList(result);
            }
            throw new IllegalArgumentException("Unsupported argument type: " + value.getClass().getName());
        }

        /** Equality is the frozen entry map. */
        @Override
        public boolean equals(Object other) {
            return other instanceof Args && values.equals(((Args) other).values);
        }

        /** Hashes the frozen entry map. */
        @Override
        public int hashCode() {
            return values.hashCode();
        }

        /** Renders the frozen entry map for diagnostics. */
        @Override
        public String toString() {
            return values.toString();
        }
    }
}
