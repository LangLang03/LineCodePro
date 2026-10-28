package cn.lineai.engine.prompt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The single template registry: id, asset file and declared placeholders. */
public enum PromptId {

    SYSTEM_PROMPT("systemPrompt", "system-prompt-template.txt", "TOOLS_CONTEXT", "TONE_CONTEXT",
            "CHAT_MODE_CONTEXT", "WORK_DIRECTORY_CONTEXT", "LEARNING_CONTEXT", "MODEL_IDENTITY",
            "TODO_STATE"),
    WORK_DIRECTORY("workDirectory", "work-directory-template.txt", "HOME_PATH", "LINECODE_ROOT",
            "GLOBAL_SKILLS_ROOT", "WORKSPACE_PRIVATE_ROOT", "WORKSPACE_SKILLS_ROOT"),
    TONE_CODING("toneCoding", "tone-coding-template.txt"),
    TONE_CHAT("toneChat", "tone-chat-template.txt"),
    MINIMAL_PROMPT("minimalPrompt", "minimal-prompt-template.txt", "CHAT_MODE_CONTEXT"),
    LEARNING_CONTEXT("learningContext", "learning-context-template.txt",
            "WORKING_MEMORY_SECTION", "MEMORY_SECTION", "HISTORY_SECTION", "SKILL_PATHS_SECTION",
            "SKILLS_SECTION", "PRIVATE_BOUNDARY_SECTION"),
    CHAT_MODE_CHAT("chatModeChat", "chat-mode-chat.txt"),
    CHAT_MODE_PLAN("chatModePlan", "chat-mode-plan.txt"),
    CHAT_MODE_AGENT("chatModeAgent", "chat-mode-agent.txt"),
    CHAT_MODE_CONTROL("chatModeControl", "chat-mode-control.txt"),
    CONTEXT_COMPACTION("contextCompaction", "context-compaction-template.txt"),
    MODEL_IDENTITY("modelIdentity", "model-identity-template.txt", "MODEL_ID", "MODEL_NAME",
            "MODEL_PROVIDER", "MODEL_PROTOCOL"),
    TODO_STATE("todoState", "todo-state-template.txt", "TODO_LIST"),
    TODO_USAGE("todoUsage", "todo-usage-template.txt"),
    AGENT_ROLE_EXPLORE_REMOTE("agentRoleExploreRemote", "agent-role-explore-remote.txt"),
    AGENT_ROLE_CODING_REMOTE("agentRoleCodingRemote", "agent-role-coding-remote.txt"),
    AGENT_ROLE_EXPLORE_LOCAL("agentRoleExploreLocal", "agent-role-explore-local.txt"),
    AGENT_ROLE_CODING_LOCAL("agentRoleCodingLocal", "agent-role-coding-local.txt"),
    AGENT_SYSTEM_PROMPT("agentSystemPrompt", "agent-system-prompt-template.txt", "ROLE_PROMPT",
            "TASK_DESCRIPTION", "WORKSPACE_CONTEXT", "SCOPE_CONTEXT", "EXTENSIONS_CONTEXT",
            "TOOLS_CONTEXT"),
    IMAGE_UNDERSTANDING_TOOL_SYSTEM("imageUnderstandingToolSystem", "image-understanding-tool-system.txt"),
    CONTEXT_COMPACTION_SUMMARY_PREFIX("contextCompactionSummaryPrefix",
            "context-compaction-summary-prefix.txt", "SUMMARY"),
    CONTEXT_COMPACTION_RESPONSES_FALLBACK("contextCompactionResponsesFallback", "context-compaction-responses-fallback.txt");

    public final String id;
    public final String file;
    private final List<String> variables;

    PromptId(String id, String file, String... variables) {
        this.id = id;
        this.file = file;
        this.variables = Collections.unmodifiableList(Arrays.asList(variables));
    }

    public List<String> variables() {
        return variables;
    }

    public static PromptId byId(String id) {
        for (PromptId prompt : values()) {
            if (prompt.id.equals(id)) return prompt;
        }
        throw new IllegalArgumentException("Unknown prompt: " + id);
    }
}
