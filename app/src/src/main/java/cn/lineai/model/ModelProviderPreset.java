package cn.lineai.model;

public final class ModelProviderPreset {
    private final String id;
    private final String label;
    private final String desc;
    private final ModelProtocolType protocolType;
    private final String baseUrl;
    private final String placeholder;
    private final String hint;

    public ModelProviderPreset(String id, String label, String desc, ModelProtocolType protocolType, String baseUrl, String placeholder, String hint) {
        this.id = id;
        this.label = label;
        this.desc = desc;
        this.protocolType = protocolType;
        this.baseUrl = baseUrl;
        this.placeholder = placeholder;
        this.hint = hint;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDesc() {
        return desc;
    }

    public ModelProtocolType getProtocolType() {
        return protocolType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getHint() {
        return hint;
    }
}
