package cn.lineai.ipc.terminal;

import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderFactory;
import cn.lineai.ipc.IpcProviderType;

/**
 * Singleton factory for the terminal provider client.
 *
 * <p>Registers itself with {@link cn.lineai.ipc.IpcProviderRegistry} and creates
 * {@link TerminalIpcProvider} instances on demand.
 */
public final class TerminalProviderServiceFactory implements IpcProviderFactory {

    private static final TerminalProviderServiceFactory INSTANCE = new TerminalProviderServiceFactory();

    /** Returns the process-wide singleton, already usable without explicit registration. */
    public static TerminalProviderServiceFactory getInstance() {
        return INSTANCE;
    }

    /** Singleton; use {@link #getInstance()}. */
    private TerminalProviderServiceFactory() {
    }

    /** Returns {@link IpcProviderType#TERMINAL}. */
    @Override
    public IpcProviderType getProviderType() {
        return IpcProviderType.TERMINAL;
    }

    /** Creates an unbound {@link TerminalIpcProvider} for the given configuration. */
    @Override
    public BaseIpcProvider create(IpcProviderConfig config) {
        return new TerminalIpcProvider(config);
    }
}
