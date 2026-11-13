package cn.lineai.ipc;

/**
 * Known IPC provider kinds and their discovery identifiers.
 *
 * <p>Each constant carries the triple that identifies a provider on the device: a stable
 * string id persisted in configurations, the intent action services declare, and the
 * custom permission guarding them. All three are part of the frozen AIDL contract.
 */
public enum IpcProviderType {
    /** The Android shell terminal provider. */
    TERMINAL("terminal", "cn.lineai.action.IPC_TERMINAL_PROVIDER", "cn.lineai.permission.IPC_TERMINAL_PROVIDER");

    private final String id;
    private final String intentAction;
    private final String permissionName;

    /**
     * Creates a constant.
     *
     * @param id the stable string id persisted in provider configurations
     * @param intentAction the intent action the provider service declares
     * @param permissionName the custom permission the provider service requires
     */
    IpcProviderType(String id, String intentAction, String permissionName) {
        this.id = id;
        this.intentAction = intentAction;
        this.permissionName = permissionName;
    }

    /** Returns the stable string id used in configurations and scan results. */
    public String getId() {
        return id;
    }

    /** Returns the intent action used to discover provider services of this kind. */
    public String getIntentAction() {
        return intentAction;
    }

    /** Returns the custom permission a compliant provider service must enforce. */
    public String getPermissionName() {
        return permissionName;
    }

    /**
     * Resolves a persisted id back to its constant.
     *
     * @param id the id previously obtained from {@link #getId()}
     * @return the matching constant
     * @throws IllegalArgumentException when the id is null or matches no constant
     */
    public static IpcProviderType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ipc provider type id is empty");
        }
        for (IpcProviderType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown ipc provider type: " + id);
    }
}
