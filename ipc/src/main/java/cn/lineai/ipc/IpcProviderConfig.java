package cn.lineai.ipc;

/**
 * Immutable description of one configured IPC provider, persisted by the host app.
 *
 * <p>The id is the primary key of the provider registry; {@code providerType} carries an
 * {@link IpcProviderType} id used to locate the matching factory. All string fields are
 * normalized to the empty string so instances never carry null. Use {@link Builder} to
 * assemble instances.
 */
public final class IpcProviderConfig {
    private final String id;
    private final boolean enabled;
    private final String providerType;
    private final String name;
    private final String packageName;
    private final String serviceClass;
    private final long createdAt;
    private final long updatedAt;

    /**
     * Creates an immutable configuration; null strings become empty strings.
     *
     * @param id unique provider id used as the registry key
     * @param enabled whether the provider participates in binding
     * @param providerType an {@link IpcProviderType} id selecting the factory
     * @param name human-readable display name
     * @param packageName package declaring the provider service
     * @param serviceClass fully qualified service class name
     * @param createdAt creation timestamp in epoch millis
     * @param updatedAt last-modification timestamp in epoch millis
     */
    public IpcProviderConfig(String id, boolean enabled, String providerType, String name,
                             String packageName, String serviceClass,
                             long createdAt, long updatedAt) {
        this.id = id == null ? "" : id;
        this.enabled = enabled;
        this.providerType = providerType == null ? "" : providerType;
        this.name = name == null ? "" : name;
        this.packageName = packageName == null ? "" : packageName;
        this.serviceClass = serviceClass == null ? "" : serviceClass;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Returns the unique provider id used as the registry key. */
    public String getId() {
        return id;
    }

    /** Returns whether the provider participates in binding. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Returns the {@link IpcProviderType} id selecting the factory. */
    public String getProviderType() {
        return providerType;
    }

    /** Returns the human-readable display name. */
    public String getName() {
        return name;
    }

    /** Returns the package declaring the provider service. */
    public String getPackageName() {
        return packageName;
    }

    /** Returns the fully qualified service class name, or the empty string for action-only binding. */
    public String getServiceClass() {
        return serviceClass;
    }

    /** Returns the creation timestamp in epoch millis. */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Returns the last-modification timestamp in epoch millis. */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /** Returns a fresh builder with the documented defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link IpcProviderConfig}. Fields default to empty strings,
     * {@code enabled} defaults to true, and unset timestamps are stamped at build time.
     */
    public static final class Builder {
        private String id = "";
        private boolean enabled = true;
        private String providerType = "";
        private String name = "";
        private String packageName = "";
        private String serviceClass = "";
        private long createdAt;
        private long updatedAt;

        /** Sets the unique provider id. */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /** Sets whether the provider participates in binding. */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /** Sets the {@link IpcProviderType} id selecting the factory. */
        public Builder providerType(String providerType) {
            this.providerType = providerType;
            return this;
        }

        /** Sets the human-readable display name. */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** Sets the package declaring the provider service. */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /** Sets the fully qualified service class name. */
        public Builder serviceClass(String serviceClass) {
            this.serviceClass = serviceClass;
            return this;
        }

        /** Sets the creation timestamp; unset or non-positive values are stamped at build time. */
        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /** Sets the modification timestamp; unset or non-positive values are stamped at build time. */
        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /** Builds the immutable configuration, stamping missing timestamps with the current time. */
        public IpcProviderConfig build() {
            long now = System.currentTimeMillis();
            return new IpcProviderConfig(
                    id, enabled, providerType, name, packageName, serviceClass,
                    createdAt <= 0 ? now : createdAt,
                    updatedAt <= 0 ? now : updatedAt
            );
        }
    }
}
