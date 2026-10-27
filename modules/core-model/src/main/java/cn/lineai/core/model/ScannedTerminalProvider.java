package cn.lineai.core.model;

/** A terminal provider service discovered on the device. */
public final class ScannedTerminalProvider {

    public final String name;
    public final String packageName;
    public final String serviceClass;
    public final String providerType;

    public ScannedTerminalProvider(String name, String packageName, String serviceClass,
                                   String providerType) {
        this.name = name == null ? "" : name;
        this.packageName = packageName == null ? "" : packageName;
        this.serviceClass = serviceClass == null ? "" : serviceClass;
        this.providerType = providerType == null ? "" : providerType;
    }
}
