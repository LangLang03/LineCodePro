package cn.lineai.runtime;

import org.json.JSONObject;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;
import cn.lineai.tool.workspace.ShellKillTool;
import cn.lineai.tool.workspace.ShellOutputTool;
import cn.lineai.tool.workspace.ShellTool;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class LocalToolPolicyTest {

    @Test
    public void keepsShellFamilyWhenThePreferenceIsEnabled() {
        ToolRegistry source = registry();
        assertSame(source, LocalToolPolicy.apply(source, true));
    }

    @Test
    public void removesTheWholeShellFamilyWhenThePreferenceIsDisabled() {
        ToolRegistry filtered = LocalToolPolicy.apply(registry(), false);
        assertNull(filtered.find(ShellTool.NAME));
        assertNull(filtered.find(ShellOutputTool.NAME));
        assertNull(filtered.find(ShellKillTool.NAME));
        assertNotNull(filtered.find("other_tool"));
    }

    private static ToolRegistry registry() {
        return new ToolRegistry().register(new FakeTool(ShellTool.NAME))
                .register(new FakeTool(ShellOutputTool.NAME))
                .register(new FakeTool(ShellKillTool.NAME))
                .register(new FakeTool("other_tool"));
    }

    private static final class FakeTool implements Tool {
        private final String name;

        FakeTool(String name) {
            this.name = name;
        }

        @Override public String name() { return name; }

        @Override public String description() { return name; }

        @Override public String parametersJson() { return "{}"; }

        @Override public boolean mutating() { return true; }

        @Override public ToolResult execute(cn.lineai.tool.api.ToolContext context,
                                            JSONObject arguments) {
            return ToolResult.success("", "");
        }
    }
}
