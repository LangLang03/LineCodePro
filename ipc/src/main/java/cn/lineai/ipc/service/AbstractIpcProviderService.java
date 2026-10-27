package cn.lineai.ipc.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * IPC 服务端抽象基类（模板方法模式）。
 *
 * <p>封装 Service 生命周期的样板代码：
 * <ul>
 *   <li>{@link #onBind(Intent)}：返回 {@link #createBinder()} 提供的 AIDL Stub</li>
 *   <li>{@link #onUnbind(Intent)}：调用 {@link #onProviderUnbind(Intent)} 钩子</li>
 *   <li>{@link #onDestroy()}：调用 {@link #onProviderDestroy()} 钩子</li>
 * </ul>
 *
 * <p>子类仅需实现 {@link #createBinder()} 即可完成一个最小可用的 Provider Service；
 * 需要更多控制时覆盖对应钩子方法。
 */
public abstract class AbstractIpcProviderService extends Service {

    private static final String TAG = "AbstractIpcProviderService";

    @Override
    public final IBinder onBind(Intent intent) {
        Log.i(TAG, getClass().getSimpleName() + "#onBind action=" + (intent == null ? "null" : intent.getAction()));
        return createBinder();
    }

    @Override
    public final boolean onUnbind(Intent intent) {
        Log.i(TAG, getClass().getSimpleName() + "#onUnbind");
        onProviderUnbind(intent);
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onProviderDestroy();
    }

    /**
     * 子类返回 AIDL Stub（通常为 {@code IXxxService.Stub} 匿名实例）。
     * 该方法在主线程调用，应只做对象创建，不做耗时操作。
     */
    protected abstract IBinder createBinder();

    /** 解绑时的资源清理钩子；默认无操作。 */
    protected void onProviderUnbind(Intent intent) {
    }

    /** Service 销毁时的资源清理钩子；默认无操作。 */
    protected void onProviderDestroy() {
    }
}
