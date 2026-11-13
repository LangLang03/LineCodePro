package cn.lineai.feature.chat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolRegistry;

/**
 * Approval decisions for one conversation: read-only tools always run, mutating tools need
 * the permission mode to be auto or an explicit "always allow" from the user.
 */
final class ApprovalPolicy {

    private final Supplier<ToolRegistry> registry;
    private final Supplier<String> mode;
    private final Set<String> alwaysAllowed = new HashSet<>();

    ApprovalPolicy(Supplier<ToolRegistry> registry, Supplier<String> mode) {
        this.registry = registry;
        this.mode = mode;
    }

    /** True when the whole batch can run without asking the user. */
    boolean autoApprove(List<ToolCall> calls) {
        if (calls.isEmpty() || ToolModes.AUTO.equals(mode.get())) {
            return true;
        }
        for (ToolCall call : calls) {
            Tool tool = registry.get().find(call.name);
            boolean readOnly = tool != null && !tool.mutating();
            if (!readOnly && !alwaysAllowed.contains(call.name)) {
                return false;
            }
        }
        return true;
    }

    /** "Always allow" for the pending batch; lives for the process lifetime. */
    void allowAll(List<ToolApprovalItem> approvals) {
        for (ToolApprovalItem item : approvals) {
            alwaysAllowed.add(item.call.name);
        }
    }
}
