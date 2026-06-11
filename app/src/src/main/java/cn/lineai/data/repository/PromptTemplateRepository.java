package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.ChatMode;
import cn.lineai.model.PromptTemplateItem;
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
    public static final String ID_MEMORY_EXTRACTION = "memoryExtraction";
    public static final String ID_SKILL_EXTRACTION = "skillExtraction";
    public static final String ID_CONTEXT_COMPACTION = "contextCompaction";
    public static final String ID_CHAT_MODE_CHAT = "chatModeChat";
    public static final String ID_CHAT_MODE_PLAN = "chatModePlan";
    public static final String ID_CHAT_MODE_AGENT = "chatModeAgent";

    private static final String KEY_PREFIX = "@linecode_prompt_template_";
    private static final List<Definition> DEFINITIONS = buildDefinitions();

    private final Context context;
    private final SettingsRepository settingsRepository;

    public PromptTemplateRepository(Context context) {
        this.context = context.getApplicationContext();
        this.settingsRepository = new SettingsRepository(this.context);
    }

    public synchronized List<PromptTemplateItem> getTemplates() {
        ArrayList<PromptTemplateItem> items = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            String defaultText = defaultText(definition);
            String currentText = settingsRepository.getString(key(definition.id), defaultText);
            items.add(new PromptTemplateItem(
                    definition.id,
                    definition.title,
                    definition.description,
                    definition.sourceLabel,
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

    private Definition definitionFor(String id) {
        String safeId = id == null ? "" : id;
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(safeId)) {
                return definition;
            }
        }
        throw new IllegalArgumentException("未知提示词模板: " + safeId);
    }

    private String key(String id) {
        return KEY_PREFIX + id;
    }

    private String readAsset(String path) {
        try {
            InputStream input = context.getAssets().open(path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalStateException("无法读取提示词模板: " + path, e);
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
                "System 提示词",
                "主系统提示词，定义 LineCode 的身份、工作方式、工具循环、回答规范，并组合其它上下文模板。",
                "prompts/system-prompt-template.txt",
                "TOOLS_CONTEXT", "TONE_CONTEXT", "CHAT_MODE_CONTEXT", "WORK_DIRECTORY_CONTEXT", "LEARNING_CONTEXT"
        ));
        definitions.add(new Definition(
                ID_WORK_DIRECTORY,
                "工作目录模板",
                "注入当前工作区、应用私有目录、Skills 目录和 SSH Skills 目录，约束文件操作默认路径。",
                "prompts/work-directory-template.txt",
                "HOME_PATH", "LINECODE_ROOT", "GLOBAL_SKILLS_ROOT", "WORKSPACE_PRIVATE_ROOT", "WORKSPACE_SKILLS_ROOT"
        ));
        definitions.add(new Definition(
                ID_TONE_CODING,
                "编程模式提示词",
                "AI 行为选择“编程模式”时追加的语气和输出风格约束。",
                "prompts/tone-coding-template.txt"
        ));
        definitions.add(new Definition(
                ID_TONE_CHAT,
                "聊天模式提示词",
                "AI 行为选择“聊天模式”时追加的语气和输出风格约束。",
                "prompts/tone-chat-template.txt"
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_CHAT,
                "会话 Chat 模式提示词",
                "顶部会话模式选择 Chat 时注入，只读交流、解释和搜索，不允许修改文件或执行 Shell。",
                "内置模板：ChatMode.CHAT",
                ChatMode.promptContext(ChatMode.CHAT)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_PLAN,
                "会话 Plan 模式提示词",
                "顶部会话模式选择 Plan 时注入，只读规划，允许收集上下文但禁止执行计划。",
                "内置模板：ChatMode.PLAN",
                ChatMode.promptContext(ChatMode.PLAN)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_AGENT,
                "会话 Agent 模式提示词",
                "顶部会话模式选择 Agent 时注入，允许按权限读取、修改、执行和验证任务。",
                "内置模板：ChatMode.AGENT",
                ChatMode.promptContext(ChatMode.AGENT)
        ));
        definitions.add(new Definition(
                ID_LEARNING_CONTEXT,
                "学习模式上下文模板",
                "学习模式开启时，把短期记忆、长期记忆、聊天检索和 Skills 检索结果渲染进 system prompt。",
                "prompts/learning-context-template.txt",
                "WORKING_MEMORY_SECTION", "MEMORY_SECTION", "HISTORY_SECTION", "SKILL_PATHS_SECTION",
                "SKILLS_SECTION", "PRIVATE_BOUNDARY_SECTION"
        ));
        definitions.add(new Definition(
                ID_MEMORY_EXTRACTION,
                "长期记忆提取模板",
                "学习模式保存记忆时使用，指导模型从本轮对话中提取 user/project/environment 记忆。",
                "prompts/memory-extraction-template.txt",
                "PROJECT_ID", "USER_INPUT", "TRANSCRIPT"
        ));
        definitions.add(new Definition(
                ID_SKILL_EXTRACTION,
                "Skills 沉淀模板",
                "学习模式判断是否生成可复用 Skill 时使用，约束返回 JSON 和 Skill 内容格式。",
                "prompts/skill-extraction-template.txt",
                "PROJECT_ID", "USER_INPUT", "TRANSCRIPT"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION,
                "上下文压缩模板",
                "上下文过长时用于总结旧对话，要求模型输出可恢复任务状态的压缩摘要。",
                "prompts/context-compaction-template.txt"
        ));
        return Collections.unmodifiableList(definitions);
    }

    private static final class Definition {
        final String id;
        final String title;
        final String description;
        final String assetPath;
        final String sourceLabel;
        final String defaultText;
        final String[] variables;

        Definition(String id, String title, String description, String assetPath, String... variables) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.assetPath = assetPath;
            this.sourceLabel = assetPath;
            this.defaultText = "";
            this.variables = variables == null ? new String[0] : variables;
        }

        Definition(String id, String title, String description, String source, String defaultText) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.assetPath = "";
            this.sourceLabel = source;
            this.defaultText = defaultText == null ? "" : defaultText;
            this.variables = new String[0];
        }
    }
}
