package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.ChatMode;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Skill;
import cn.lineai.core.model.TodoItem;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.engine.protocol.ReasoningSpec;
import cn.lineai.engine.prompt.SystemPromptAssembler;

/** Reads per-turn prompt and reasoning settings. */
final class ChatTurnConfig {

    private ChatTurnConfig() {
    }

    static String systemPrompt(SystemPromptAssembler prompts, SettingsStore settings,
                               ModelConfig model, String extensionSection,
                               String summarySection, String learningSection) {
        String extensions = extensionSection == null ? "" : extensionSection;
        String summary = summarySection == null ? "" : summarySection;
        if (prompts == null) return extensions + summary;
        String tone = settings.get(UiPreferences.Field.TONE.key, "coding");
        String mode = ChatMode.normalize(settings.get(ToolModes.CHAT_MODE_KEY, ChatMode.AGENT));
        boolean minimal = Boolean.parseBoolean(
                settings.get(UiPreferences.Field.MINIMAL_MODE.key, "false"));
        String base = minimal
                ? prompts.minimal(mode)
                : prompts.assemble(model.modelId, model.displayName(), model.providerId,
                        model.protocol.name(), tone, mode,
                        learningSection == null ? "" : learningSection);
        String extra = minimal ? summary : extensions + summary;
        return extra.isEmpty() ? base : base + extra;
    }

    static List<ContentBlock> blocks(CharSequence text, CharSequence reasoning) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (reasoning != null && reasoning.length() > 0) {
            blocks.add(ContentBlock.reasoning(reasoning.toString()));
        }
        if (text != null && text.length() > 0) {
            blocks.add(ContentBlock.text(text.toString()));
        }
        return blocks;
    }

    static int percent(List<Message> messages, int contextSize) {
        long chars = 0;
        for (Message message : messages) {
            chars += message.plainText().length() + message.reasoningText().length();
        }
        long tokens = chars / 4;
        return (int) Math.min(100, tokens * 100 / Math.max(1, contextSize));
    }

    static String titleFor(String prompt) {
        String single = prompt.replace('\n', ' ').trim();
        return single.length() <= 30 ? single : single.substring(0, 30);
    }

    static String agentSection(List<AgentExtension> agents) {
        if (agents == null) return "";
        StringBuilder out = new StringBuilder();
        for (AgentExtension agent : agents) {
            if (!agent.enabled || agent.prompt.isEmpty()) continue;
            if (out.length() > 4000) break;
            out.append("\n\n### ").append(agent.name.isEmpty() ? agent.slug : agent.name)
                    .append(" (").append(agent.slug).append(')');
            if (!agent.trigger.isEmpty()) out.append("\nTrigger: ").append(agent.trigger);
            if (!agent.toolNames.isEmpty()) out.append("\nTools: ").append(agent.toolNames);
            out.append("\n").append(agent.prompt);
        }
        if (out.length() == 0) return "";
        return "\n\n## Custom Agent extensions" + out;
    }

    static String skillSection(List<Skill> skills) {
        if (skills == null) return "";
        StringBuilder out = new StringBuilder();
        for (Skill skill : skills) {
            if (!skill.enabled || skill.content.trim().isEmpty()) continue;
            if (out.length() > 18000) break;
            out.append("\n\n### Skill: ").append(skill.name);
            if (!skill.description.isEmpty()) out.append("\n").append(skill.description);
            out.append("\n\n").append(skill.content.trim());
        }
        if (out.length() == 0) return "";
        return "\n\n## Skills" + out;
    }

    static void addAttachments(Message.Builder builder, List<String> images, List<String> files) {
        for (String image : images) builder.addBlock(ContentBlock.image(image));
        for (String file : files) {
            builder.addBlock(ContentBlock.file(file, readAttachment(file)));
        }
    }

    static String readAttachment(String path) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(new java.io.File(path).toPath());
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return text.length() > 20000 ? text.substring(0, 20000) + "\n… truncated" : text;
        } catch (Exception error) {
            return "(binary or unreadable file)";
        }
    }

    static String attachmentTitle(String prompt, List<String> images, List<String> files) {
        if (!prompt.isEmpty()) return titleFor(prompt);
        if (!images.isEmpty()) return new java.io.File(images.get(0)).getName();
        if (!files.isEmpty()) return new java.io.File(files.get(0)).getName();
        return "New chat";
    }

    static String todoSection(List<TodoItem> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\n\n## TODO");
        for (TodoItem item : items) {
            String marker = item.status == TodoItem.Status.COMPLETED ? "[x]"
                    : item.status == TodoItem.Status.IN_PROGRESS ? "[~]" : "[ ]";
            out.append("\n- ").append(marker).append(' ').append(item.content);
        }
        return out.toString();
    }

    static ReasoningSpec reasoningSpec(SettingsStore settings, ModelConfig model) {
        String effort = settings.get(UiPreferences.Field.REASONING.key, "medium");
        boolean preserve = Boolean.parseBoolean(
                settings.get(UiPreferences.Field.PRESERVE_REASONING.key, "false"));
        String provider = model.providerLabel.isEmpty() ? model.providerId : model.providerLabel;
        return new ReasoningSpec(model.baseUrl, model.modelId, provider, effort, preserve);
    }
}
