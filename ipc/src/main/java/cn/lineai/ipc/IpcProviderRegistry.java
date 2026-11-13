package cn.lineai.ipc;

import cn.lineai.ipc.terminal.TerminalProviderServiceFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of IPC provider factories (registry-based discovery).
 *
 * <p>Replaces the {@code switch} embedded in {@code IpcProviderManager}: new provider
 * types register their factory through {@link #getInstance()} from their own module, the
 * manager itself stays untouched (open/closed principle), and registration and lookup are
 * thread-safe via {@link ConcurrentHashMap}.
 */
public final class IpcProviderRegistry {

    private static final IpcProviderRegistry INSTANCE = new IpcProviderRegistry();

    private final Map<IpcProviderType, IpcProviderFactory> factories = new ConcurrentHashMap<>();

    private IpcProviderRegistry() {
        // The factory shipped with this library is always present.
        register(TerminalProviderServiceFactory.getInstance());
    }

    public static IpcProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a factory, replacing any factory already registered for its type.
     *
     * @param factory the factory implementation
     * @throws IllegalArgumentException when the factory is null or declares no type
     */
    public void register(IpcProviderFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        IpcProviderType type = factory.getProviderType();
        if (type == null) {
            throw new IllegalArgumentException("factory.getProviderType() must not be null");
        }
        factories.put(type, factory);
    }

    /** Removes the factory registered for the given type. */
    public void unregister(IpcProviderType type) {
        if (type == null) {
            return;
        }
        factories.remove(type);
    }

    /** Returns the factory for the type, or null when it is not registered. */
    public IpcProviderFactory get(IpcProviderType type) {
        if (type == null) {
            return null;
        }
        return factories.get(type);
    }

    /** Returns an immutable snapshot of the registered provider types. */
    public Set<IpcProviderType> registeredTypes() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(factories.keySet()));
    }

    /** Returns an immutable snapshot of the registered factories. */
    public Map<IpcProviderType, IpcProviderFactory> snapshot() {
        LinkedHashMap<IpcProviderType, IpcProviderFactory> copy = new LinkedHashMap<>(factories);
        return Collections.unmodifiableMap(copy);
    }
}
