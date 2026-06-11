package cn.lineai.model;

public final class McpRequestHeader {
    private final String name;
    private final String value;

    public McpRequestHeader(String name, String value) {
        this.name = name == null ? "" : name.trim();
        this.value = value == null ? "" : value.trim();
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
