package cn.lineai.tool.mcp;

import java.util.List;

import cn.lineai.core.model.McpHeader;

/**
 * Blocking MCP tools/call transport; implemented by the HTTP client.
 *
 * <p>The interface keeps protocol details out of the tool layer: {@link McpTool} only needs a
 * remote name, arguments and headers to obtain the result text.</p>
 */
public interface McpCaller {

    /**
     * Invokes one tool on an MCP server and returns its text content.
     *
     * @param url server endpoint URL
     * @param headers authentication and protocol headers configured for the server
     * @param toolName original MCP tool name, without the local {@code mcp_} prefix
     * @param argumentsJson serialized argument object for the remote tool
     * @return the tool result text; an MCP-level error starts with {@code "MCP error"}
     * @throws Exception on transport failure, protocol failure or malformed responses
     */
    String call(String url, List<McpHeader> headers, String toolName, String argumentsJson)
            throws Exception;
}
