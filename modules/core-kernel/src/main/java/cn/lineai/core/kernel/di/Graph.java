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
        /** Creates the instance; further dependencies may be resolved from {@code graph}. */
        T create(Graph graph);
    }

    /** Declares bindings for one layer. */
    public interface Module {
        /** Registers this module's bindings on the builder under construction. */
        void install(Builder builder);
    }

    /** Raised when a type has no binding. Carries the lookup path. */
    public static final class MissingBindingException extends RuntimeException {
        /** Creates the exception with a message describing the failed lookup. */
        public MissingBindingException(String message) {
            super(message);
        }
    }

    /** Raised when a factory re-enters itself while being created. */
    public static final class CircularDependencyException extends RuntimeException {
        /** Creates the exception with a message describing the resolution cycle. */
        public CircularDependencyException(String message) {
            super(message);
        }
    }

    private static final ThreadLocal<Deque<Class<?>>> RESOLVING = new ThreadLocal<>();

    private final Graph parent;
    private final Map<Class<?>, Binding<?>> bindings = new HashMap<>();
    private final List<Object> owned = new ArrayList<>();
    private boolean closed;

    /** Creates a graph with the builder's bindings and the given parent. */
    private Graph(Graph parent, Builder builder) {
        this.parent = parent;
        this.bindings.putAll(builder.bindings);
    }

    /**
     * Resolves an instance for {@code type}, walking this graph and its ancestors.
     *
     * <p>Singleton bindings are created on first use by the graph that declares them; the instance
     * is cached with double-checked locking, so factories must be thread-safe or reached from a
     * single thread.</p>
     *
     * @param type binding key; must not be null
     * @param <T> requested instance type
     * @return an instance produced by the owning binding
     * @throws IllegalArgumentException when {@code type} is null
     * @throws CircularDependencyException when resolution re-enters a type already on the thread's
     *         resolution stack
     * @throws MissingBindingException when neither this graph nor any ancestor binds the type
     */
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

    /**
     * Returns {@code true} when the type can be resolved from this graph or any ancestor.
     *
     * <p>Lookup only, so it never creates an instance.</p>
     */
    public <T> boolean has(Class<T> type) {
        return ownerOf(type) != null;
    }

    /**
     * Creates a child graph. Closing the child releases only the objects it owns.
     *
     * @param module bindings to install on the child; may be null for a pass-through child
     * @return the child graph, with this graph as parent
     */
    public Graph newChild(Module module) {
        Builder builder = new Builder();
        if (module != null) {
            module.install(builder);
        }
        return new Graph(this, builder);
    }

    /**
     * Closes owned instances in reverse creation order.
     *
     * <p>Only {@link AutoCloseable} instances are closed; close failures are reported to standard
     * error and do not abort the remaining closes. Repeated calls do nothing.</p>
     */
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

    /** Finds the nearest graph (self or ancestor) that binds the type, or null when unbound. */
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

    /** Returns the thread-local resolution stack, creating it on first use. */
    private static Deque<Class<?>> stack() {
        Deque<Class<?>> stack = RESOLVING.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            RESOLVING.set(stack);
        }
        return stack;
    }

    /** Renders a cycle as {@code A -> B -> A} using simple class names. */
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

    /** Builds the diagnostic message for a missing binding, including the requiring path. */
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

    /** Binding strategy for one type. */
    private abstract static class Binding<T> {
        /** Produces the instance, caching it on {@code graph} where the strategy requires. */
        abstract T get(Graph graph);
    }

    /** Lazily created instance, cached on the owning graph and closed together with it. */
    private static final class SingletonBinding<T> extends Binding<T> {
        private final Factory<T> factory;
        private volatile T instance;

        SingletonBinding(Factory<T> factory) {
            this.factory = factory;
        }

        /** Returns the cached instance, creating it under the binding monitor on first use. */
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

    /** Produces a fresh instance on every lookup. */
    private static final class PrototypeBinding<T> extends Binding<T> {
        private final Factory<T> factory;

        PrototypeBinding(Factory<T> factory) {
            this.factory = factory;
        }

        /** Delegates to the factory without caching. */
        @Override
        T get(Graph graph) {
            return factory.create(graph);
        }
    }

    /** Returns one fixed value for every lookup. */
    private static final class InstanceBinding<T> extends Binding<T> {
        private final T value;

        InstanceBinding(T value) {
            this.value = value;
        }

        /** Returns the pre-built value. */
        @Override
        T get(Graph graph) {
            return value;
        }
    }

    /** Collects bindings and produces a root graph. */
    public static final class Builder {
        private final Map<Class<?>, Binding<?>> bindings = new HashMap<>();

        /**
         * Binds a pre-built value.
         *
         * @param type binding key
         * @param value instance returned for every lookup
         * @param <T> instance type
         * @return this builder
         */
        public <T> Builder instance(Class<T> type, T value) {
            bindings.put(type, new InstanceBinding<>(value));
            return this;
        }

        /**
         * Binds a lazily created, cached and graph-owned instance.
         *
         * @param type binding key
         * @param factory creator invoked once, on first resolution
         * @param <T> instance type
         * @return this builder
         */
        public <T> Builder singleton(Class<T> type, Factory<T> factory) {
            bindings.put(type, new SingletonBinding<>(factory));
            return this;
        }

        /**
         * Binds a factory that produces a fresh instance per lookup.
         *
         * @param type binding key
         * @param factory creator invoked on every resolution
         * @param <T> instance type
         * @return this builder
         */
        public <T> Builder factory(Class<T> type, Factory<T> factory) {
            bindings.put(type, new PrototypeBinding<>(factory));
            return this;
        }

        /** Creates the root graph; root graphs have no parent. */
        public Graph build() {
            return new Graph(null, this);
        }
    }
}
