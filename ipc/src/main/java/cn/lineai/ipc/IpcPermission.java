package cn.lineai.ipc;

/**
 * Permission identifiers of the IPC contract library.
 *
 * <p>Providers declare these values as {@code <service android:permission="...">} and
 * clients declare them as {@code <uses-permission>}. The literal strings are part of the
 * frozen AIDL contract; renaming them breaks every deployed provider and host.
 */
public final class IpcPermission {
    /** Custom permission guarding access to the terminal provider service. */
    public static final String TERMINAL_PROVIDER = "cn.lineai.permission.IPC_TERMINAL_PROVIDER";

    /** Utility class; never instantiated. */
    private IpcPermission() {
    }
}
