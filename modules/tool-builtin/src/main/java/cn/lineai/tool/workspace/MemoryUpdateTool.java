package cn.lineai.tool.workspace;

import org.json.JSONObject;

import cn.lineai.tool.api.MemorySink;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;
import cn.lineai.tool.api.ToolSchemaBuilder;

/**
 * Saves a durable user/project/environment memory when the model explicitly asks to remember.
 *
 * <p>The tool guards the memory store against noise: titles and content are length-capped,
 * unknown scopes fall back to {@code user}, and content that looks like credentials is
 * rejected outright so secrets never reach durable storage.</p>
 */
public final class MemoryUpdateTool implements Tool {

    /** {@inheritDoc} */
    @Override public String name() { return "memory_update"; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Save a durable long-term memory for future sessions. Call only when the user "
                + "states a lasting preference, project constraint, or environment fact that "
                + "should persist across chats. Do not save one-off tasks, temporary progress, "
                + "logs, secrets, or ordinary conversation content. scope: user (cross-project "
                + "preference), project (this workspace only), environment (device/build setup).";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return ToolSchemaBuilder.object()
                .string("title", "Short label for this memory, at most 40 chars")
                .string("content", "Independent durable memory statement, max 320 chars")
                .enumString("scope", "user | project | environment",
                        "user", "project", "environment")
                .required("title").required("content").build();
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return true; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        String title = arguments == null ? "" : arguments.optString("title", "");
        return new ToolPresentation("Save memory", ToolIcon.MEMORY, title,
                ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rejects empty title or content, content over the length caps is clipped, and content
     * containing credential-like keywords is refused rather than stored.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        MemorySink sink = context == null ? null : context.memory();
        if (sink == null) return ToolResult.failure("", "Memory store is not available");
        String title = arguments == null ? "" : arguments.optString("title", "").trim();
        String content = arguments == null ? "" : arguments.optString("content", "").trim();
        String scope = arguments == null ? "user" : arguments.optString("scope", "user");
        if (title.isEmpty()) return ToolResult.failure("", "Memory title cannot be empty");
        if (content.isEmpty()) return ToolResult.failure("", "Memory content cannot be empty");
        if (title.length() > 40) title = title.substring(0, 40);
        if (content.length() > 320) content = content.substring(0, 320);
        if (!"project".equals(scope) && !"environment".equals(scope)) scope = "user";
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("password") || lower.contains("api key")
                || lower.contains("secret") || lower.contains("token")) {
            return ToolResult.failure("", "Refused to store sensitive content as memory");
        }
        sink.save(scope, title, content);
        return ToolResult.success("", "Memory saved");
    }
}
