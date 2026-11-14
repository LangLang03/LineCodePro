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

    /**
     * Creates a configuration, normalising null strings to empty.
     * @param id stable provider identifier
     * @param enabled whether the provider participates in terminal scanning
     * @param name display name
     * @param packageName Android package hosting the provider service
     * @param serviceClass provider service class name
     * @param providerType provider protocol family
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last modification timestamp in epoch milliseconds
     */
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

    /**
     * Returns a copy with the enabled flag replaced.
     * @param value new enabled state
     * @return a new immutable configuration
     */
    public TerminalProviderConfig toggled(boolean value) {
        return new TerminalProviderConfig(id, value, name, packageName, serviceClass,
                providerType, createdAt, updatedAt);
    }
}
