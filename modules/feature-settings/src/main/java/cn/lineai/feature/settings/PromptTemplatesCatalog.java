package cn.lineai.feature.settings;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.engine.prompt.PromptId;
import cn.lineai.engine.prompt.PromptStore;
import cn.lineai.ui.i18n.R;

/** Maps the packaged prompt registry onto the editable list, in the legacy display order. */
public final class PromptTemplatesCatalog {

    private static final PromptId[] ORDER = {
            PromptId.SYSTEM_PROMPT,
            PromptId.WORK_DIRECTORY,
            PromptId.TONE_CODING,
            PromptId.TONE_CHAT,
            PromptId.CHAT_MODE_CHAT,
            PromptId.CHAT_MODE_PLAN,
            PromptId.CHAT_MODE_AGENT,
            PromptId.CHAT_MODE_CONTROL,
            PromptId.CONTEXT_COMPACTION,
            PromptId.MODEL_IDENTITY,
            PromptId.TODO_STATE,
            PromptId.TODO_USAGE,
            PromptId.AGENT_ROLE_EXPLORE_REMOTE,
            PromptId.AGENT_ROLE_CODING_REMOTE,
            PromptId.AGENT_ROLE_EXPLORE_LOCAL,
            PromptId.AGENT_ROLE_CODING_LOCAL,
            PromptId.AGENT_SYSTEM_PROMPT,
            PromptId.IMAGE_UNDERSTANDING_TOOL_SYSTEM,
            PromptId.CONTEXT_COMPACTION_SUMMARY_PREFIX,
            PromptId.CONTEXT_COMPACTION_RESPONSES_FALLBACK
    };

    private PromptTemplatesCatalog() {
    }

    public static List<PromptTemplateEntry> entries(PromptStore store, android.content.Context context) {
        List<PromptTemplateEntry> entries = new ArrayList<>(ORDER.length);
        for (PromptId prompt : ORDER) {
            entries.add(new PromptTemplateEntry(
                    prompt.id,
                    title(prompt),
                    description(prompt),
                    source(prompt, context),
                    prompt.variables().toArray(new String[0]),
                    store.defaultText(prompt),
                    store.current(prompt)));
        }
        return entries;
    }

    public static PromptId prompt(String id) {
        return PromptId.byId(id);
    }

    static int title(PromptId prompt) {
        switch (prompt) {
            case SYSTEM_PROMPT: return R.string.prompt_template_system_prompt_title;
            case WORK_DIRECTORY: return R.string.prompt_template_work_directory_title;
            case TONE_CODING: return R.string.prompt_template_tone_coding_title;
            case TONE_CHAT: return R.string.prompt_template_tone_chat_title;
            case CHAT_MODE_CHAT: return R.string.prompt_template_chat_mode_chat_title;
            case CHAT_MODE_PLAN: return R.string.prompt_template_chat_mode_plan_title;
            case CHAT_MODE_AGENT: return R.string.prompt_template_chat_mode_agent_title;
            case CHAT_MODE_CONTROL: return R.string.prompt_template_chat_mode_control_title;
            case CONTEXT_COMPACTION: return R.string.prompt_template_context_compaction_title;
            case MODEL_IDENTITY: return R.string.prompt_template_model_identity_title;
            case TODO_STATE: return R.string.prompt_template_todo_state_title;
            case TODO_USAGE: return R.string.prompt_template_todo_usage_title;
            case AGENT_ROLE_EXPLORE_REMOTE: return R.string.prompt_template_agent_role_explore_remote_title;
            case AGENT_ROLE_CODING_REMOTE: return R.string.prompt_template_agent_role_coding_remote_title;
            case AGENT_ROLE_EXPLORE_LOCAL: return R.string.prompt_template_agent_role_explore_local_title;
            case AGENT_ROLE_CODING_LOCAL: return R.string.prompt_template_agent_role_coding_local_title;
            case AGENT_SYSTEM_PROMPT: return R.string.prompt_template_agent_system_prompt_title;
            case IMAGE_UNDERSTANDING_TOOL_SYSTEM: return R.string.prompt_template_image_understanding_tool_system_title;
            case CONTEXT_COMPACTION_SUMMARY_PREFIX: return R.string.prompt_template_context_compaction_summary_prefix_title;
            case CONTEXT_COMPACTION_RESPONSES_FALLBACK: return R.string.prompt_template_context_compaction_responses_fallback_title;
            default: throw new IllegalArgumentException("Unknown prompt: " + prompt.id);
        }
    }

    static int description(PromptId prompt) {
        switch (prompt) {
            case SYSTEM_PROMPT: return R.string.prompt_template_system_prompt_description;
            case WORK_DIRECTORY: return R.string.prompt_template_work_directory_description;
            case TONE_CODING: return R.string.prompt_template_tone_coding_description;
            case TONE_CHAT: return R.string.prompt_template_tone_chat_description;
            case CHAT_MODE_CHAT: return R.string.prompt_template_chat_mode_chat_description;
            case CHAT_MODE_PLAN: return R.string.prompt_template_chat_mode_plan_description;
            case CHAT_MODE_AGENT: return R.string.prompt_template_chat_mode_agent_description;
            case CHAT_MODE_CONTROL: return R.string.prompt_template_chat_mode_control_description;
            case CONTEXT_COMPACTION: return R.string.prompt_template_context_compaction_description;
            case MODEL_IDENTITY: return R.string.prompt_template_model_identity_description;
            case TODO_STATE: return R.string.prompt_template_todo_state_description;
            case TODO_USAGE: return R.string.prompt_template_todo_usage_description;
            case AGENT_ROLE_EXPLORE_REMOTE: return R.string.prompt_template_agent_role_explore_remote_description;
            case AGENT_ROLE_CODING_REMOTE: return R.string.prompt_template_agent_role_coding_remote_description;
            case AGENT_ROLE_EXPLORE_LOCAL: return R.string.prompt_template_agent_role_explore_local_description;
            case AGENT_ROLE_CODING_LOCAL: return R.string.prompt_template_agent_role_coding_local_description;
            case AGENT_SYSTEM_PROMPT: return R.string.prompt_template_agent_system_prompt_description;
            case IMAGE_UNDERSTANDING_TOOL_SYSTEM: return R.string.prompt_template_image_understanding_tool_system_description;
            case CONTEXT_COMPACTION_SUMMARY_PREFIX: return R.string.prompt_template_context_compaction_summary_prefix_description;
            case CONTEXT_COMPACTION_RESPONSES_FALLBACK: return R.string.prompt_template_context_compaction_responses_fallback_description;
            default: throw new IllegalArgumentException("Unknown prompt: " + prompt.id);
        }
    }

    static String source(PromptId prompt, android.content.Context context) {
        switch (prompt) {
            case CHAT_MODE_CHAT: return context.getString(R.string.prompt_template_source_builtin_chat);
            case CHAT_MODE_PLAN: return context.getString(R.string.prompt_template_source_builtin_plan);
            case CHAT_MODE_AGENT: return context.getString(R.string.prompt_template_source_builtin_agent);
            case CHAT_MODE_CONTROL: return context.getString(R.string.prompt_template_source_builtin_control);
            default: return "prompts/" + prompt.file;
        }
    }
}
