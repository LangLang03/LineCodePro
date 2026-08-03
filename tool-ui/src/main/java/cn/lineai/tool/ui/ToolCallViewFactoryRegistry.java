package cn.lineai.tool.ui;

import android.content.Context;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolCall 卡片视图注册表。
 * <p>解析顺序：工具声明的视图类（{@code ToolInfo.getToolCallViewClass()}）精确命中
 * → 工具显示分类 → GENERIC 兜底。</p>
 */
public final class ToolCallViewFactoryRegistry {
    private static ToolCallViewFactoryRegistry defaultInstance;
    private final Map<ToolDisplayCategory, ToolCallViewFactory> factories = new HashMap<>();
    private final Map<Class<? extends ToolCallCardView>, ToolCallViewFactory> viewClassFactories = new HashMap<>();

    public static void setDefault(ToolCallViewFactoryRegistry registry) {
        defaultInstance = registry;
    }

    public static ToolCallViewFactoryRegistry getDefault() {
        return defaultInstance;
    }

    public void register(ToolCallViewFactory factory) {
        if (factory != null) {
            factories.put(factory.category(), factory);
        }
    }

    /**
     * 按视图类注册；工具通过 {@code ToolInfo.getToolCallViewClass()} 声明后由工厂创建。
     */
    public void register(Class<? extends ToolCallCardView> viewClass, ToolCallViewFactory factory) {
        if (viewClass != null && factory != null) {
            viewClassFactories.put(viewClass, factory);
        }
    }

    public ToolCallViewFactory resolveFactory(Class<? extends ToolCallCardView> viewClass, ToolDisplayCategory category) {
        if (viewClass != null) {
            ToolCallViewFactory factory = viewClassFactories.get(viewClass);
            if (factory != null) {
                return factory;
            }
        }
        ToolCallViewFactory factory = factories.get(category);
        if (factory != null) {
            return factory;
        }
        return factories.get(ToolDisplayCategory.GENERIC);
    }

    public ToolCallCardView createView(Context context, Class<? extends ToolCallCardView> viewClass, ToolDisplayCategory category) {
        ToolCallViewFactory factory = resolveFactory(viewClass, category);
        return factory == null ? null : factory.createView(context);
    }

    public List<ToolCallViewFactory> getAllFactories() {
        List<ToolCallViewFactory> result = new ArrayList<>();
        Set<ToolCallViewFactory> seen = new HashSet<>();
        for (ToolCallViewFactory factory : viewClassFactories.values()) {
            if (seen.add(factory)) {
                result.add(factory);
            }
        }
        for (ToolCallViewFactory factory : factories.values()) {
            if (seen.add(factory)) {
                result.add(factory);
            }
        }
        return result;
    }
}
