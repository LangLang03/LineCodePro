package cn.lineai.feature.chat;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.Conversation;
import cn.lineai.core.model.MemoryItem;
import cn.lineai.core.model.Skill;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.ConversationStore;
import cn.lineai.data.api.MemoryStore;
import cn.lineai.data.api.ProjectStore;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.data.api.SkillStore;
import cn.lineai.engine.prompt.PromptId;
import cn.lineai.engine.prompt.PromptRenderer;
import cn.lineai.engine.prompt.PromptStore;

/** Builds the learning-mode context from memories, chat titles, skills and skill paths. */
public final class LearningContextProvider {

    /** Cap on injected memory items. */
    private static final int MAX_MEMORIES = 20;
    /** Cap on injected conversation titles. */
    private static final int MAX_HISTORY = 10;
    /** Cap on the length of a single injected memory. */
    private static final int MAX_MEMORY_CHARS = 600;

    private final SettingsStore settings;
    private final MemoryStore memories;
    private final ConversationStore conversations;
    private final SkillStore skills;
    private final ProjectStore projects;
    private final PromptStore prompts;
    private final File skillsRoot;

    /**
     * @param settings  preference store; learning mode must be enabled
     * @param memories  long-term memory items
     * @param conversations recent conversation titles
     * @param skills    enabled skills
     * @param projects  selected project for scope and skill paths
     * @param prompts   store holding the learning-context prompt template
     * @param skillsRoot app-private skills directory injected as the readable boundary
     */
    public LearningContextProvider(SettingsStore settings, MemoryStore memories,
                            ConversationStore conversations, SkillStore skills,
                            ProjectStore projects, PromptStore prompts, File skillsRoot) {
        this.settings = settings;
        this.memories = memories;
        this.conversations = conversations;
        this.skills = skills;
        this.projects = projects;
        this.prompts = prompts;
        this.skillsRoot = skillsRoot;
    }

    /**
     * Renders the learning-context prompt when learning mode is enabled.
     *
     * @return rendered prompt text; empty when learning mode is off
     */
    public String build() {
        if (!Boolean.parseBoolean(
                settings.get(UiPreferences.Field.LEARNING_MODE.key, "false"))) {
            return "";
        }
        String projectId = projects.selected() == null ? "" : projects.selected().path;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("WORKING_MEMORY_SECTION", "");
        values.put("MEMORY_SECTION", memorySection(projectId));
        values.put("HISTORY_SECTION", historySection());
        values.put("SKILL_PATHS_SECTION", skillPaths(projectId));
        values.put("SKILLS_SECTION", skillSection());
        values.put("PRIVATE_BOUNDARY_SECTION", boundary());
        return PromptRenderer.render(PromptId.LEARNING_CONTEXT,
                prompts.current(PromptId.LEARNING_CONTEXT), values);
    }

    /** Long-term memory list scoped to the project, capped and truncated per item. */
    private String memorySection(String projectId) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (MemoryItem item : memories.list(projectId)) {
            if (++count > MAX_MEMORIES) break;
            String text = item.content.trim().replace('\n', ' ');
            if (text.isEmpty()) continue;
            if (text.length() > MAX_MEMORY_CHARS) {
                text = text.substring(0, MAX_MEMORY_CHARS) + "…";
            }
            out.append("\n- [").append(item.scope);
            if (!item.projectId.isEmpty()) out.append(':').append(item.projectId);
            out.append("] ");
            if (!item.title.isEmpty()) out.append(item.title).append(": ");
            out.append(text);
        }
        return out.length() == 0 ? "" : "### Long-term memory" + out;
    }

    /** Recent conversation titles, capped at {@link #MAX_HISTORY}. */
    private String historySection() {
        StringBuilder out = new StringBuilder();
        List<Conversation> items = conversations.list();
        for (int i = 0; i < items.size() && i < MAX_HISTORY; i++) {
            String title = items.get(i).title.trim();
            if (title.isEmpty()) continue;
            out.append("\n- ").append(title);
        }
        return out.length() == 0 ? "" : "### Recent conversations" + out;
    }

    /** Readable skill directories (app, project, ssh) injected as the file-tool boundary. */
    private String skillPaths(String projectId) {
        StringBuilder out = new StringBuilder("### Skills paths");
        out.append("\n- app: ").append(skillsRoot.getAbsolutePath());
        if (!projectId.isEmpty()) {
            out.append("\n- project: ")
                    .append(new File(projectId, ".linecode/skills").getAbsolutePath());
        }
        out.append("\n- ssh: ~/.linecode/skills");
        return out.toString();
    }

    /** Names and descriptions of enabled skills, capped in length. */
    private String skillSection() {
        StringBuilder out = new StringBuilder();
        for (Skill skill : skills.enabled()) {
            if (out.length() > 2000) break;
            out.append("\n- ").append(skill.name);
            if (!skill.description.isEmpty()) out.append(": ").append(skill.description);
        }
        return out.length() == 0 ? "" : "### Enabled skills" + out;
    }

    /** Hard privacy rule confining file tools to the injected skills directories. */
    private String boundary() {
        return "### Private directory boundary\nFile tools may use the injected Skills paths ("
                + skillsRoot.getAbsolutePath()
                + " and the selected project's .linecode/skills); do not probe other "
                + "app-private directories.";
    }
}
