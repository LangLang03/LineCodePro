package cn.lineai.ipc;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * IPC Provider 管理器（外观模式 + 观察者聚合）。
 *
 * <p>负责：
 * <ul>
 *   <li>注册 / 注销 Provider</li>
 *   <li>触发 {@link BaseIpcProvider#bind} / {@link BaseIpcProvider#unbind}</li>
 *   <li>按 id / 类型查找已绑定 Provider</li>
 *   <li>聚合所有 Provider 的状态变化并对外发布（观察者）</li>
 * </ul>
 *
 * <p>Provider 实例通过 {@link IpcProviderRegistry} 注册的 {@link IpcProviderFactory} 创建，
 * 遵守 OCP：新增类型无需修改本类。
 */
public final class IpcProviderManager {

    private final Map<String, BaseIpcProvider> activeProviders = new ConcurrentHashMap<>();
    private final List<IpcProviderStateListener> globalListeners = new CopyOnWriteArrayList<>();
    private final Context context;
    private final IpcProviderRegistry registry;

    public IpcProviderManager(Context context) {
        this(context, IpcProviderRegistry.getInstance());
    }

    public IpcProviderManager(Context context, IpcProviderRegistry registry) {
        this.context = context.getApplicationContext();
        this.registry = registry == null ? IpcProviderRegistry.getInstance() : registry;
    }

    /**
     * 注册 Provider 并尝试 bind。
     *
     * <p>若同 id 的 Provider 已存在则先解绑旧实例，再创建新实例。
     */
    public BaseIpcProvider registerAndBind(IpcProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为空");
        }
        BaseIpcProvider existing = activeProviders.get(config.getId());
        if (existing != null) {
            existing.unbind(context);
            existing.removeStateListener(providerStateForwarder);
        }
        BaseIpcProvider provider = createProvider(config);
        if (provider == null) {
            throw new IllegalStateException("未找到 Provider 工厂: " + config.getProviderType());
        }
        provider.addStateListener(providerStateForwarder);
        provider.bind(context);
        activeProviders.put(config.getId(), provider);
        return provider;
    }

    /** 注销并解绑指定 id 的 Provider。 */
    public void unregisterAndUnbind(String providerId) {
        BaseIpcProvider provider = activeProviders.remove(providerId);
        if (provider != null) {
            provider.removeStateListener(providerStateForwarder);
            provider.unbind(context);
        }
    }

    /** 注销所有 Provider。 */
    public void unregisterAll() {
        for (BaseIpcProvider provider : new ArrayList<>(activeProviders.values())) {
            provider.removeStateListener(providerStateForwarder);
            provider.unbind(context);
        }
        activeProviders.clear();
    }

    /** 按 id 与类型查找已注册 Provider；类型不匹配返回 null。 */
    public <T extends BaseIpcProvider> T getProvider(String providerId, Class<T> type) {
        BaseIpcProvider provider = activeProviders.get(providerId);
        if (provider == null || !type.isInstance(provider)) {
            return null;
        }
        return type.cast(provider);
    }

    /** 按类型查找第一个已绑定的 Provider。 */
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

    /** 按类型查找所有已注册 Provider。 */
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

    /** 添加全局状态监听器；所有 Provider 状态变化都会转发到此监听器。 */
    public void addStateListener(IpcProviderStateListener listener) {
        if (listener != null) {
            globalListeners.add(listener);
        }
    }

    /** 移除全局状态监听器。 */
    public void removeStateListener(IpcProviderStateListener listener) {
        if (listener != null) {
            globalListeners.remove(listener);
        }
    }

    private final IpcProviderStateListener providerStateForwarder = (provider, newState, cause) -> {
        for (IpcProviderStateListener listener : globalListeners) {
            try {
                listener.onStateChanged(provider, newState, cause);
            } catch (RuntimeException ignored) {
            }
        }
    };

    /**
     * 通过注册表创建 Provider 实例；找不到工厂时返回 null。
     *
     * <p>替换旧的 {@code switch} 结构，遵守 OCP。
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
