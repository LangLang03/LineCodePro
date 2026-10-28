package cn.lineai.core.mvi.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Implementations must be immutable and encode all identity-bearing fields. */
public interface Params {
    Args toArgs();

    enum Empty implements Params {
        INSTANCE;

        @Override
        public Args toArgs() {
            return Args.empty();
        }

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

        public Args(Map<String, ?> values) {
            this.values = freezeMap(Objects.requireNonNull(values, "values"));
        }

        public static Args empty() {
            return new Args(Collections.emptyMap());
        }

        public static Args of(String key, Object value) {
            return new Args(Collections.singletonMap(
                    Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")));
        }

        @Override
        public Args toArgs() {
            return this;
        }

        public Map<String, Object> asMap() {
            return values;
        }

        public Object get(String key) {
            return values.get(Objects.requireNonNull(key, "key"));
        }

        public String requireString(String key) {
            Object value = get(key);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Expected string argument: " + key);
            }
            return (String) value;
        }

        public Args requireArgs(String key) {
            Object value = get(key);
            if (!(value instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Expected map argument: " + key);
            }
            return new Args(freezeMap((Map<?, ?>) value));
        }

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

        @Override
        public boolean equals(Object other) {
            return other instanceof Args && values.equals(((Args) other).values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }
}
