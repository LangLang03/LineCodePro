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
 * IPC 提供者抽象基类（模板方法 + 状态机 + 观察者）。
 *
 * <p>职责：
 * <ul>
 *   <li>封装 {@code bindService}/{@code unbindService} 通用流程</li>
 *   <li>用 {@link IpcProviderConnectionState} 状态机描述连接生命周期</li>
 *   <li>通过 {@link IpcProviderStateListener} 列表向业务方发布状态变化</li>
 * </ul>
 *
 * <p>子类实现 {@link #getProviderType()} 声明自身类型，并通过 {@code TerminalIpcProvider}
 * 等具体类对外暴露 AIDL 调用。
 */
public abstract class BaseIpcProvider {

    private static final String TAG = "BaseIpcProvider";

    protected final IpcProviderConfig config;
    protected volatile IBinder serviceBinder;
    protected volatile boolean bound;
    private ServiceConnection connection;

    private volatile IpcProviderConnectionState connectionState = IpcProviderConnectionState.DISCONNECTED;
    private final List<IpcProviderStateListener> stateListeners = new CopyOnWriteArrayList<>();

    protected BaseIpcProvider(IpcProviderConfig config) {
        this.config = config;
    }

    /** 子类声明自身的 Provider 类型。 */
    public abstract IpcProviderType getProviderType();

    /**
     * 绑定远端服务（final 模板方法）。
     *
     * <p>内部驱动状态机：DISCONNECTED → CONNECTING → CONNECTED / FAILED。
     */
    public final synchronized boolean bind(Context context) {
        if (bound) {
            return true;
        }
        Context appContext = context.getApplicationContext();
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
            Log.w(TAG, "bindService 权限拒绝: " + e.getMessage());
            transitionTo(IpcProviderConnectionState.FAILED, e);
            connection = null;
            return false;
        }
        if (!result) {
            transitionTo(IpcProviderConnectionState.FAILED,
                    new IllegalStateException("bindService 返回 false"));
            connection = null;
        }
        return result;
    }

    /**
     * 解绑远端服务（final 模板方法）。
     *
     * <p>驱动状态机：* → DISCONNECTED。
     */
    public final synchronized void unbind(Context context) {
        if (!bound && connection == null) {
            transitionTo(IpcProviderConnectionState.DISCONNECTED, null);
            serviceBinder = null;
            return;
        }
        if (bound && connection != null) {
            try {
                context.getApplicationContext().unbindService(connection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "unbindService 异常: " + e.getMessage());
            }
        }
        connection = null;
        serviceBinder = null;
        bound = false;
        transitionTo(IpcProviderConnectionState.DISCONNECTED, null);
    }

    public final boolean isBound() {
        return bound;
    }

    public final IpcProviderConfig getConfig() {
        return config;
    }

    public final IBinder getServiceBinder() {
        return serviceBinder;
    }

    /** 当前连接状态。 */
    public final IpcProviderConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * 是否需要在用户层做二次确认（仅作为 UI 提示用，IPC 权限校验在系统层完成）。
     * 默认 false；子类可覆盖（如 shell 执行类需 true）。
     */
    public boolean requiresConfirmation() {
        return false;
    }

    /** 添加状态监听器。 */
    public final void addStateListener(IpcProviderStateListener listener) {
        if (listener == null) {
            return;
        }
        stateListeners.add(listener);
    }

    /** 移除状态监听器。 */
    public final void removeStateListener(IpcProviderStateListener listener) {
        if (listener == null) {
            return;
        }
        stateListeners.remove(listener);
    }

    /** 子类在 ServiceConnection 回调时通知基类（受保护，供 {@link IpcProviderManager} 等调用）。 */
    protected final void onServiceConnected(IBinder binder) {
        this.serviceBinder = binder;
        this.bound = true;
        transitionTo(IpcProviderConnectionState.CONNECTED, null);
    }

    /** 子类在 ServiceConnection 断开时通知基类。 */
    protected final void onServiceDisconnected() {
        this.serviceBinder = null;
        this.bound = false;
        transitionTo(IpcProviderConnectionState.DISCONNECTED, null);
    }

    private void transitionTo(IpcProviderConnectionState newState, Throwable cause) {
        if (this.connectionState == newState) {
            return;
        }
        IpcProviderConnectionState oldState = this.connectionState;
        this.connectionState = newState;
        Log.i(TAG, getProviderType() + " 状态迁移: " + oldState + " -> " + newState);
        for (IpcProviderStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(this, newState, cause);
            } catch (RuntimeException e) {
                Log.w(TAG, "stateListener 回调异常", e);
            }
        }
    }

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
        };
    }
}
