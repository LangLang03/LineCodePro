package cn.lineai.ipc;

/**
 * IPC 提供者连接状态监听器（观察者）。
 *
 * <p>由 {@link BaseIpcProvider} 在状态机迁移时回调；允许业务方按需订阅连接生命周期事件。
 * 实现需保证线程安全——回调可能在 Binder 线程上发生。
 */
public interface IpcProviderStateListener {
    /**
     * 状态变化回调。
     *
     * @param provider 触发事件的具体 Provider
     * @param newState 新状态
     * @param cause 进入 FAILED 状态时的异常；其他状态为 null
     */
    void onStateChanged(BaseIpcProvider provider,
                        IpcProviderConnectionState newState,
                        Throwable cause);
}
