package cn.lineai.ai.prompt;

import android.content.Context;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.workspace.WorkspacePaths;
import java.util.HashMap;

public final class SystemPromptProvider {
    private final WorkspacePaths workspacePaths;
    private final PromptTemplateRepository promptTemplateRepository;

    public SystemPromptProvider(Context context) {
        this(context, new PromptTemplateRepository(context));
    }

    public SystemPromptProvider(Context context, PromptTemplateRepository promptTemplateRepository) {
        Context appContext = context.getApplicationContext();
        this.workspacePaths = new WorkspacePaths(appContext);
        this.promptTemplateRepository = promptTemplateRepository == null
                ? new PromptTemplateRepository(appContext)
                : promptTemplateRepository;
    }

    public String build(String homePath) {
        return build(homePath, AiBehaviorSettings.TONE_CODING);
    }

    public String build(String homePath, String toneMode) {
        return build(homePath, toneMode, "");
    }

    public String build(String homePath, String toneMode, String learningContext) {
        return build(homePath, toneMode, learningContext, "");
    }

    public String build(String homePath, String toneMode, String learningContext, String toolsContext) {
        return build(homePath, toneMode, "", learningContext, toolsContext);
    }

    public String build(
            String homePath,
            String toneMode,
            String chatModeContext,
            String learningContext,
            String toolsContext
    ) {
        HashMap<String, String> values = new HashMap<>();
        values.put("TONE_CONTEXT", toneContext(toneMode));
        values.put("CHAT_MODE_CONTEXT", chatModeContext == null ? "" : chatModeContext.trim());
        values.put("WORK_DIRECTORY_CONTEXT", workDirectoryContext(homePath));
        values.put("LEARNING_CONTEXT", learningContext == null ? "" : learningContext.trim());
        values.put("TOOLS_CONTEXT", toolsContext == null ? "" : toolsContext.trim());
        return template().render(values);
    }

    private StringTemplate template() {
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_SYSTEM_PROMPT));
    }

    private String workDirectoryContext(String homePath) {
        if (homePath == null || homePath.trim().length() == 0) {
            return "";
        }
        HashMap<String, String> values = new HashMap<>();
        values.put("HOME_PATH", homePath.trim());
        values.put("LINECODE_ROOT", workspacePaths.getLinecodeRoot().getAbsolutePath());
        values.put("GLOBAL_SKILLS_ROOT", workspacePaths.getSkillsRoot().getAbsolutePath());
        values.put("WORKSPACE_PRIVATE_ROOT", WorkspacePaths.join(homePath.trim(), ".linecode"));
        values.put("WORKSPACE_SKILLS_ROOT", WorkspacePaths.join(homePath.trim(), ".linecode/skills"));
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_WORK_DIRECTORY)).render(values);
    }

    private String toneContext(String toneMode) {
        if (AiBehaviorSettings.TONE_CHAT.equals(toneMode)) {
            return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_TONE_CHAT)).render(new HashMap<>());
        }
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_TONE_CODING)).render(new HashMap<>());
    }
}
