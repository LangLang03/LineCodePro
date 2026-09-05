package cn.lineai.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.util.Collections;
import org.junit.Test;

public final class ReasoningCompatibilityTest {
    private final ModelConfig glm = ModelConfig.builder(
            "glm", "GLM", ModelProtocolType.OPENAI_COMPATIBLE, "Zhipu",
            "https://open.bigmodel.cn/api/paas/v4", "key", "glm-5.2").build();

    @Test
    public void glmUsesOnlyLowHighAndMax() {
        assertEquals(AiBehaviorSettings.REASONING_LOW,
                ReasoningCompatibility.compatibleEffort(glm, AiBehaviorSettings.REASONING_OFF));
        assertEquals(AiBehaviorSettings.REASONING_LOW,
                ReasoningCompatibility.compatibleEffort(glm, AiBehaviorSettings.REASONING_LOW));
        assertEquals(AiBehaviorSettings.REASONING_HIGH,
                ReasoningCompatibility.compatibleEffort(glm, AiBehaviorSettings.REASONING_AUTO));
        assertEquals(AiBehaviorSettings.REASONING_HIGH,
                ReasoningCompatibility.compatibleEffort(glm, AiBehaviorSettings.REASONING_MEDIUM));
        assertEquals(AiBehaviorSettings.REASONING_HIGH,
                ReasoningCompatibility.compatibleEffort(glm, AiBehaviorSettings.REASONING_HIGH));
        assertEquals(AiBehaviorSettings.REASONING_MAX,
                ReasoningCompatibility.compatibleEffort(glm, AiBehaviorSettings.REASONING_MAX));
    }

    @Test
    public void adaptationPreservesReasoningHistoryPreferenceAndEmptyTools() {
        ModelRequestOptions adapted = ReasoningCompatibility.adapt(glm,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_MEDIUM, true,
                        Collections.emptyList()));

        assertEquals(AiBehaviorSettings.REASONING_HIGH, adapted.getReasoningEffort());
        assertTrue(adapted.isPreserveReasoning());
        assertTrue(adapted.getTools().isEmpty());
    }

    @Test
    public void nonGlmModelsKeepTheRequestedEffort() {
        ModelConfig model = ModelConfig.builder(
                "gpt", "GPT", ModelProtocolType.OPENAI_COMPATIBLE, "OpenAI",
                "https://api.openai.com/v1", "key", "gpt-5.6-sol").build();

        assertEquals(AiBehaviorSettings.REASONING_OFF,
                ReasoningCompatibility.compatibleEffort(model, AiBehaviorSettings.REASONING_OFF));
        assertEquals(AiBehaviorSettings.REASONING_MEDIUM,
                ReasoningCompatibility.compatibleEffort(model, AiBehaviorSettings.REASONING_MEDIUM));
    }
}
