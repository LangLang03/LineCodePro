package cn.lineai.model;

import org.junit.Assert;
import org.junit.Test;

public final class InputSettingsTest {
    @Test
    public void normalizeEnterKeyBehaviorFallsBackToSend() {
        Assert.assertEquals(InputSettings.ENTER_SEND, InputSettings.normalizeEnterKeyBehavior(null));
        Assert.assertEquals(InputSettings.ENTER_SEND, InputSettings.normalizeEnterKeyBehavior(""));
        Assert.assertEquals(InputSettings.ENTER_SEND, InputSettings.normalizeEnterKeyBehavior("unknown"));
    }

    @Test
    public void normalizeEnterKeyBehaviorKeepsNewline() {
        Assert.assertEquals(InputSettings.ENTER_NEWLINE, InputSettings.normalizeEnterKeyBehavior(InputSettings.ENTER_NEWLINE));
    }
}
