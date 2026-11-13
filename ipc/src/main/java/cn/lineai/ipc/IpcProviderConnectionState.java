package cn.lineai.ipc;

/**
 * Connection lifecycle of an IPC provider client.
 *
 * <p>Replaces a plain {@code boolean bound} flag with the four phases of a binding.
 * Transitions are driven inside {@link BaseIpcProvider}; consumers subscribe through
 * {@link IpcProviderStateListener}.
 */
public enum IpcProviderConnectionState {
    /** Initial state, or unbound again after a successful connection. */
    DISCONNECTED,
    /** bindService has been issued; waiting for the system callback. */
    CONNECTING,
    /** Bound with a live IBinder. */
    CONNECTED,
    /** Binding failed or was interrupted; the cause carries the details. */
    FAILED
}
