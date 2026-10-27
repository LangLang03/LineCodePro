# Reflection resolves this class by name to load the optional local-model bridge.
-keep class cn.lineai.runtime.local.LocalModelBridgeImpl {
    public <init>();
}
