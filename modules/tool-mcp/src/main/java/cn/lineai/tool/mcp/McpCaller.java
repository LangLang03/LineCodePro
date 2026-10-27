package cn.lineai.tool.mcp;

import java.util.List;

import cn.lineai.core.model.McpHeader;

/** Blocking MCP tools/call transport; implemented by the HTTP client. */
public interface McpCaller {

    String call(String url, List<McpHeader> headers, String toolName, String argumentsJson)
            throws Exception;
}
