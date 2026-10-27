package cn.lineai.ipc;

import cn.lineai.ipc.terminal.TerminalProviderServiceFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IPC Provider 工厂注册表（注册式发现）。
 *
 * <p>替换 {@code IpcProviderManager} 内嵌的 {@code switch}：
 * <ul>
 *   <li>新增 Provider 类型时，在对应模块中通过 {@link #getInstance()} 注册工厂即可</li>
 *   <li>核心管理器（{@link IpcProviderManager}）无需修改，遵守 OCP</li>
 *   <li>线程安全：{@link ConcurrentHashMap} 保证并发注册/查询</li>
 * </ul>
 */
public final class IpcProviderRegistry {

    private static final IpcProviderRegistry INSTANCE = new IpcProviderRegistry();

    private final Map<IpcProviderType, IpcProviderFactory> factories = new ConcurrentHashMap<>();

    private IpcProviderRegistry() {
        // 默认注册核心库提供的工厂
        register(TerminalProviderServiceFactory.getInstance());
    }

    public static IpcProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个工厂。若同类型已注册则覆盖。
     *
     * @param factory 工厂实现
     * @throws IllegalArgumentException factory 为 null 或未指定合法类型
     */
    public void register(IpcProviderFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory 不能为空");
        }
        IpcProviderType type = factory.getProviderType();
        if (type == null) {
            throw new IllegalArgumentException("factory.getProviderType() 不能为空");
        }
        factories.put(type, factory);
    }

    /** 取消注册某类型的工厂。 */
    public void unregister(IpcProviderType type) {
        if (type == null) {
            return;
        }
        factories.remove(type);
    }

    /** 查询指定类型的工厂；未注册返回 null。 */
    public IpcProviderFactory get(IpcProviderType type) {
        if (type == null) {
            return null;
        }
        return factories.get(type);
    }

    /** 已注册的所有 Provider 类型（不可变快照）。 */
    public Set<IpcProviderType> registeredTypes() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(factories.keySet()));
    }

    /** 已注册的工厂快照（不可变）。 */
    public Map<IpcProviderType, IpcProviderFactory> snapshot() {
        LinkedHashMap<IpcProviderType, IpcProviderFactory> copy = new LinkedHashMap<>(factories);
        return Collections.unmodifiableMap(copy);
    }
}
