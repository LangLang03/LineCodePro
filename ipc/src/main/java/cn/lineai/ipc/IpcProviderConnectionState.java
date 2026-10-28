package cn.lineai.ipc;

/**
 * IPC 提供者连接状态枚举（状态机）。
 *
 * <p>替代单一的 {@code boolean bound} 字段，能更精确地表达连接生命周期。
 * 状态迁移由 {@link BaseIpcProvider} 内部驱动，业务方通过 {@link IpcProviderStateListener}
 * 订阅状态变化。
 */
public enum IpcProviderConnectionState {
    /** 未连接（初始或已解绑） */
    DISCONNECTED,
    /** 正在执行 bindService，等待系统回调 */
    CONNECTING,
    /** 已成功绑定并持有有效的 IBinder */
    CONNECTED,
    /** bind 失败或连接中断（详情见异常） */
    FAILED
}
