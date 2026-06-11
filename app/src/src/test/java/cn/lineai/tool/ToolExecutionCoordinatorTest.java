package cn.lineai.tool;

import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public final class ToolExecutionCoordinatorTest {
    @Test
    public void deleteToolRunsSequentially() {
        ToolCall read = new ToolCall("read_1", "file_read", "{\"file_path\":\"a.txt\"}");
        ToolCall delete = new ToolCall("delete_1", "file_delete", "{\"reason\":\"cleanup\",\"paths\":[\"a.txt\"]}");

        ToolExecutionCoordinator.ToolExecutionPlan plan = new ToolExecutionCoordinator()
                .createPlan(Arrays.asList(read, delete));

        Assert.assertEquals(1, plan.getConcurrentTasks().size());
        Assert.assertEquals("file_read", plan.getConcurrentTasks().get(0).getName());
        Assert.assertEquals(1, plan.getSequentialTasks().size());
        Assert.assertEquals("file_delete", plan.getSequentialTasks().get(0).getName());
    }
}
