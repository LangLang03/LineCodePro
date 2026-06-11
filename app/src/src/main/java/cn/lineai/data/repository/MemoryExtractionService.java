package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.StringTemplate;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.ModelConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MemoryExtractionService {
    private static final int MAX_TRANSCRIPT_CHARS = 6000;
    private static final int MAX_MEMORY_CHARS = 320;
    private static final int MAX_MEMORIES = 5;
    private static final int MAX_SKILLS = 2;
    private static final int MAX_SKILL_CONTENT_CHARS = 8000;

    private final Context context;
    private final LearningContextRepository repository;
    private final ExtensionRepository extensionRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ModelClient modelClient = new ModelClient();

    public MemoryExtractionService(Context context, LearningContextRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.extensionRepository = new ExtensionRepository(this.context);
        this.promptTemplateRepository = new PromptTemplateRepository(this.context);
    }

    public void extractAndStore(ModelConfig selectedModel, String projectId, String userInput, String transcript) {
        ArrayList<ExtractedMemory> candidates = new ArrayList<>();
        if (selectedModel != null) {
            try {
                candidates.addAll(extractWithModel(selectedModel, projectId, userInput, transcript));
            } catch (Exception ignored) {
            }
        }
        candidates.addAll(ruleBasedCandidates(userInput, transcript));
        for (ExtractedMemory memory : dedupe(candidates)) {
            repository.saveExtractedMemory(memory.scope, projectId, memory.content, memory.confidence);
        }
        extractAndStoreSkills(selectedModel, projectId, userInput, transcript);
    }

    private List<ExtractedMemory> extractWithModel(
            ModelConfig selectedModel,
            String projectId,
            String userInput,
            String transcript
    ) throws Exception {
        HashMap<String, String> values = new HashMap<>();
        values.put("PROJECT_ID", safe(projectId));
        values.put("USER_INPUT", trimForPrompt(userInput, 1200));
        values.put("TRANSCRIPT", trimForPrompt(transcript, MAX_TRANSCRIPT_CHARS));
        String prompt = template().render(values);
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new SystemModelMessage(prompt));
        messages.add(new UserModelMessage("请仅返回 JSON。"));
        ModelCompletionResponse response = modelClient.complete(selectedModel, messages);
        return parseCandidates(response.getText(), userInput);
    }

    private void extractAndStoreSkills(ModelConfig selectedModel, String projectId, String userInput, String transcript) {
        if (selectedModel == null || !hasSkillSignal(userInput, transcript)) {
            return;
        }
        try {
            HashMap<String, String> values = new HashMap<>();
            values.put("PROJECT_ID", safe(projectId));
            values.put("USER_INPUT", trimForPrompt(userInput, 1200));
            values.put("TRANSCRIPT", trimForPrompt(transcript, MAX_TRANSCRIPT_CHARS));
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new SystemModelMessage(skillTemplate().render(values)));
            messages.add(new UserModelMessage("请仅返回 JSON。"));
            ModelCompletionResponse response = modelClient.complete(selectedModel, messages);
            for (ExtractedSkill skill : parseSkills(response.getText())) {
                if (skillExists(projectId, skill.name)) {
                    continue;
                }
                extensionRepository.createSkill(projectId, skill.location, skill.name, skill.description, skill.content);
            }
        } catch (Exception ignored) {
        }
    }

    private List<ExtractedSkill> parseSkills(String rawText) {
        ArrayList<ExtractedSkill> skills = new ArrayList<>();
        String json = extractJson(rawText);
        if (json.length() == 0) {
            return skills;
        }
        try {
            JSONObject object = new JSONObject(json);
            JSONArray array = object.optJSONArray("skills");
            if (array == null && json.startsWith("[")) {
                array = new JSONArray(json);
            }
            if (array == null) {
                return skills;
            }
            for (int i = 0; i < array.length() && skills.size() < MAX_SKILLS; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = sanitizeSkillName(item.optString("name"));
                String description = normalizeContent(item.optString("description"));
                String content = item.optString("content").trim();
                String location = "app".equals(item.optString("location")) ? "app" : "project";
                if (name.length() == 0 || content.length() < 80 || content.length() > MAX_SKILL_CONTENT_CHARS) {
                    continue;
                }
                if (shouldKeep(content, 0.80)) {
                    skills.add(new ExtractedSkill(name, description, location, content));
                }
            }
        } catch (Exception ignored) {
        }
        return skills;
    }

    private boolean skillExists(String projectId, String name) {
        String target = normalizedKey(name);
        if (target.length() == 0) {
            return true;
        }
        for (cn.lineai.model.SkillRecord skill : extensionRepository.getSkills(projectId)) {
            if (normalizedKey(skill.getName()).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSkillSignal(String userInput, String transcript) {
        String text = (safe(userInput) + "\n" + safe(transcript)).toLowerCase(Locale.ROOT);
        return containsAny(text, "skill", "skills", "沉淀", "复用", "长期", "流程", "规范", "以后", "下次", "自动创建");
    }

    private List<ExtractedMemory> parseCandidates(String rawText, String userInput) {
        ArrayList<ExtractedMemory> candidates = new ArrayList<>();
        String json = extractJson(rawText);
        if (json.length() == 0) {
            return candidates;
        }
        try {
            JSONArray array;
            if (json.startsWith("[")) {
                array = new JSONArray(json);
            } else {
                JSONObject object = new JSONObject(json);
                array = object.optJSONArray("memories");
                if (array == null) {
                    array = new JSONArray();
                }
            }
            for (int i = 0; i < array.length() && candidates.size() < MAX_MEMORIES; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String content = normalizeContent(item.optString("content"));
                String scope = resolveScope(item.optString("scope"), content, userInput);
                double confidence = item.optDouble("confidence", 0.7);
                addIfValid(candidates, scope, content, confidence);
            }
        } catch (Exception ignored) {
        }
        return candidates;
    }

    private String extractJson(String rawText) {
        String value = safe(rawText).trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("(?is)^```(?:json)?\\s*", "");
            value = value.replaceFirst("(?is)\\s*```$", "").trim();
        }
        int objectStart = value.indexOf('{');
        int objectEnd = value.lastIndexOf('}');
        int arrayStart = value.indexOf('[');
        int arrayEnd = value.lastIndexOf(']');
        if (objectStart >= 0 && objectEnd > objectStart && (arrayStart < 0 || objectStart < arrayStart)) {
            return value.substring(objectStart, objectEnd + 1);
        }
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return value.substring(arrayStart, arrayEnd + 1);
        }
        return "";
    }

    static List<ExtractedMemory> ruleBasedCandidates(String userInput, String transcript) {
        ArrayList<ExtractedMemory> candidates = new ArrayList<>();
        String source = (safeStatic(userInput) + "\n" + safeStatic(transcript)).trim();
        String normalized = compactSpaces(source);
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (containsAny(lower, "androidx", "android x")
                && containsAny(normalized, "不用", "不要用", "不能用", "不能使用", "禁止使用", "不能依赖")
                && hasProjectCue(normalized)) {
            addIfValid(candidates, MemoryOverviewState.Memory.SCOPE_PROJECT, "当前项目不能使用 AndroidX。", 0.95);
        }

        for (String sentence : splitSentences(normalized)) {
            if (candidates.size() >= MAX_MEMORIES) {
                break;
            }
            String scope = inferScope(sentence);
            if (scope.length() == 0 || !hasStableCue(sentence)) {
                continue;
            }
            String content = normalizeContent(sentence);
            if (content.length() == 0 || content.contains("比如")) {
                continue;
            }
            addIfValid(candidates, scope, content, 0.65);
        }
        return candidates;
    }

    private static String inferScope(String sentence) {
        if (hasProjectCue(sentence)) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        if (hasEnvironmentCue(sentence)) {
            return MemoryOverviewState.Memory.SCOPE_ENVIRONMENT;
        }
        if (hasUserCue(sentence)) {
            return MemoryOverviewState.Memory.SCOPE_USER;
        }
        return "";
    }

    private static boolean hasProjectCue(String text) {
        return containsAny(text, "这个项目", "当前项目", "本项目", "该项目", "这个 app", "当前 app", "工作区", "代码库", "仓库", "工程", "LineCode", "LineAI");
    }

    private static boolean hasStableCue(String text) {
        return containsAny(text, "不能", "不要", "禁止", "必须", "只能", "采用", "使用", "依赖", "对齐", "保持", "架构", "默认", "偏好", "喜欢", "习惯");
    }

    private static boolean hasEnvironmentCue(String text) {
        return containsAny(text, "我的手机", "当前设备", "设备", "Android", "Termux", "Gradle", "JDK", "路径", "权限", "镜像源", "环境", "adb");
    }

    private static boolean hasUserCue(String text) {
        return containsAny(text, "我喜欢", "我偏好", "我的习惯", "以后都", "默认", "所有项目", "全局", "不要问我", "用中文");
    }

    private static List<String> splitSentences(String text) {
        ArrayList<String> sentences = new ArrayList<>();
        String[] parts = safeStatic(text).split("[。！？!?\\n]+");
        for (String part : parts) {
            String value = compactSpaces(part);
            if (value.length() >= 8) {
                sentences.add(value);
            }
        }
        return sentences;
    }

    private static void addIfValid(List<ExtractedMemory> candidates, String scope, String content, double confidence) {
        String normalizedScope = normalizeScope(scope);
        String normalizedContent = normalizeContent(content);
        if (!shouldKeep(normalizedContent, confidence)) {
            return;
        }
        candidates.add(new ExtractedMemory(normalizedScope, normalizedContent, confidence));
    }

    private List<ExtractedMemory> dedupe(List<ExtractedMemory> candidates) {
        LinkedHashMap<String, ExtractedMemory> unique = new LinkedHashMap<>();
        for (ExtractedMemory candidate : candidates) {
            if (candidate == null || !shouldKeep(candidate.content, candidate.confidence)) {
                continue;
            }
            String key = candidate.scope + ":" + normalizedKey(candidate.content);
            if (!unique.containsKey(key)) {
                unique.put(key, candidate);
            }
            if (unique.size() >= MAX_MEMORIES) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean shouldKeep(String content, double confidence) {
        String value = safeStatic(content).trim();
        if (confidence < 0.55 || value.length() < 8 || value.length() > MAX_MEMORY_CHARS) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "api key", "apikey", "token", "password", "passwd", "secret", "cookie", "私钥", "密码", "密钥", "sk-")) {
            return false;
        }
        return !containsAny(value, "本轮任务", "这次任务", "刚才的输出", "报错日志", "安装到手机", "继续写", "下一步干嘛");
    }

    private static String resolveScope(String requestedScope, String content, String userInput) {
        String safeContent = safeStatic(content);
        if (hasProjectCue(safeContent)) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        if (hasEnvironmentCue(safeContent)) {
            return MemoryOverviewState.Memory.SCOPE_ENVIRONMENT;
        }
        if (hasUserCue(safeContent)) {
            return MemoryOverviewState.Memory.SCOPE_USER;
        }
        String normalizedRequest = normalizeScope(requestedScope);
        if (MemoryOverviewState.Memory.SCOPE_PROJECT.equals(normalizedRequest)) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        String lowerContent = safeContent.toLowerCase(Locale.ROOT);
        if (hasProjectCue(userInput) && containsAny(lowerContent, "androidx", "android x")) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        return normalizedRequest;
    }

    private static String normalizeScope(String scope) {
        String value = safeStatic(scope).trim().toLowerCase(Locale.ROOT);
        if (MemoryOverviewState.Memory.SCOPE_PROJECT.equals(value)) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        if (MemoryOverviewState.Memory.SCOPE_ENVIRONMENT.equals(value)) {
            return MemoryOverviewState.Memory.SCOPE_ENVIRONMENT;
        }
        return MemoryOverviewState.Memory.SCOPE_USER;
    }

    private static String normalizeContent(String content) {
        String value = compactSpaces(content);
        while (value.startsWith("-") || value.startsWith("，") || value.startsWith(",") || value.startsWith(":") || value.startsWith("：")) {
            value = value.substring(1).trim();
        }
        if (value.length() > MAX_MEMORY_CHARS) {
            value = value.substring(0, MAX_MEMORY_CHARS - 1).trim() + "。";
        }
        return value;
    }

    private static String compactSpaces(String text) {
        String value = safeStatic(text).replace('\r', ' ').replace('\n', ' ').trim();
        while (value.contains("  ")) {
            value = value.replace("  ", " ");
        }
        return value;
    }

    private static boolean containsAny(String text, String... needles) {
        String value = safeStatic(text);
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedKey(String content) {
        String value = safeStatic(content).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || (ch >= '\u4e00' && ch <= '\u9fff')) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String trimForPrompt(String value, int maxChars) {
        String text = safe(value);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 3) + "...";
    }

    private StringTemplate template() {
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_MEMORY_EXTRACTION));
    }

    private StringTemplate skillTemplate() {
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_SKILL_EXTRACTION));
    }

    private String sanitizeSkillName(String name) {
        String value = safe(name).trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length() && builder.length() < 64; i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                builder.append(ch);
            } else if (Character.isWhitespace(ch)) {
                builder.append('-');
            }
        }
        String clean = builder.toString();
        while (clean.contains("--")) {
            clean = clean.replace("--", "-");
        }
        while (clean.startsWith("-")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("-")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeStatic(String value) {
        return value == null ? "" : value;
    }

    static final class ExtractedMemory {
        final String scope;
        final String content;
        final double confidence;

        ExtractedMemory(String scope, String content, double confidence) {
            this.scope = normalizeScope(scope);
            this.content = normalizeContent(content);
            this.confidence = confidence;
        }
    }

    private static final class ExtractedSkill {
        final String name;
        final String description;
        final String location;
        final String content;

        ExtractedSkill(String name, String description, String location, String content) {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
            this.location = "app".equals(location) ? "app" : "project";
            this.content = content == null ? "" : content;
        }
    }
}
