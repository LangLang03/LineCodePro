package cn.lineai.tool.ui;

import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ui.GenericToolCallViewFactory;
import cn.lineai.tool.ui.ReadToolCallViewFactory;
import cn.lineai.tool.ui.ToolCallReadView;
import org.junit.Assert;
import org.junit.Test;

public final class ToolCallViewFactoryRegistryTest {

    @Test
    public void classBindingWinsOverCategory() {
        ToolCallViewFactoryRegistry registry = new ToolCallViewFactoryRegistry();
        ToolCallViewFactory readFactory = new ReadToolCallViewFactory();
        ToolCallViewFactory genericFactory = new GenericToolCallViewFactory();
        registry.register(readFactory);
        registry.register(genericFactory);
        registry.register(ToolCallReadView.class, readFactory);

        Assert.assertSame(readFactory, registry.resolveFactory(ToolCallReadView.class, ToolDisplayCategory.GENERIC));
    }

    @Test
    public void categoryFallbackWhenNoClassBinding() {
        ToolCallViewFactoryRegistry registry = new ToolCallViewFactoryRegistry();
        ToolCallViewFactory readFactory = new ReadToolCallViewFactory();
        registry.register(readFactory);

        Assert.assertSame(readFactory, registry.resolveFactory(null, ToolDisplayCategory.READ));
    }

    @Test
    public void genericFallbackForUnknownCategory() {
        ToolCallViewFactoryRegistry registry = new ToolCallViewFactoryRegistry();
        ToolCallViewFactory genericFactory = new GenericToolCallViewFactory();
        registry.register(genericFactory);

        Assert.assertSame(genericFactory, registry.resolveFactory(null, ToolDisplayCategory.IMAGE_GENERATION));
    }

    @Test
    public void nullClassBindingFallsBackToCategory() {
        ToolCallViewFactoryRegistry registry = new ToolCallViewFactoryRegistry();
        ToolCallViewFactory readFactory = new ReadToolCallViewFactory();
        registry.register(readFactory);

        Assert.assertSame(readFactory, registry.resolveFactory(ToolCallReadView.class, ToolDisplayCategory.READ));
    }
}
