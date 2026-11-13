package cn.lineai.ipc;

/**
 * Immutable description of one provider service discovered on the device.
 *
 * <p>Produced by {@link IpcProviderScanner}; the values are sufficient to build an
 * {@link IpcProviderConfig} and bind the service. Null inputs are normalized to the
 * empty string so instances are always safe to consume.
 */
public final class ScannedProvider {
    private final String packageName;
    private final String serviceClass;
    private final String label;
    private final String providerType;

    /**
     * Creates a description; null fields become empty strings.
     *
     * @param packageName the package declaring the provider service
     * @param serviceClass the fully qualified service class name
     * @param label the localized label reported by the package manager
     * @param providerType the {@link IpcProviderType} id the service was scanned for
     */
    public ScannedProvider(String packageName, String serviceClass, String label, String providerType) {
        this.packageName = packageName == null ? "" : packageName;
        this.serviceClass = serviceClass == null ? "" : serviceClass;
        this.label = label == null ? "" : label;
        this.providerType = providerType == null ? "" : providerType;
    }

    /** Returns the package declaring the provider service; never null. */
    public String getPackageName() {
        return packageName;
    }

    /** Returns the fully qualified service class name; never null. */
    public String getServiceClass() {
        return serviceClass;
    }

    /** Returns the localized provider label; never null. */
    public String getLabel() {
        return label;
    }

    /** Returns the {@link IpcProviderType} id the service was scanned for; never null. */
    public String getProviderType() {
        return providerType;
    }
}
