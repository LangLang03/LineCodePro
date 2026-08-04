package cn.lineai.tool.ui;

/**
 * {@link ToolInfoResolver} 的全局注册点。由 app 启动时（MainDependencies）注入实现。
 */
public final class ToolInfoResolverProvider {
    private static ToolInfoResolver resolver;

    private ToolInfoResolverProvider() {
    }

    public static void setDefault(ToolInfoResolver resolver) {
        ToolInfoResolverProvider.resolver = resolver;
    }

    public static ToolInfoResolver getDefault() {
        return resolver;
    }
}
