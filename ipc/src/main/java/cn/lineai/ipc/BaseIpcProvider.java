package cn.lineai.ipc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for IPC provider clients. It combines the template-method pattern for the
 * {@code bindService}/{@code unbindService} flow, an {@link IpcProviderConnectionState}
 * state machine for the connection lifecycle, and an observer list that publishes state
 * transitions to the owning feature layer.
 *
 * <p>Subclasses declare their type through {@link #getProviderType()} and expose the AIDL
 * calls of a concrete provider such as {@code TerminalIpcProvider}.
 */
public abstract class BaseIpcProvider {

    private static final String TAG = "BaseIpcProvider";

    /** Immutable configuration this provider was created from. */
    protected final IpcProviderConfig config;
    /** The live AIDL binder once connected; null while disconnected. Subclasses read it directly. */
    protected volatile IBinder serviceBinder;
    /** Reflects the ServiceConnection callbacks, not the intent issued in {@link #bind}. */
    protected volatile boolean bound;
    /** Non-null from the first bind attempt until unbind, so CONNECTING stays unbindable. */
    private ServiceConnection connection;
    /** Application context captured at bind time; used for the automatic rebind. */
    private Context boundContext;

    /** Current lifecycle phase; written on Binder or main threads, read anywhere. */
    private volatile IpcProviderConnectionState connectionState = IpcProviderConnectionState.DISCONNECTED;
    private final List<IpcProviderStateListener> stateListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a provider; binding happens later through {@link #bind}.
     *
     * @param config immutable configuration for this provider
     */
    protected BaseIpcProvider(IpcProviderConfig config) {
        this.config = config;
    }

    /** Returns the provider type declared by the concrete subclass. */
    public abstract IpcProviderType getProviderType();

    /**
     * Binds the remote provider service (final template method).
     *
     * <p>Drives the state machine DISCONNECTED &rarr; CONNECTING &rarr; CONNECTED / FAILED.
     */
    public final synchronized boolean bind(Context context) {
        if (bound) {
            return true;
        }
        if (connection != null) {
            // A second bind() while CONNECTING would overwrite the connection field and
            // leak the already-registered ServiceConnection.
            return false;
        }
        Context appContext = context.getApplicationContext();
        boundContext = appContext;
        Intent intent = new Intent(getProviderType().getIntentAction());
        intent.setPackage(config.getPackageName());
        if (config.getServiceClass() != null && config.getServiceClass().length() > 0) {
            intent.setClassName(config.getPackageName(), config.getServiceClass());
        }
        connection = createConnection();
        transitionTo(IpcProviderConnectionState.CONNECTING, null);
        boolean result;
        try {
            result = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.w(TAG, "bindService permission denied: " + e.getMessage());
            transitionTo(IpcProviderConnectionState.FAILED, e);
            connection = null;
            return false;
        }
        if (!result) {
            transitionTo(IpcProviderConnectionState.FAILED,
                    new IllegalStateException("bindService returned false"));
            connection = null;
        }
        return result;
    }

    /**
     * Unbinds the remote provider service (final template method).
     *
     * <p>Drives the state machine to DISCONNECTED from any prior state.
     */
    public final synchronized void unbind(Context context) {
        if (connection != null) {
            // While CONNECTING the bound flag is still false, but the ServiceConnection is
            // already registered with the system and must be unbound here.
            try {
                context.getApplicationContext().unbindService(connection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "unbindService failed: " + e.getMessage());
            }
        }
        connection = null;
        serviceBinder = null;
        bound = false;
        transitionTo(IpcProviderConnectionState.DISCONNECTED, null);
    }

    /**
     * Reports whether a live binder has been delivered by the system. False while
     * CONNECTING even though the ServiceConnection is already registered.
     */
    public final boolean isBound() {
        return bound;
    }

    /** Returns the immutable configuration this provider was created from. */
    public final IpcProviderConfig getConfig() {
        return config;
    }

    /** Returns the live AIDL binder, or null while the provider is not connected. */
    public final IBinder getServiceBinder() {
        return serviceBinder;
    }

    /** Returns the current connection state. */
    public final IpcProviderConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * Whether callers should show an extra user-side confirmation. This is a UI hint only;
     * enforcement of the IPC permission happens at the system layer. Defaults to false;
     * shell-execution providers override it to true.
     */
    public boolean requiresConfirmation() {
        return false;
    }

    /** Registers a state listener; registration is idempotent per listener instance. */
    public final void addStateListener(IpcProviderStateListener listener) {
        if (listener == null) {
            return;
        }
        stateListeners.add(listener);
    }

    /** Removes a previously registered state listener. */
    public final void removeStateListener(IpcProviderStateListener listener) {
        if (listener == null) {
            return;
        }
        stateListeners.remove(listener);
    }

    /** Called by the concrete subclass when the ServiceConnection delivers the binder. */
    protected final void onServiceConnected(IBinder binder) {
        this.serviceBinder = binder;
        this.bound = true;
        transitionTo(IpcProviderConnectionState.CONNECTED, null);
    }

    /** Called by the concrete subclass when the ServiceConnection drops. */
    protected final void onServiceDisconnected() {
        this.serviceBinder = null;
        this.bound = false;
        transitionTo(IpcProviderConnectionState.DISCONNECTED, null);
    }

    /**
     * Publishes a state change to the listeners; listener failures are logged and
     * swallowed so one broken observer cannot corrupt the state machine.
     */
    private void transitionTo(IpcProviderConnectionState newState, Throwable cause) {
        if (this.connectionState == newState) {
            return;
        }
        IpcProviderConnectionState oldState = this.connectionState;
        this.connectionState = newState;
        Log.i(TAG, getProviderType() + " state: " + oldState + " -> " + newState);
        for (IpcProviderStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(this, newState, cause);
            } catch (RuntimeException e) {
                Log.w(TAG, "stateListener callback failed", e);
            }
        }
    }

    /** Wraps the four system callbacks into the corresponding state-machine transitions. */
    private ServiceConnection createConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                BaseIpcProvider.this.onServiceConnected(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                BaseIpcProvider.this.onServiceDisconnected();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                BaseIpcProvider.this.onBindingDied();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                BaseIpcProvider.this.onBindingDied();
            }
        };
    }

    /**
     * Releases the stale binding when the provider process dies and rebinds once; a failed
     * rebind enters FAILED and is left to the owner's refresh flow instead of idling in
     * DISCONNECTED with no recovery path.
     */
    private synchronized void onBindingDied() {
        if (connection == null) {
            return;
        }
        Context context = boundContext;
        try {
            if (context != null) {
                context.unbindService(connection);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "unbindService failed: " + e.getMessage());
        }
        connection = null;
        serviceBinder = null;
        bound = false;
        if (context == null) {
            transitionTo(IpcProviderConnectionState.DISCONNECTED, null);
            return;
        }
        Log.w(TAG, getProviderType() + " binding died, rebinding");
        bind(context);
    }
}
