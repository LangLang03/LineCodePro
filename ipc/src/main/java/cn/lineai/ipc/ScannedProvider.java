package cn.lineai.ipc;

public final class ScannedProvider {
    private final String packageName;
    private final String serviceClass;
    private final String label;
    private final String providerType;

    public ScannedProvider(String packageName, String serviceClass, String label, String providerType) {
        this.packageName = packageName == null ? "" : packageName;
        this.serviceClass = serviceClass == null ? "" : serviceClass;
        this.label = label == null ? "" : label;
        this.providerType = providerType == null ? "" : providerType;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getLabel() {
        return label;
    }

    public String getProviderType() {
        return providerType;
    }
}
