package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import org.junit.Assert;
import org.junit.Test;

public final class GenerationControllerTest {
    @Test
    public void enforcesFiniteToolCallLimit() {
        GenerationController controller = new GenerationController();
        ModelConfig model = model(2);

        Assert.assertTrue(controller.canExecuteToolCalls(model, 1, 1));
        Assert.assertFalse(controller.canExecuteToolCalls(model, 1, 2));
        Assert.assertTrue(controller.hasRemainingToolCalls(model, 1));
        Assert.assertFalse(controller.hasRemainingToolCalls(model, 2));
    }

    @Test
    public void unlimitedToolLimitAlwaysHasCapacity() {
        GenerationController controller = new GenerationController();
        ModelConfig model = model(ModelConfig.UNLIMITED_TOOL_CALLS);

        Assert.assertTrue(controller.canExecuteToolCalls(model, 500, 50));
        Assert.assertTrue(controller.hasRemainingToolCalls(model, 500));
    }

    private ModelConfig model(int toolCallLimit) {
        return new ModelConfig(
                "m1",
                "Model",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://example.invalid",
                "key",
                "gpt",
                toolCallLimit
        );
    }
}
