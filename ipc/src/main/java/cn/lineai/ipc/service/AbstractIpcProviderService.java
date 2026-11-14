package cn.lineai.ipc.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Base class for the IPC server side (template-method pattern).
 *
 * <p>Wraps the Service lifecycle boilerplate:
 * <ul>
 *   <li>{@link #onBind(Intent)} returns the AIDL stub produced by {@link #createBinder()}</li>
 *   <li>{@link #onUnbind(Intent)} invokes the {@link #onProviderUnbind(Intent)} hook</li>
 *   <li>{@link #onDestroy()} invokes the {@link #onProviderDestroy()} hook</li>
 * </ul>
 *
 * <p>A subclass implements {@link #createBinder()} for a minimal provider service and
 * overrides the hooks when it needs more control.
 */
public abstract class AbstractIpcProviderService extends Service {

    private static final String TAG = "AbstractIpcProviderService";

    /**
     * Delivers the AIDL binder produced by {@link #createBinder()}; made final so
     * subclasses cannot bypass the stub-creation contract.
     */
    @Override
    public final IBinder onBind(Intent intent) {
        Log.i(TAG, getClass().getSimpleName() + "#onBind action=" + (intent == null ? "null" : intent.getAction()));
        return createBinder();
    }

    /**
     * Runs the {@link #onProviderUnbind(Intent)} hook and returns false so the system does
     * not keep the rebind-on-restart behaviour.
     */
    @Override
    public final boolean onUnbind(Intent intent) {
        Log.i(TAG, getClass().getSimpleName() + "#onUnbind");
        onProviderUnbind(intent);
        return false;
    }

    /** Runs the {@link #onProviderDestroy()} hook after the platform cleanup. */
    @Override
    public void onDestroy() {
        super.onDestroy();
        onProviderDestroy();
    }

    /**
     * Returns the AIDL stub (usually an anonymous {@code IXxxService.Stub}). Called on
     * the main thread: create objects only, never do blocking work.
     */
    protected abstract IBinder createBinder();

    /** Cleanup hook for unbind; the default implementation does nothing. */
    protected void onProviderUnbind(Intent intent) {
    }

    /** Cleanup hook for service destruction; the default implementation does nothing. */
    protected void onProviderDestroy() {
    }
}
