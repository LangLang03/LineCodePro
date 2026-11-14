package cn.lineai.tool.agent;

import java.io.File;
import java.io.IOException;

import org.json.JSONObject;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * One enabled custom agent exposed as a tool.
 *
 * <p>Every invocation starts an asynchronous sub agent session: the tool returns a handle
 * immediately and the sub agent's final output reaches the parent loop through the runtime
 * event inbox. A {@code persistent=true} invocation creates a messageable, revivable
 * session; the default is a one-shot transient run. The tool description embeds the agent's
 * trigger, tool names and a clipped prompt so the outer model can decide when delegation is
 * appropriate without reading the full prompt.</p>
 *
 * <p>The workspace argument is mandatory: the invocation resolves it against the caller's
 * workspace root, validates that it is an existing directory and binds the session to it, so
 * {@code "."} inside the sub agent is the bound directory rather than the parent's root. The
 * containment rules are the file tools' own, which is why a bad binding fails the invocation
 * instead of creating a session. SSH and terminal-provider workspaces are not supported: their
 * tools run on a remote host, so only {@code "."} is accepted there and the session inherits
 * the parent workspace.</p>
 */
final class AgentTool implements Tool {

    private final AgentExtension agent;
    private final String name;
    private final AgentRuntime runtime;

    /**
     * Creates the tool.
     *
     * @param agent extension describing the custom agent
     * @param name local, collision-free tool name assigned by {@link AgentTools}
     * @param runtime runtime launching and tracking the sub agent session
     */
    AgentTool(AgentExtension agent, String name, AgentRuntime runtime) {
        this.agent = agent;
        this.name = name;
        this.runtime = runtime;
    }

    /** {@inheritDoc} */
    @Override public String name() { return name; }

    /**
     * {@inheritDoc}
     *
     * <p>Composes trigger, tool list and prompt excerpt; the prompt is clipped to 900
     * characters to bound the catalog size.</p>
     */
    @Override public String description() {
        StringBuilder text = new StringBuilder();
        text.append("Invoke the custom Agent \"").append(agent.name).append("\". The agent runs")
                .append(" asynchronously and its final output is delivered back to you.")
                .append("\nThe session is bound to the directory passed in workspace: \".\" and")
                .append(" relative paths inside the session resolve there.");
        if (!agent.trigger.isEmpty()) text.append("\nTrigger: ").append(agent.trigger);
        if (!agent.toolNames.isEmpty()) text.append("\nTools: ").append(agent.toolNames);
        String prompt = agent.prompt.length() > 900
                ? agent.prompt.substring(0, 900) : agent.prompt;
        if (!prompt.isEmpty()) text.append("\nCapabilities: ").append(prompt);
        return text.toString();
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("task", "Task handed to the agent")
                .string("context", "Optional context")
                .string("workspace", "Workspace directory the sub agent is bound to, relative"
                        + " to your workspace root; \".\" binds it to the same root. The"
                        + " directory must already exist.")
                .bool("persistent", "Create a messageable session that survives this turn and"
                        + " accepts follow-up messages; default false (one-shot run)")
                .required("task").required("workspace").build();
    }

    /**
     * Agent invocations are workspace-agnostic: a local dispatch binds the session to a local
     * directory, while a remote one (SSH or terminal-provider guest) inherits the parent
     * workspace because those modes cannot bind locally, so the tool stays visible everywhere.
     */
    @Override public boolean needsLocalWorkspace() { return false; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation(agent.name.isEmpty() ? agent.slug : agent.name,
                ToolIcon.AGENT_SPAWN, arguments == null ? "" : arguments.optString("task", ""),
                ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>An empty task or workspace is rejected locally; the workspace argument is resolved and
     * validated before the session starts so a bad binding never creates a session. Optional
     * context is appended to the task text. The returned result is the launch confirmation with
     * the agent identifier and its binding, not the agent's answer.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        if (runtime == null) return ToolResult.failure("", "Sub agent runtime is unavailable");
        String task = arguments == null ? "" : arguments.optString("task", "").trim();
        if (task.isEmpty()) return ToolResult.failure("", "task must not be empty");
        String workspace = arguments == null ? "" : arguments.optString("workspace", "").trim();
        if (workspace.isEmpty()) {
            return ToolResult.failure("", "workspace must not be empty; pass \".\" to bind the"
                    + " sub agent to the current workspace root");
        }
        String binding;
        try {
            binding = resolveBinding(context, workspace);
        } catch (SecurityException error) {
            return ToolResult.failure("", error.getMessage());
        }
        String extra = arguments.optString("context", "").trim();
        if (!extra.isEmpty()) task = task + "\n\nContext:\n" + extra;
        boolean persistent = arguments.optBoolean("persistent", false);
        AgentEntry started = runtime.start(agent, task, persistent, binding);
        if (started == null) return ToolResult.failure("", "Sub agent runtime refused the task");
        return ToolResult.success("", "Started sub agent \"" + started.name + "\" id=\""
                + started.id + "\" (" + (persistent ? "persistent" : "transient")
                + ", status " + started.status.name().toLowerCase(java.util.Locale.ROOT)
                + ", workspace " + (binding.isEmpty() ? "inherited" : "\"" + binding + "\"")
                + "). Its final output will be delivered to you when it finishes"
                + (persistent ? "; send it messages with agent_send and check status with"
                + " agent_list or agent_wait." : "."));
    }

    /**
     * Resolves the model-supplied workspace argument to the canonical directory the session
     * binds to.
     *
     * <p>The path goes through {@link ToolContext#resolvePath}, so containment, the
     * absolute-path rule and the explicit path-protection bypass behave exactly like the file
     * tools. SSH and terminal-provider workspaces are not supported because their tools run on a
     * remote host where a local directory has no meaning; they accept only {@code "."}, which
     * inherits the parent workspace without a local binding.</p>
     *
     * @param context the calling tool context; may be null in headless callers
     * @param requested the model-supplied workspace argument, already trimmed and non-empty
     * @return the canonical local directory, or an empty string when the parent workspace is
     *         inherited
     * @throws SecurityException when the binding is unsupported, escapes the workspace, is
     *         absolute without the bypass switch, or is not an existing directory
     */
    private static String resolveBinding(ToolContext context, String requested) {
        if (context == null) {
            throw new SecurityException("workspace cannot be resolved without a tool context");
        }
        if (context.remoteWorkspace()) {
            if (!".".equals(requested)) {
                throw new SecurityException("workspace binding is not supported in SSH or"
                        + " terminal-provider workspaces; pass \".\" to inherit the remote"
                        + " workspace");
            }
            return "";
        }
        File bound;
        try {
            bound = context.resolvePath(requested).getCanonicalFile();
        } catch (IOException error) {
            throw new SecurityException("Cannot resolve workspace: " + requested, error);
        }
        if (!bound.isDirectory()) {
            throw new SecurityException("workspace is not an existing directory: " + requested);
        }
        return bound.getPath();
    }
}
