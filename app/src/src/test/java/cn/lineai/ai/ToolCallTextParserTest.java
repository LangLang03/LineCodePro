package cn.lineai.ai;

import cn.lineai.tool.ToolCall;
import org.junit.Assert;
import org.junit.Test;

public final class ToolCallTextParserTest {
    @Test
    public void parsesToolCallsTagAndRemovesItFromText() {
        ToolCallTextParser.Result result = ToolCallTextParser.parse(
                "先读取文件\n<tool_calls>[{\"name\":\"file_read\",\"arguments\":{\"file_path\":\"app/build.gradle.kts\"}}]</tool_calls>"
        );

        Assert.assertEquals("先读取文件", result.getText());
        Assert.assertEquals(1, result.getToolCalls().size());
        Assert.assertTrue(result.hasToolMarkup());
        ToolCall call = result.getToolCalls().get(0);
        Assert.assertEquals("file_read", call.getName());
        Assert.assertTrue(call.getArguments().contains("app/build.gradle.kts"));
    }

    @Test
    public void parsesSingleToolCallWithFunctionShape() {
        ToolCallTextParser.Result result = ToolCallTextParser.parse(
                "<tool_call>{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"glob\",\"arguments\":\"{\\\"pattern\\\":\\\"**/*.java\\\"}\"}}</tool_call>"
        );

        Assert.assertEquals("", result.getText());
        Assert.assertEquals(1, result.getToolCalls().size());
        Assert.assertEquals("call_1", result.getToolCalls().get(0).getId());
        Assert.assertEquals("glob", result.getToolCalls().get(0).getName());
        Assert.assertEquals("{\"pattern\":\"**/*.java\"}", result.getToolCalls().get(0).getArguments());
    }

    @Test
    public void parsesXmlStyleToolCallArgumentsAndNormalizesName() {
        ToolCallTextParser.Result result = ToolCallTextParser.parse(
                "准备写入\n<tool_calls>\n"
                        + "<tool_call name=\"filewrite\">\n"
                        + "<argument name=\"file_path\">xxxxxx</argument>\n"
                        + "<argument name=\"content\">&lt;hello&gt;</argument>\n"
                        + "</tool_call>\n"
                        + "</tool_calls>"
        );

        Assert.assertEquals("准备写入", result.getText());
        Assert.assertEquals(1, result.getToolCalls().size());
        ToolCall call = result.getToolCalls().get(0);
        Assert.assertEquals("file_write", call.getName());
        Assert.assertTrue(call.getArguments().contains("\"file_path\":\"xxxxxx\""));
        Assert.assertTrue(call.getArguments().contains("\"content\":\"<hello>\""));
    }

    @Test
    public void streamingPreviewParsesToolTypeBeforeXmlCompletes() {
        ToolCallTextParser.Result result = ToolCallTextParser.parseStreamingPreview(
                "准备读取\n<tool_calls>\n<tool_call name=\"fileread\">"
        );

        Assert.assertEquals("准备读取", result.getText());
        Assert.assertEquals(1, result.getToolCalls().size());
        ToolCall call = result.getToolCalls().get(0);
        Assert.assertEquals("file_read", call.getName());
        Assert.assertEquals("text_tool_xml_0", call.getId());
        Assert.assertEquals("{}", call.getArguments());
    }

    @Test
    public void finalParserDoesNotExecuteIncompleteXmlPreview() {
        ToolCallTextParser.Result result = ToolCallTextParser.parse(
                "准备读取\n<tool_calls>\n<tool_call name=\"fileread\">"
        );

        Assert.assertEquals("准备读取", result.getText());
        Assert.assertEquals(0, result.getToolCalls().size());
    }
}
