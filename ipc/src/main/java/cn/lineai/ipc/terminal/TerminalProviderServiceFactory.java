package cn.lineai.ipc.terminal;

import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderFactory;
import cn.lineai.ipc.IpcProviderType;

/**
 * 终端 Provider 工厂（单例）。
 *
 * <p>负责在 {@link cn.lineai.ipc.IpcProviderRegistry} 中注册自身，
 * 并按需创建 {@link TerminalIpcProvider} 实例。
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
