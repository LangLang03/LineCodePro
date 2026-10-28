package cn.lineai.ipc;

public enum IpcProviderType {
    TERMINAL("terminal", "cn.lineai.action.IPC_TERMINAL_PROVIDER", "cn.lineai.permission.IPC_TERMINAL_PROVIDER");

    private final String id;
    private final String intentAction;
    private final String permissionName;

    IpcProviderType(String id, String intentAction, String permissionName) {
        this.id = id;
        this.intentAction = intentAction;
        this.permissionName = permissionName;
    }

    public String getId() {
        return id;
    }

    public String getIntentAction() {
        return intentAction;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public static IpcProviderType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("IPC 提供者类型 id 为空");
        }
        for (IpcProviderType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的 IPC 提供者类型: " + id);
    }
}
