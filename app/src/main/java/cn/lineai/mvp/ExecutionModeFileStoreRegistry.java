package cn.lineai.mvp;

import java.util.HashMap;

/**
 * 执行模式到 FileStore 的注册表，遵循 OCP：新增执行模式只需注册新的 FileStore，
 * 无需修改 FileOperationController 的选择逻辑。
 */
public final class ExecutionModeFileStoreRegistry {

    public static final String MODE_LOCAL = "local";
    public static final String MODE_SSH = "ssh";
    public static final String MODE_IPC = "ipc";

    private final HashMap<String, FileOperationController.FileStore> stores = new HashMap<>();
    private String defaultMode = MODE_LOCAL;

    public void register(String mode, FileOperationController.FileStore store) {
        if (mode == null || store == null) {
            return;
        }
        stores.put(mode, store);
    }

    public void useDefaultMode(String mode) {
        if (mode != null) {
            this.defaultMode = mode;
        }
    }

    public FileOperationController.FileStore get(String mode) {
        FileOperationController.FileStore store = stores.get(mode);
        if (store != null) {
            return store;
        }
        FileOperationController.FileStore fallback = stores.get(defaultMode);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No FileStore registered for mode: " + mode + " and no default fallback");
    }

    /**
     * 根据宿主状态推断当前执行模式键。
     */
    public static String resolveMode(FileOperationController.Host host) {
        if (host.isTerminalProviderExecutionMode()) {
            return MODE_IPC;
        }
        if (host.isSshExecutionMode()) {
            return MODE_SSH;
        }
        return MODE_LOCAL;
    }
}
