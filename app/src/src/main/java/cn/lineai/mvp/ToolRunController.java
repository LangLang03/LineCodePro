package cn.lineai.mvp;

import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ToolRunController {
    private final ToolExecutionCoordinator executionCoordinator;
    private final ToolRegistry toolRegistry;
    private final ToolSettingsRepository toolSettingsRepository;

    public ToolRunController(
            ToolExecutionCoordinator executionCoordinator,
            ToolRegistry toolRegistry,
            ToolSettingsRepository toolSettingsRepository
    ) {
        this.executionCoordinator = executionCoordinator;
        this.toolRegistry = toolRegistry;
        this.toolSettingsRepository = toolSettingsRepository;
    }

    public ToolExecutionCoordinator.ToolExecutionPlan createPlan(List<ToolCall> toolCalls) {
        return executionCoordinator.createPlan(toolCalls);
    }

    public ArrayList<ToolResult> orderedResults(List<ToolCall> toolCalls, HashMap<String, ToolResult> resultById) {
        ArrayList<ToolResult> ordered = new ArrayList<>();
        if (toolCalls == null || resultById == null) {
            return ordered;
        }
        for (ToolCall call : toolCalls) {
            if (call == null) {
                continue;
            }
            ToolResult result = resultById.get(call.getId());
            if (result != null) {
                ordered.add(result);
            }
        }
        return ordered;
    }

    public ArrayList<ToolCall> remainingCalls(List<ToolCall> calls, int startIndex) {
        ArrayList<ToolCall> remaining = new ArrayList<>();
        if (calls == null) {
            return remaining;
        }
        for (int i = Math.max(0, startIndex); i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            if (call != null) {
                remaining.add(call);
            }
        }
        return remaining;
    }

    public boolean shouldPauseForConfirmation(ToolCall call) {
        if (call == null) {
            return false;
        }
        BaseTool tool = toolRegistry.get(call.getName());
        if (tool == null || !tool.requiresConfirmation()) {
            return false;
        }
        PermissionResult permission = toolSettingsRepository.canExecuteTool(tool.getName(), tool.getCategory());
        if (!permission.isAllowed()) {
            return false;
        }
        return "file_delete".equals(tool.getName()) || toolSettingsRepository.needsConfirmation(tool.getName());
    }
}
