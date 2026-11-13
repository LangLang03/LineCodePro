package cn.lineai.engine.prompt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The single template registry: id, asset file and declared placeholders.
 *
 * <p>Each constant maps one packaged template to its stable string id (the override key) and to
 * the placeholders the renderer must be given. Adding a constant requires a matching asset under
 * {@code assets/prompts}; removing one breaks persisted overrides keyed on the id.</p>
 */
public enum PromptId {

    /** Top-level chat system prompt, assembled from the context sections below. */
    SYSTEM_PROMPT("systemPrompt", "system-prompt-template.txt", "TOOLS_CONTEXT", "TONE_CONTEXT",
            "CHAT_MODE_CONTEXT", "WORK_DIRECTORY_CONTEXT", "LEARNING_CONTEXT", "MODEL_IDENTITY",
            "TODO_STATE"),
    /** Working-directory facts block injected into the system prompt. */
    WORK_DIRECTORY("workDirectory", "work-directory-template.txt", "HOME_PATH", "LINECODE_ROOT",
            "GLOBAL_SKILLS_ROOT", "WORKSPACE_PRIVATE_ROOT", "WORKSPACE_SKILLS_ROOT"),
    /** Tone instructions for coding sessions. */
    TONE_CODING("toneCoding", "tone-coding-template.txt"),
    /** Tone instructions for chat sessions. */
    TONE_CHAT("toneChat", "tone-chat-template.txt"),
    /** Bare system prompt used by minimal mode. */
    MINIMAL_PROMPT("minimalPrompt", "minimal-prompt-template.txt"),
    /** Learning memory, history and skills sections of the system prompt. */
    LEARNING_CONTEXT("learningContext", "learning-context-template.txt",
            "WORKING_MEMORY_SECTION", "MEMORY_SECTION", "HISTORY_SECTION", "SKILL_PATHS_SECTION",
            "SKILLS_SECTION", "PRIVATE_BOUNDARY_SECTION"),
    /** Session-mode instructions for chat mode. */
    CHAT_MODE_CHAT("chatModeChat", "chat-mode-chat.txt"),
    /** Session-mode instructions for plan mode. */
    CHAT_MODE_PLAN("chatModePlan", "chat-mode-plan.txt"),
    /** Session-mode instructions for agent mode. */
    CHAT_MODE_AGENT("chatModeAgent", "chat-mode-agent.txt"),
    /** Session-mode instructions for control mode. */
    CHAT_MODE_CONTROL("chatModeControl", "chat-mode-control.txt"),
    /** Instruction template that drives context compaction. */
    CONTEXT_COMPACTION("contextCompaction", "context-compaction-template.txt"),
    /** Model identity block naming the active model, provider and protocol. */
    MODEL_IDENTITY("modelIdentity", "model-identity-template.txt", "MODEL_ID", "MODEL_NAME",
            "MODEL_PROVIDER", "MODEL_PROTOCOL"),
    /** Current todo list rendered into the system prompt. */
    TODO_STATE("todoState", "todo-state-template.txt", "TODO_LIST"),
    /** Usage instructions shown alongside the todo tool. */
    TODO_USAGE("todoUsage", "todo-usage-template.txt"),
    /** Role prompt for remote explore agents. */
    AGENT_ROLE_EXPLORE_REMOTE("agentRoleExploreRemote", "agent-role-explore-remote.txt"),
    /** Role prompt for remote coding agents. */
    AGENT_ROLE_CODING_REMOTE("agentRoleCodingRemote", "agent-role-coding-remote.txt"),
    /** Role prompt for local explore agents. */
    AGENT_ROLE_EXPLORE_LOCAL("agentRoleExploreLocal", "agent-role-explore-local.txt"),
    /** Role prompt for local coding agents. */
    AGENT_ROLE_CODING_LOCAL("agentRoleCodingLocal", "agent-role-coding-local.txt"),
    /** Sub-agent system prompt assembled from role, task and workspace sections. */
    AGENT_SYSTEM_PROMPT("agentSystemPrompt", "agent-system-prompt-template.txt", "ROLE_PROMPT",
            "TASK_DESCRIPTION", "WORKSPACE_CONTEXT", "SCOPE_CONTEXT", "EXTENSIONS_CONTEXT",
            "TOOLS_CONTEXT"),
    /** System prompt for the image understanding tool. */
    IMAGE_UNDERSTANDING_TOOL_SYSTEM("imageUnderstandingToolSystem", "image-understanding-tool-system.txt"),
    /** Prefix wrapping a stored compaction summary for reuse as compaction input. */
    CONTEXT_COMPACTION_SUMMARY_PREFIX("contextCompactionSummaryPrefix",
            "context-compaction-summary-prefix.txt", "SUMMARY"),
    /** Fallback instruction when compaction runs against a Responses-only provider. */
    CONTEXT_COMPACTION_RESPONSES_FALLBACK("contextCompactionResponsesFallback", "context-compaction-responses-fallback.txt"),
    /** Draft template from which the model proposes a new agent definition. */
    AGENT_DRAFT("agentDraft", "agent-draft-template.txt", "USER_NEED", "AVAILABLE_TOOLS");

    /** Stable string identifier, used as the override key. */
    public final String id;
    /** Asset file name under {@code prompts/}. */
    public final String file;
    private final List<String> variables;

    /**
     * Creates a constant.
     *
     * @param id stable string identifier
     * @param file asset file name under {@code prompts/}
     * @param variables declared placeholder names, in declaration order
     */
    PromptId(String id, String file, String... variables) {
        this.id = id;
        this.file = file;
        this.variables = Collections.unmodifiableList(Arrays.asList(variables));
    }

    /**
     * Returns the declared placeholder names, in declaration order.
     *
     * @return unmodifiable placeholder list
     */
    public List<String> variables() {
        return variables;
    }

    /**
     * Resolves a constant by its stable string id.
     *
     * @param id stable identifier as stored in overrides
     * @return matching constant
     * @throws IllegalArgumentException when no constant carries the id
     */
    public static PromptId byId(String id) {
        for (PromptId prompt : values()) {
            if (prompt.id.equals(id)) return prompt;
        }
        throw new IllegalArgumentException("Unknown prompt: " + id);
    }
}
