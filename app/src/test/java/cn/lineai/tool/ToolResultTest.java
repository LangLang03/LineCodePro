package cn.lineai.tool;
import cn.lineai.model.tool.ToolResult;

import org.junit.Assert;
import org.junit.Test;

public final class ToolResultTest {

    @Test
    public void truncateContent_returnsNullForNull() {
        Assert.assertNull(ToolResult.truncateContent(null));
    }

    @Test
    public void truncateContent_returnsSameWhenUnderLimit() {
        String content = "hello world";
        Assert.assertSame(content, ToolResult.truncateContent(content));
    }

    @Test
    public void truncateContent_returnsSameAtExactLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ToolResult.MAX_TOOL_RESULT_CHARS; i++) {
            sb.append('x');
        }
        String content = sb.toString();
        Assert.assertSame(content, ToolResult.truncateContent(content));
    }

    @Test
    public void truncateContent_truncatesMiddleWhenOverLimit() {
        int half = ToolResult.MAX_TOOL_RESULT_CHARS / 2;
        int overBy = 5000;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ToolResult.MAX_TOOL_RESULT_CHARS + overBy; i++) {
            sb.append('x');
        }
        String content = sb.toString();
        String result = ToolResult.truncateContent(content);

        Assert.assertTrue(result.contains(overBy + " chars truncated"));
        Assert.assertTrue(result.startsWith(content.substring(0, half)));
        Assert.assertTrue(result.endsWith(content.substring(content.length() - half)));
        Assert.assertTrue(result.length() < content.length());
    }

    @Test
    public void truncateContent_preservesHeadAndTail() {
        String prefix = "PREFIX_START_";
        String suffix = "_SUFFIX_END";
        int fillSize = ToolResult.MAX_TOOL_RESULT_CHARS + 1000;
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < fillSize; i++) {
            sb.append('a');
        }
        sb.append(suffix);
        String content = sb.toString();
        String result = ToolResult.truncateContent(content);

        Assert.assertTrue(result.startsWith(prefix));
        Assert.assertTrue(result.endsWith(suffix));
    }
}
