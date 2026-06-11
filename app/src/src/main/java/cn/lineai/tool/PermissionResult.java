package cn.lineai.tool;

public final class PermissionResult {
    private final boolean allowed;
    private final String reason;

    private PermissionResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason == null ? "" : reason;
    }

    public static PermissionResult allowed() {
        return new PermissionResult(true, "");
    }

    public static PermissionResult denied(String reason) {
        return new PermissionResult(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
