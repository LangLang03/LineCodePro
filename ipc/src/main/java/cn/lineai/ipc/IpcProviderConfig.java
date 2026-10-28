package cn.lineai.ipc;

public final class IpcProviderConfig {
    private final String id;
    private final boolean enabled;
    private final String providerType;
    private final String name;
    private final String packageName;
    private final String serviceClass;
    private final long createdAt;
    private final long updatedAt;

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

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getName() {
        return name;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = "";
        private boolean enabled = true;
        private String providerType = "";
        private String name = "";
        private String packageName = "";
        private String serviceClass = "";
        private long createdAt;
        private long updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder providerType(String providerType) {
            this.providerType = providerType;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder serviceClass(String serviceClass) {
            this.serviceClass = serviceClass;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

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
