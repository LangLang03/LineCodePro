package cn.lineai.tool.mcp;

import org.json.JSONObject;

import java.util.List;

import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.McpHeader;
import cn.lineai.core.model.McpToolInfo;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** One remote MCP tool exposed to the local tool loop. */
final class McpTool implements Tool {

    private final McpExtension server;
    private final McpToolInfo remote;
    private final String name;
    private final McpCaller caller;

    McpTool(McpExtension server, McpToolInfo remote, String name, McpCaller caller) {
        this.server = server;
        this.remote = remote;
        this.name = name;
        this.caller = caller;
    }

    @Override public String name() {
        return name;
    }

    @Override public String description() {
        return remote.description.isEmpty()
                ? "MCP tool " + remote.name + " from " + server.name
                : remote.description + "\nServer: " + server.name;
    }

    @Override public String parametersJson() {
        return remote.inputSchemaJson.isEmpty() ? "{\"type\":\"object\",\"properties\":{}}"
                : remote.inputSchemaJson;
    }

    @Override public boolean mutating() {
        return true;
    }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        ToolPresentation generic = ToolPresentation.generic(name,
                arguments == null ? new JSONObject() : arguments);
        return new ToolPresentation(server.name + " · " + remote.name, ToolIcon.MCP,
                generic.action, ToolPresentation.reason(arguments));
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        try {
            List<McpHeader> headers = server.headers;
            String text = caller.call(server.url, headers, remote.name,
                    arguments == null ? "{}" : arguments.toString());
            if (text.startsWith("MCP error")) {
                return ToolResult.failure("", text);
            }
            return ToolResult.success("", text);
        } catch (Exception error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
}
