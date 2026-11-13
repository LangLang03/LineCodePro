package cn.lineai.ipc;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Facade over the registered IPC providers (facade plus observer aggregation).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Register and unregister providers</li>
 *   <li>Drive {@link BaseIpcProvider#bind} / {@link BaseIpcProvider#unbind}</li>
 *   <li>Look up bound providers by id or type</li>
 *   <li>Aggregate provider state changes and republish them to global listeners</li>
 * </ul>
 *
 * <p>Providers are created by the {@link IpcProviderFactory} instances held in
 * {@link IpcProviderRegistry}; new provider types require no change to this class.
 */
public final class IpcProviderManager {

    private final Map<String, BaseIpcProvider> activeProviders = new ConcurrentHashMap<>();
    private final List<IpcProviderStateListener> globalListeners = new CopyOnWriteArrayList<>();
    private final Context context;
    private final IpcProviderRegistry registry;

    /**
     * Creates a manager backed by the default {@link IpcProviderRegistry}.
     *
     * @param context context used for binding; only the application context is retained
     */
    public IpcProviderManager(Context context) {
        this(context, IpcProviderRegistry.getInstance());
    }

    /**
     * Creates a manager backed by an explicit registry, which allows hosts to use an
     * isolated factory set. A null registry falls back to the default singleton.
     *
     * @param context context used for binding; only the application context is retained
     * @param registry the factory registry to resolve provider types against
     */
    public IpcProviderManager(Context context, IpcProviderRegistry registry) {
        this.context = context.getApplicationContext();
        this.registry = registry == null ? IpcProviderRegistry.getInstance() : registry;
    }

    /**
     * Registers a provider and binds it.
     *
     * <p>When a provider with the same id already exists, the old instance is unbound
     * first and then replaced by the newly created one.
     */
    public BaseIpcProvider registerAndBind(IpcProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        BaseIpcProvider existing = activeProviders.get(config.getId());
        if (existing != null) {
            existing.unbind(context);
            existing.removeStateListener(providerStateForwarder);
        }
        BaseIpcProvider provider = createProvider(config);
        if (provider == null) {
            throw new IllegalStateException("no provider factory for type: " + config.getProviderType());
        }
        provider.addStateListener(providerStateForwarder);
        provider.bind(context);
        activeProviders.put(config.getId(), provider);
        return provider;
    }

    /** Unregisters and unbinds the provider with the given id. */
    public void unregisterAndUnbind(String providerId) {
        BaseIpcProvider provider = activeProviders.remove(providerId);
        if (provider != null) {
            provider.removeStateListener(providerStateForwarder);
            provider.unbind(context);
        }
    }

    /** Unregisters and unbinds every active provider. */
    public void unregisterAll() {
        for (BaseIpcProvider provider : new ArrayList<>(activeProviders.values())) {
            provider.removeStateListener(providerStateForwarder);
            provider.unbind(context);
        }
        activeProviders.clear();
    }

    /** Returns the registered provider for the id, or null when its type differs. */
    public <T extends BaseIpcProvider> T getProvider(String providerId, Class<T> type) {
        BaseIpcProvider provider = activeProviders.get(providerId);
        if (provider == null || !type.isInstance(provider)) {
            return null;
        }
        return type.cast(provider);
    }

    /** Returns the first bound provider of the type, or null. */
    public BaseIpcProvider getProviderByType(IpcProviderType type) {
        if (type == null) {
            return null;
        }
        for (BaseIpcProvider provider : activeProviders.values()) {
            if (provider.getProviderType() == type && provider.isBound()) {
                return provider;
            }
        }
        return null;
    }

    /** Returns every registered provider of the type. */
    public List<BaseIpcProvider> getProvidersByType(IpcProviderType type) {
        List<BaseIpcProvider> result = new ArrayList<>();
        if (type == null) {
            return result;
        }
        for (BaseIpcProvider provider : activeProviders.values()) {
            if (provider.getProviderType() == type) {
                result.add(provider);
            }
        }
        return result;
    }

    /** Registers a global listener; every provider state change is forwarded to it. */
    public void addStateListener(IpcProviderStateListener listener) {
        if (listener != null) {
            globalListeners.add(listener);
        }
    }

    /** Removes a previously registered global listener. */
    public void removeStateListener(IpcProviderStateListener listener) {
        if (listener != null) {
            globalListeners.remove(listener);
        }
    }

    /** Forwards every provider transition to the global listeners, isolating listener failures. */
    private final IpcProviderStateListener providerStateForwarder = (provider, newState, cause) -> {
        for (IpcProviderStateListener listener : globalListeners) {
            try {
                listener.onStateChanged(provider, newState, cause);
            } catch (RuntimeException brokenListener) {
                android.util.Log.w("IpcProviderManager", "state listener threw", brokenListener);
            }
        }
    };

    /**
     * Creates a provider instance through the registry; returns null when no factory is
     * registered for the type (open/closed principle, no type-specific branches here).
     */
    private BaseIpcProvider createProvider(IpcProviderConfig config) {
        IpcProviderType type = IpcProviderType.fromId(config.getProviderType());
        IpcProviderFactory factory = registry.get(type);
        if (factory == null) {
            return null;
        }
        return factory.create(config);
    }
}
