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

    public static TerminalProviderServiceFactory getInstance() {
        return INSTANCE;
    }

    private TerminalProviderServiceFactory() {
    }

    @Override
    public IpcProviderType getProviderType() {
        return IpcProviderType.TERMINAL;
    }

    @Override
    public BaseIpcProvider create(IpcProviderConfig config) {
        return new TerminalIpcProvider(config);
    }
}
