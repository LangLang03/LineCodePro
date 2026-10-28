package cn.lineai.ipc;

/**
 * IPC 提供者工厂接口（工厂方法模式）。
 *
 * <p>具体实现由各 Provider 子包提供；通过 {@link IpcProviderRegistry} 注册后，
 * {@link IpcProviderManager} 即可按 {@link IpcProviderType} 创建对应实例，遵守 OCP。
 */
public interface IpcProviderFactory {

    /** 该工厂能创建的 Provider 类型 */
    IpcProviderType getProviderType();

    /**
     * 根据配置创建一个已绑定（未执行 bind）的 Provider 实例。
     *
     * @param config Provider 配置（不可变值对象）
     * @return 新创建的 Provider；调用方负责调用 {@link BaseIpcProvider#bind}
     */
    BaseIpcProvider create(IpcProviderConfig config);
}
