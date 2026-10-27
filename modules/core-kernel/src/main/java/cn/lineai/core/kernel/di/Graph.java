package cn.lineai.core.kernel.di;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written dependency graph.
 *
 * <p>Three lifetimes are supported by creating a child graph: application, activity and screen.
 * Lookups walk from the current graph to its parent, so a screen can depend on an application
 * singleton without any global static holder.</p>
 *
 * <p>There is intentionally no {@code inject(Object)} and no mutable global registry: every
 * dependency must be declared by a {@link Module} at the composition root.</p>
 */
public final class Graph {

    /** Creates one instance for a binding. */
    public interface Factory<T> {
        T create(Graph graph);
    }

    /** Declares bindings for one layer. */
    public interface Module {
        void install(Builder builder);
    }

    /** Raised when a type has no binding. Carries the lookup path. */
    public static final class MissingBindingException extends RuntimeException {
        public MissingBindingException(String message) {
            super(message);
        }
    }

    /** Raised when a factory re-enters itself while being created. */
    public static final class CircularDependencyException extends RuntimeException {
        public CircularDependencyException(String message) {
            super(message);
        }
    }

    private static final ThreadLocal<Deque<Class<?>>> RESOLVING = new ThreadLocal<>();

    private final Graph parent;
    private final Map<Class<?>, Binding<?>> bindings = new HashMap<>();
    private final List<Object> owned = new ArrayList<>();
    private boolean closed;

    private Graph(Graph parent, Builder builder) {
        this.parent = parent;
        this.bindings.putAll(builder.bindings);
    }

    public <T> T get(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type == null");
        }
        Deque<Class<?>> stack = stack();
        if (stack.contains(type)) {
            throw new CircularDependencyException("Circular dependency: " + describeCycle(stack, type));
        }
        stack.push(type);
        try {
            Graph owner = ownerOf(type);
            if (owner == null) {
                throw new MissingBindingException(missingMessage(type, stack));
            }
            Binding<?> binding = owner.bindings.get(type);
            return type.cast(binding.get(owner));
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                RESOLVING.remove();
            }
        }
    }

    public <T> boolean has(Class<T> type) {
        return ownerOf(type) != null;
    }

    /** Creates a child graph. Closing the child releases only the objects it owns. */
    public Graph newChild(Module module) {
        Builder builder = new Builder();
        if (module != null) {
            module.install(builder);
        }
        return new Graph(this, builder);
    }

    /** Closes owned instances in reverse creation order. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int i = owned.size() - 1; i >= 0; i--) {
            Object value = owned.get(i);
            if (value instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) value).close();
                } catch (Exception error) {
                    System.err.println("Graph close failed: " + error);
                }
            }
        }
        owned.clear();
        bindings.clear();
    }

    private Graph ownerOf(Class<?> type) {
        Graph cursor = this;
        while (cursor != null) {
            if (cursor.bindings.containsKey(type)) {
                return cursor;
            }
            cursor = cursor.parent;
        }
        return null;
    }

    private static Deque<Class<?>> stack() {
        Deque<Class<?>> stack = RESOLVING.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            RESOLVING.set(stack);
        }
        return stack;
    }

    private static String describeCycle(Deque<Class<?>> stack, Class<?> type) {
        List<String> names = new ArrayList<>();
        boolean started = false;
        for (Class<?> entry : stack) {
            if (entry == type) {
                started = true;
            }
            if (started) {
                names.add(entry.getSimpleName());
            }
        }
        names.add(type.getSimpleName());
        return String.join(" -> ", names);
    }

    private static String missingMessage(Class<?> type, Deque<Class<?>> stack) {
        StringBuilder sb = new StringBuilder("Missing binding: ").append(type.getName());
        if (!stack.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Class<?> entry : stack) {
                if (entry != type) {
                    names.add(entry.getSimpleName());
                }
            }
            if (!names.isEmpty()) {
                sb.append(" (required by ").append(String.join(" <- ", names)).append(')');
            }
        }
        return sb.toString();
    }

    private abstract static class Binding<T> {
        abstract T get(Graph graph);
    }

    private static final class SingletonBinding<T> extends Binding<T> {
        private final Factory<T> factory;
        private volatile T instance;

        SingletonBinding(Factory<T> factory) {
            this.factory = factory;
        }

        @Override
        T get(Graph graph) {
            T local = instance;
            if (local != null) {
                return local;
            }
            synchronized (this) {
                local = instance;
                if (local == null) {
                    local = factory.create(graph);
                    instance = local;
                    if (local != null) {
                        graph.owned.add(local);
                    }
                }
                return local;
            }
        }
    }

    private static final class PrototypeBinding<T> extends Binding<T> {
        private final Factory<T> factory;

        PrototypeBinding(Factory<T> factory) {
            this.factory = factory;
        }

        @Override
        T get(Graph graph) {
            return factory.create(graph);
        }
    }

    private static final class InstanceBinding<T> extends Binding<T> {
        private final T value;

        InstanceBinding(T value) {
            this.value = value;
        }

        @Override
        T get(Graph graph) {
            return value;
        }
    }

    /** Collects bindings and produces a root graph. */
    public static final class Builder {
        private final Map<Class<?>, Binding<?>> bindings = new HashMap<>();

        public <T> Builder instance(Class<T> type, T value) {
            bindings.put(type, new InstanceBinding<>(value));
            return this;
        }

        public <T> Builder singleton(Class<T> type, Factory<T> factory) {
            bindings.put(type, new SingletonBinding<>(factory));
            return this;
        }

        public <T> Builder factory(Class<T> type, Factory<T> factory) {
            bindings.put(type, new PrototypeBinding<>(factory));
            return this;
        }

        public Graph build() {
            return new Graph(null, this);
        }
    }
}
