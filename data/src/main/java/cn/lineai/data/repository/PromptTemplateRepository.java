package cn.lineai.data.repository;

import cn.lineai.data.R;
import cn.lineai.model.ChatMode;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.resource.ResourceProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PromptTemplateRepository {
    public static final String ID_SYSTEM_PROMPT = "systemPrompt";
    public static final String ID_WORK_DIRECTORY = "workDirectory";
    public static final String ID_TONE_CODING = "toneCoding";
    public static final String ID_TONE_CHAT = "toneChat";
    public static final String ID_LEARNING_CONTEXT = "learningContext";
    public static final String ID_CONTEXT_COMPACTION = "contextCompaction";
    public static final String ID_CHAT_MODE_CHAT = "chatModeChat";
    public static final String ID_CHAT_MODE_PLAN = "chatModePlan";
    public static final String ID_CHAT_MODE_AGENT = "chatModeAgent";
    public static final String ID_CHAT_MODE_CONTROL = "chatModeControl";
    public static final String ID_MODEL_IDENTITY = "modelIdentity";
    public static final String ID_TODO_STATE = "todoState";
    public static final String ID_TODO_USAGE = "todoUsage";
    public static final String ID_AGENT_ROLE_EXPLORE_REMOTE = "agentRoleExploreRemote";
    public static final String ID_AGENT_ROLE_CODING_REMOTE = "agentRoleCodingRemote";
    public static final String ID_AGENT_ROLE_EXPLORE_LOCAL = "agentRoleExploreLocal";
    public static final String ID_AGENT_ROLE_CODING_LOCAL = "agentRoleCodingLocal";
    public static final String ID_AGENT_SYSTEM_PROMPT = "agentSystemPrompt";
    public static final String ID_IMAGE_UNDERSTANDING_TOOL_SYSTEM = "imageUnderstandingToolSystem";
    public static final String ID_CONTEXT_COMPACTION_SUMMARY_PREFIX = "contextCompactionSummaryPrefix";
    public static final String ID_CONTEXT_COMPACTION_RESPONSES_FALLBACK = "contextCompactionResponsesFallback";

    private static final String KEY_PREFIX = "@linecode_prompt_template_";
    private static final List<Definition> DEFINITIONS = buildDefinitions();

    private final ResourceProvider resourceProvider;
    private final SettingsRepository settingsRepository;

    public PromptTemplateRepository(ResourceProvider resourceProvider, SettingsRepository settingsRepository) {
        this.resourceProvider = resourceProvider;
        this.settingsRepository = settingsRepository;
    }

    public synchronized List<PromptTemplateItem> getTemplates() {
        ArrayList<PromptTemplateItem> items = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            String defaultText = defaultText(definition);
            String currentText = settingsRepository.getString(key(definition.id), defaultText);
            items.add(new PromptTemplateItem(
                    definition.id,
                    resourceProvider.getString(definition.titleResId),
                    resourceProvider.getString(definition.descriptionResId),
                    resolveSourceLabel(definition),
                    definition.variables,
                    defaultText,
                    currentText,
                    !defaultText.equals(currentText)
            ));
        }
        return items;
    }

    public synchronized String getTemplateText(String id) {
        Definition definition = definitionFor(id);
        String defaultText = defaultText(definition);
        return settingsRepository.getString(key(definition.id), defaultText);
    }

    public synchronized String getDefaultTemplateText(String id) {
        return defaultText(definitionFor(id));
    }

    public synchronized void saveTemplate(String id, String value) {
        definitionFor(id);
        settingsRepository.setString(key(id), value == null ? "" : value);
    }

    public synchronized void resetTemplate(String id) {
        definitionFor(id);
        settingsRepository.remove(key(id));
    }

    public static List<String> templateIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            ids.add(definition.id);
        }
        return Collections.unmodifiableList(ids);
    }

    private String resolveSourceLabel(Definition definition) {
        if (definition.sourceLabelResId != 0) {
            return resourceProvider.getString(definition.sourceLabelResId);
        }
        return definition.sourceLabel;
    }

    private Definition definitionFor(String id) {
        String safeId = id == null ? "" : id;
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(safeId)) {
                return definition;
            }
        }
        throw new IllegalArgumentException(resourceProvider.getString(R.string.prompt_template_error_unknown, safeId));
    }

    private String key(String id) {
        return KEY_PREFIX + id;
    }

    private String readAsset(String path) {
        try {
            InputStream input = resourceProvider.openAsset(path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalStateException(resourceProvider.getString(R.string.prompt_template_error_read_failed, path), e);
        }
    }

    private String defaultText(Definition definition) {
        if (definition.assetPath.length() > 0) {
            return readAsset(definition.assetPath);
        }
        return definition.defaultText;
    }

    private static List<Definition> buildDefinitions() {
        ArrayList<Definition> definitions = new ArrayList<>();
        definitions.add(new Definition(
                ID_SYSTEM_PROMPT,
                R.string.prompt_template_system_prompt_title,
                R.string.prompt_template_system_prompt_description,
                "prompts/system-prompt-template.txt",
                "TOOLS_CONTEXT", "TONE_CONTEXT", "CHAT_MODE_CONTEXT", "WORK_DIRECTORY_CONTEXT", "LEARNING_CONTEXT", "MODEL_IDENTITY"
        ));
        definitions.add(new Definition(
                ID_WORK_DIRECTORY,
                R.string.prompt_template_work_directory_title,
                R.string.prompt_template_work_directory_description,
                "prompts/work-directory-template.txt",
                "HOME_PATH", "LINECODE_ROOT", "GLOBAL_SKILLS_ROOT", "WORKSPACE_PRIVATE_ROOT", "WORKSPACE_SKILLS_ROOT"
        ));
        definitions.add(new Definition(
                ID_TONE_CODING,
                R.string.prompt_template_tone_coding_title,
                R.string.prompt_template_tone_coding_description,
                "prompts/tone-coding-template.txt"
        ));
        definitions.add(new Definition(
                ID_TONE_CHAT,
                R.string.prompt_template_tone_chat_title,
                R.string.prompt_template_tone_chat_description,
                "prompts/tone-chat-template.txt"
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_CHAT,
                R.string.prompt_template_chat_mode_chat_title,
                R.string.prompt_template_chat_mode_chat_description,
                R.string.prompt_template_source_builtin_chat,
                ChatMode.promptContext(ChatMode.CHAT)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_PLAN,
                R.string.prompt_template_chat_mode_plan_title,
                R.string.prompt_template_chat_mode_plan_description,
                R.string.prompt_template_source_builtin_plan,
                ChatMode.promptContext(ChatMode.PLAN)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_AGENT,
                R.string.prompt_template_chat_mode_agent_title,
                R.string.prompt_template_chat_mode_agent_description,
                R.string.prompt_template_source_builtin_agent,
                ChatMode.promptContext(ChatMode.AGENT)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_CONTROL,
                R.string.prompt_template_chat_mode_control_title,
                R.string.prompt_template_chat_mode_control_description,
                R.string.prompt_template_source_builtin_control,
                ChatMode.promptContext(ChatMode.CONTROL)
        ));
        definitions.add(new Definition(
                ID_LEARNING_CONTEXT,
                R.string.prompt_template_learning_context_title,
                R.string.prompt_template_learning_context_description,
                "prompts/learning-context-template.txt",
                "WORKING_MEMORY_SECTION", "MEMORY_SECTION", "HISTORY_SECTION", "SKILL_PATHS_SECTION",
                "SKILLS_SECTION", "PRIVATE_BOUNDARY_SECTION"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION,
                R.string.prompt_template_context_compaction_title,
                R.string.prompt_template_context_compaction_description,
                "prompts/context-compaction-template.txt"
        ));
        definitions.add(new Definition(
                ID_MODEL_IDENTITY,
                R.string.prompt_template_model_identity_title,
                R.string.prompt_template_model_identity_description,
                "prompts/model-identity-template.txt",
                "MODEL_ID", "MODEL_NAME", "MODEL_PROVIDER", "MODEL_PROTOCOL"
        ));
        definitions.add(new Definition(
                ID_TODO_STATE,
                R.string.prompt_template_todo_state_title,
                R.string.prompt_template_todo_state_description,
                "prompts/todo-state-template.txt",
                "TODO_LIST"
        ));
        definitions.add(new Definition(
                ID_TODO_USAGE,
                R.string.prompt_template_todo_usage_title,
                R.string.prompt_template_todo_usage_description,
                "prompts/todo-usage-template.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_EXPLORE_REMOTE,
                R.string.prompt_template_agent_role_explore_remote_title,
                R.string.prompt_template_agent_role_explore_remote_description,
                "prompts/agent-role-explore-remote.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_CODING_REMOTE,
                R.string.prompt_template_agent_role_coding_remote_title,
                R.string.prompt_template_agent_role_coding_remote_description,
                "prompts/agent-role-coding-remote.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_EXPLORE_LOCAL,
                R.string.prompt_template_agent_role_explore_local_title,
                R.string.prompt_template_agent_role_explore_local_description,
                "prompts/agent-role-explore-local.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_CODING_LOCAL,
                R.string.prompt_template_agent_role_coding_local_title,
                R.string.prompt_template_agent_role_coding_local_description,
                "prompts/agent-role-coding-local.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_SYSTEM_PROMPT,
                R.string.prompt_template_agent_system_prompt_title,
                R.string.prompt_template_agent_system_prompt_description,
                "prompts/agent-system-prompt-template.txt",
                "ROLE_PROMPT", "TASK_DESCRIPTION", "WORKSPACE_CONTEXT", "SCOPE_CONTEXT", "EXTENSIONS_CONTEXT", "TOOLS_CONTEXT"
        ));
        definitions.add(new Definition(
                ID_IMAGE_UNDERSTANDING_TOOL_SYSTEM,
                R.string.prompt_template_image_understanding_tool_system_title,
                R.string.prompt_template_image_understanding_tool_system_description,
                "prompts/image-understanding-tool-system.txt"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION_SUMMARY_PREFIX,
                R.string.prompt_template_context_compaction_summary_prefix_title,
                R.string.prompt_template_context_compaction_summary_prefix_description,
                "prompts/context-compaction-summary-prefix.txt",
                "SUMMARY"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION_RESPONSES_FALLBACK,
                R.string.prompt_template_context_compaction_responses_fallback_title,
                R.string.prompt_template_context_compaction_responses_fallback_description,
                "prompts/context-compaction-responses-fallback.txt"
        ));
        return Collections.unmodifiableList(definitions);
    }

    private static final class Definition {
        final String id;
        final int titleResId;
        final int descriptionResId;
        final int sourceLabelResId;
        final String assetPath;
        final String sourceLabel;
        final String defaultText;
        final String[] variables;

        Definition(String id, int titleResId, int descriptionResId, String assetPath, String... variables) {
            this.id = id;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
            this.assetPath = assetPath;
            this.sourceLabel = assetPath;
            this.sourceLabelResId = 0;
            this.defaultText = "";
            this.variables = variables == null ? new String[0] : variables;
        }

        Definition(String id, int titleResId, int descriptionResId, int sourceLabelResId, String defaultText) {
            this.id = id;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
            this.assetPath = "";
            this.sourceLabel = "";
            this.sourceLabelResId = sourceLabelResId;
            this.defaultText = defaultText == null ? "" : defaultText;
            this.variables = new String[0];
        }
    }
}
