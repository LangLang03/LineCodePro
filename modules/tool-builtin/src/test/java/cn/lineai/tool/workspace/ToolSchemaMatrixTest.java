package cn.lineai.tool.workspace;

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
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ToolSchemaMatrixTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> tools() {
        ToolRegistry registry = BuiltinTools.registry();
        List<Object[]> values = new ArrayList<>();
        for (Tool tool : registry.all()) values.add(new Object[] {tool});
        return values;
    }

    private final Tool tool;

    public ToolSchemaMatrixTest(Tool tool) {
        this.tool = tool;
    }

    @Test public void metadataAndSchemaAreWellFormed() throws Exception {
        assertFalse(tool.name().isEmpty());
        assertFalse(tool.description().isEmpty());
        JSONObject schema = new JSONObject(tool.parametersJson());
        assertEquals("object", schema.getString("type"));
        assertTrue(schema.has("properties"));
        assertFalse(tool.presentation(new JSONObject()).title.isEmpty());
    }
}
