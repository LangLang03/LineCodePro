package cn.lineai.core.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class ModelPresetMatrixTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> presets() {
        List<Object[]> values = new ArrayList<>();
        for (ModelPreset preset : ModelPreset.all()) values.add(new Object[] {preset});
        return values;
    }

    private final ModelPreset preset;

    public ModelPresetMatrixTest(ModelPreset preset) {
        this.preset = preset;
    }

    @Test public void identityFieldsArePresent() {
        assertFalse(preset.id.isEmpty());
        assertFalse(preset.baseUrl.isEmpty());
        assertFalse(preset.protocol == null);
    }
}
