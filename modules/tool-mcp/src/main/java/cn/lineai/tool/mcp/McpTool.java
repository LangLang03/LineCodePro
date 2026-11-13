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
import cn.lineai.tool.api.ToolSchemaBuilder;

/**
 * One remote MCP tool exposed to the local tool loop.
 *
 * <p>Remote tools are always treated as mutating because their side effects cannot be
 * inspected here; the local name is a sanitized, collision-free alias that still identifies
 * both server and tool to the model.</p>
 */
final class McpTool implements Tool {

    /** Empty object schema used when the server publishes no input schema. */
    private static final String EMPTY_SCHEMA = ToolSchemaBuilder.object().build();

    private final McpExtension server;
    private final McpToolInfo remote;
    private final String name;
    private final McpCaller caller;

    /**
     * Creates the adapter for one remote tool.
     *
     * @param server owning extension configuration with endpoint and headers
     * @param remote server-published tool metadata
     * @param name local, collision-free tool name assigned by the catalog
     * @param caller transport used to reach the server
     */
    McpTool(McpExtension server, McpToolInfo remote, String name, McpCaller caller) {
        this.server = server;
        this.remote = remote;
        this.name = name;
        this.caller = caller;
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return name;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A missing remote description falls back to a generated one so the catalog entry
     * always explains where the tool comes from.</p>
     */
    @Override public String description() {
        return remote.description.isEmpty()
                ? "MCP tool " + remote.name + " from " + server.name
                : remote.description + "\nServer: " + server.name;
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return remote.inputSchemaJson.isEmpty() ? EMPTY_SCHEMA : remote.inputSchemaJson;
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        ToolPresentation generic = ToolPresentation.generic(name,
                arguments == null ? new JSONObject() : arguments);
        return new ToolPresentation(server.name + " · " + remote.name, ToolIcon.MCP,
                generic.action, ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link McpCaller#call}; MCP-level errors reported in the response text
     * and transport failures both become failure results.</p>
     */
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
