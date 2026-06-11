package cn.lineai.mvp;

import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolResult;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public final class ToolRunControllerTest {
    @Test
    public void orderedResultsFollowOriginalToolCallOrder() {
        ToolRunController controller = new ToolRunController(new ToolExecutionCoordinator(), null, null);
        ToolCall first = new ToolCall("read_1", "file_read", "{}");
        ToolCall second = new ToolCall("glob_1", "glob", "{}");
        HashMap<String, ToolResult> resultById = new HashMap<>();
        resultById.put("glob_1", new ToolResult("glob_1", "glob", "b", false));
        resultById.put("read_1", new ToolResult("read_1", "file_read", "a", false));

        List<ToolResult> ordered = controller.orderedResults(Arrays.asList(first, second), resultById);

        Assert.assertEquals("read_1", ordered.get(0).getToolCallId());
        Assert.assertEquals("glob_1", ordered.get(1).getToolCallId());
    }

    @Test
    public void remainingCallsSkipsCompletedPrefixAndNulls() {
        ToolRunController controller = new ToolRunController(new ToolExecutionCoordinator(), null, null);
        ToolCall second = new ToolCall("delete_1", "file_delete", "{}");
        ToolCall third = new ToolCall("write_1", "file_write", "{}");

        List<ToolCall> remaining = controller.remainingCalls(Arrays.asList(null, second, third), 1);

        Assert.assertEquals(2, remaining.size());
        Assert.assertEquals("delete_1", remaining.get(0).getId());
        Assert.assertEquals("write_1", remaining.get(1).getId());
    }
}
