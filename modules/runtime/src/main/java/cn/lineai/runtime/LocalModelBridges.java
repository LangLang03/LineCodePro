package cn.lineai.runtime;

/** Finds the optional local-model bridge without a compile-time dependency on it. */
final class LocalModelBridges {

    private static final String IMPLEMENTATION = "cn.lineai.runtime.local.LocalModelBridgeImpl";

    private LocalModelBridges() {
    }

    static LocalModelBridge find() {
        try {
            Object value = Class.forName(IMPLEMENTATION).getDeclaredConstructor().newInstance();
            return value instanceof LocalModelBridge ? (LocalModelBridge) value : null;
        } catch (ReflectiveOperationException error) {
            return null;
        } catch (LinkageError error) {
            return null;
        }
    }
}
