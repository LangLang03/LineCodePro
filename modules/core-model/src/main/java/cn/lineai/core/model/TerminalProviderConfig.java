package cn.lineai.core.model;

/** One installed external terminal provider. */
public final class TerminalProviderConfig {

    public final String id;
    public final boolean enabled;
    public final String name;
    public final String packageName;
    public final String serviceClass;
    public final String providerType;
    public final long createdAt;
    public final long updatedAt;

    public TerminalProviderConfig(String id, boolean enabled, String name, String packageName,
                                  String serviceClass, String providerType,
                                  long createdAt, long updatedAt) {
        this.id = id == null ? "" : id;
        this.enabled = enabled;
        this.name = name == null ? "" : name;
        this.packageName = packageName == null ? "" : packageName;
        this.serviceClass = serviceClass == null ? "" : serviceClass;
        this.providerType = providerType == null ? "" : providerType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public TerminalProviderConfig toggled(boolean value) {
        return new TerminalProviderConfig(id, value, name, packageName, serviceClass,
                providerType, createdAt, updatedAt);
    }
}
