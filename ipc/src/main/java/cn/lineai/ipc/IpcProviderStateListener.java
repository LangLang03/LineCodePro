package cn.lineai.ipc;

/**
 * Observer for IPC provider connection state.
 *
 * <p>Invoked by {@link BaseIpcProvider} on every state-machine transition, so owners can
 * subscribe to the connection lifecycle. Implementations must be thread-safe: callbacks
 * may arrive on a Binder thread.
 */
public interface IpcProviderStateListener {
    /**
     * Reports a state transition.
     *
     * @param provider the provider that produced the event
     * @param newState the state entered
     * @param cause the failure when entering FAILED; null for other states
     */
    void onStateChanged(BaseIpcProvider provider,
                        IpcProviderConnectionState newState,
                        Throwable cause);
}
