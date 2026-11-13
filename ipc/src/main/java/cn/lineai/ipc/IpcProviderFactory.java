package cn.lineai.ipc;

/**
 * Factory interface for IPC provider clients (factory-method pattern).
 *
 * <p>Concrete implementations live in the provider sub-packages. Once a factory is
 * registered with {@link IpcProviderRegistry}, {@link IpcProviderManager} can create
 * instances for its {@link IpcProviderType} without modification, per the open/closed
 * principle.
 */
public interface IpcProviderFactory {

    /** Returns the provider type this factory creates. */
    IpcProviderType getProviderType();

    /**
     * Creates an unbound provider instance for the given configuration.
     *
     * @param config immutable provider configuration
     * @return a new provider; the caller owns the subsequent {@link BaseIpcProvider#bind}
     */
    BaseIpcProvider create(IpcProviderConfig config);
}
