package cn.lineai.ai.prompt;

import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.McpToolConfig;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolPromptRenderer {
    private static final String PROCESSING_BOUNDARY_RULE = "\nAfter completing the tool work, emit the exact internal marker <LEOF \"完成了任务重写\"> once, immediately before the final answer. "
            + "The app hides this marker, closes the processing section, and displays subsequent text as the final answer. "
            + "Never emit a separate <L prefix, put the marker in a tool call, or call tools after the marker.";

    public static String renderToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, ToolInfo> toolByName,
            boolean nativeToolProtocol
    ) {
        return renderToolPrompt(ToolSettingsStore.EXECUTION_LOCAL, configs, enabled, toolByName, nativeToolProtocol);
    }

    public static String renderToolPrompt(
            String executionMode,
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, ToolInfo> toolByName,
            boolean nativeToolProtocol
    ) {
        if (enabled.isEmpty()) {
            return "## Available Tools\nNo tools are available. No model tools configured, tool groups disabled, or all tools restricted by current permission mode.";
        }
        String mode = ToolSettingsRepository.normalizeExecutionMode(executionMode);
        if (ToolSettingsStore.EXECUTION_SSH.equals(mode)) {
            return renderRemoteToolPrompt(mode, configs, enabled, toolByName, nativeToolProtocol);
        }
        if (ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(mode)) {
            return renderRemoteToolPrompt(mode, configs, enabled, toolByName, nativeToolProtocol);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## Available Tools\nThe following tool list is dynamically generated from current MCP settings, permission mode, execution target, and registered tools. Unlisted tools are unavailable; tool execution must comply with the current permission mode.\n\n");
        List<McpToolConfig> promptConfigs = orderedConfigs(configs);
        HashSet<String> renderedTools = new HashSet<>();
        for (McpToolConfig config : promptConfigs) {
            ArrayList<String> tools = new ArrayList<>();
            for (String tool : config.getTools()) {
                if (enabled.contains(tool)) {
                    tools.add(tool);
                    renderedTools.add(tool);
                }
            }
            if (tools.isEmpty()) {
                continue;
            }
            java.util.Collections.sort(tools);
            builder.append("### ").append(config.getName()).append('\n');
            for (String toolName : tools) {
                ToolInfo tool = toolByName == null ? null : toolByName.get(toolName);
                builder.append("  - ").append(toolName);
                if (tool != null) {
                    builder.append(" [").append(categoryLabel(tool.getCategory()));
                    if (tool.needsConfirmation()) {
                        builder.append(", confirmation depends on permission mode");
                    }
                    builder.append("]: ").append(tool.getDescription()).append('\n');
                    try {
                        builder.append("    Parameters: ").append(StableJson.stringify(tool.getParameters())).append('\n');
                    } catch (Exception ignored) {
                        builder.append("    Parameters: {}\n");
                    }
                } else {
                    builder.append('\n');
                }
            }
            builder.append('\n');
        }
        appendExtensionTools(builder, enabled, renderedTools, toolByName);
        if (nativeToolProtocol) {
            builder.append("Tool calls are provided by the current model protocol's native tools/function calling mechanism. When you need to read, write, search, generate images, or list directories, you must use native tool calls; do not output tool call JSON, XML, <tool_calls>, or Markdown code blocks in the response text.")
                    .append(PROCESSING_BOUNDARY_RULE);
        } else {
            builder.append("Tool call format is locked: when you need to call a tool, you must output <tool_calls><tool_call name=\"tool_name\"><argument name=\"param_name\">value</argument></tool_calls>.")
                    .append("Do not output OpenAI tool_calls JSON, Markdown code blocks, or natural language wrappers.")
                    .append(PROCESSING_BOUNDARY_RULE);
        }
        return builder.toString().trim();
    }

    private static String renderRemoteToolPrompt(
            String executionMode,
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, ToolInfo> toolByName,
            boolean nativeToolProtocol
    ) {
        boolean isSsh = ToolSettingsStore.EXECUTION_SSH.equals(executionMode);
        StringBuilder builder = new StringBuilder();
        builder.append("## Available Tools\n");
        if (isSsh) {
            builder.append("The current execution target is SSH Shell. Local file read/write and file search are disabled.\n")
                    .append("Agent, Agent Pipeline, and todo list remain available; sub-agents can only perform file operations via shell_execute within the SSH environment.\n")
                    .append("Image understanding reads SSH workspace images via SFTP; web search, image generation, and app-side custom HTTP MCPs remain available as tools when enabled.\n")
                    .append("When the session mode is Control, phone control tools (phone_screenshot, phone_click, etc.) remain available.\n")
                    .append("Do not reference the app's private home working directory; if the system prompt provides an SSH project directory, you must operate within that directory.\n")
                    .append("To read, write, list directories, or search files, use shell commands within the SSH environment.\n\n");
        } else {
            builder.append("The current execution target is Terminal Provider. Local file read/write and file search are disabled.\n")
                    .append("Agent, Agent Pipeline, and todo list remain available; sub-agents can only perform file operations via shell_execute within the terminal provider environment.\n")
                    .append("Image understanding reads terminal provider environment images via IPC; web search, image generation, and app-side custom HTTP MCPs remain available as tools when enabled.\n")
                    .append("When the session mode is Control, phone control tools (phone_screenshot, phone_click, etc.) remain available.\n")
                    .append("Do not reference the app's private home working directory; if the system prompt provides a terminal provider working directory, you must operate within that directory.\n")
                    .append("To read, write, list directories, or search files, use shell commands within the terminal provider environment.\n\n");
        }
        List<McpToolConfig> promptConfigs = orderedConfigs(configs);
        HashSet<String> renderedTools = new HashSet<>();
        for (McpToolConfig config : promptConfigs) {
            ArrayList<String> tools = new ArrayList<>();
            for (String tool : config.getTools()) {
                if (enabled.contains(tool)) {
                    tools.add(tool);
                    renderedTools.add(tool);
                }
            }
            if (tools.isEmpty()) {
                continue;
            }
            java.util.Collections.sort(tools);
            builder.append("### ").append(config.getName()).append('\n');
            for (String toolName : tools) {
                ToolInfo tool = toolByName == null ? null : toolByName.get(toolName);
                builder.append("  - ").append(toolName);
                if (tool != null) {
                    builder.append(" [").append(categoryLabel(tool.getCategory()));
                    if (tool.needsConfirmation()) {
                        builder.append(", confirmation depends on permission mode");
                    }
                    builder.append("]: ").append(tool.getDescription()).append('\n');
                    try {
                        builder.append("    Parameters: ").append(StableJson.stringify(tool.getParameters())).append('\n');
                    } catch (Exception ignored) {
                        builder.append("    Parameters: {}\n");
                    }
                } else {
                    builder.append('\n');
                }
            }
            String supplement = findToolSupplement(config, executionMode, isSsh, toolByName);
            if (supplement != null) {
                builder.append(supplement).append('\n');
            }
            builder.append('\n');
        }
        appendExtensionTools(builder, enabled, renderedTools, toolByName);
        builder.append("After each tool returns, you must continue analyzing the output; if the task is not yet complete, continue calling appropriate tools for the next step.")
                .append("Do not stop after just one or two shell command executions; only respond to the user when you confirm the task is complete, blocked, or requires a user decision.\n");
        if (nativeToolProtocol) {
            builder.append("Tool calls are provided by the current model protocol's native tools/function calling mechanism. Do not output tool call JSON, XML, <tool_calls>, or Markdown code blocks in the response text.");
        } else {
            builder.append("Tool call format is locked: when you need to call a tool, you must output <tool_calls><tool_call name=\"tool_name\"><argument name=\"param_name\">value</argument></tool_calls>.")
                    .append("Do not output OpenAI tool_calls JSON, Markdown code blocks, or natural language wrappers.");
        }
        builder.append(PROCESSING_BOUNDARY_RULE);
        return builder.toString().trim();
    }

    private static void appendExtensionTools(
            StringBuilder builder,
            Set<String> enabled,
            Set<String> renderedTools,
            Map<String, ToolInfo> toolByName
    ) {
        ArrayList<String> extensionTools = new ArrayList<>();
        for (String toolName : enabled) {
            if (!renderedTools.contains(toolName) && ToolNames.isExtensionToolName(toolName)) {
                extensionTools.add(toolName);
            }
        }
        java.util.Collections.sort(extensionTools);
        if (extensionTools.isEmpty()) {
            return;
        }
        builder.append("### Extensions\n");
        for (String toolName : extensionTools) {
            ToolInfo tool = toolByName == null ? null : toolByName.get(toolName);
            builder.append("  - ").append(toolName);
            if (tool != null) {
                builder.append(" [").append(categoryLabel(tool.getCategory())).append("]: ")
                        .append(tool.getDescription()).append('\n');
                try {
                    builder.append("    Parameters: ").append(StableJson.stringify(tool.getParameters())).append('\n');
                } catch (Exception ignored) {
                    builder.append("    Parameters: {}\n");
                }
            } else {
                builder.append('\n');
            }
        }
        builder.append('\n');
    }

    public static String categoryLabel(ToolCategory category) {
        if (category == ToolCategory.GENERATE) {
            return "generate";
        }
        if (category == ToolCategory.WRITE) {
            return "write";
        }
        if (category == ToolCategory.SYSTEM) {
            return "system";
        }
        return "read";
    }

    private static String findToolSupplement(
            McpToolConfig config,
            String executionMode,
            boolean isSsh,
            Map<String, ToolInfo> toolByName
    ) {
        if (config == null || toolByName == null || toolByName.isEmpty()) {
            return null;
        }
        ArrayList<String> toolNames = new ArrayList<>(java.util.Arrays.asList(config.getTools()));
        java.util.Collections.sort(toolNames);
        for (String toolName : toolNames) {
            ToolInfo tool = toolByName.get(toolName);
            if (tool != null) {
                String supplement = tool.promptSupplement(executionMode, isSsh);
                if (supplement != null) {
                    return supplement;
                }
            }
        }
        return null;
    }

    private static List<McpToolConfig> orderedConfigs(List<McpToolConfig> configs) {
        ArrayList<McpToolConfig> ordered = configs == null ? new ArrayList<>() : new ArrayList<>(configs);
        ordered.sort(java.util.Comparator.comparing(McpToolConfig::getId));
        return ordered;
    }
}
