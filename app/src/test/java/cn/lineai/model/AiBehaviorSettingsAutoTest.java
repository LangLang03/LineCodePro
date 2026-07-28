package cn.lineai.model;

import org.junit.Assert;
import org.junit.Test;

public final class AiBehaviorSettingsAutoTest {
    @Test
    public void autoIsNormalizedAndEnabled() {
        Assert.assertEquals(AiBehaviorSettings.REASONING_AUTO,
                AiBehaviorSettings.normalizeReasoningEffort("auto"));
        Assert.assertTrue(AiBehaviorSettings.isReasoningEnabled("auto"));
        Assert.assertEquals(AiBehaviorSettings.REASONING_MEDIUM,
                AiBehaviorSettings.concreteReasoningEffort("auto"));
    }

    @Test
    public void offDisablesReasoning() {
        Assert.assertFalse(AiBehaviorSettings.isReasoningEnabled("off"));
        Assert.assertEquals(AiBehaviorSettings.REASONING_OFF,
                AiBehaviorSettings.concreteReasoningEffort("off"));
    }
}
