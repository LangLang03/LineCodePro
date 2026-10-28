package cn.lineai.tool.phone;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class PhoneSchemaMatrixTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> tools() {
        ToolRegistry registry = PhoneTools.augment(new ToolRegistry(), () -> null);
        List<Object[]> values = new ArrayList<>();
        for (Tool tool : registry.all()) values.add(new Object[] {tool});
        return values;
    }

    private final Tool tool;

    public PhoneSchemaMatrixTest(Tool tool) {
        this.tool = tool;
    }

    @Test public void schemaIsAJsonObject() throws Exception {
        assertEquals("object", new JSONObject(tool.parametersJson()).getString("type"));
        assertFalse(tool.presentation(new JSONObject()).title.isEmpty());
    }
}
