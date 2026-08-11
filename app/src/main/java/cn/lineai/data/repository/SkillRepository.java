package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.R;
import cn.lineai.ai.SkillPromptProvider;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.service.GitHubSkillInstaller;
import cn.lineai.data.service.SkillFileManager;
import cn.lineai.data.service.SkillHubClient;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import cn.lineai.resource.ResourceProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Skill 仓库，负责 Skill 的 CRUD 与业务编排。
 * 文件系统操作委托给 {@link SkillFileManager}，提示词拼装委托给 {@link SkillPromptProvider}。
 */
public final class SkillRepository extends BaseRepository {

    private static final int MAX_SKILL_PROMPT_CHARS = 18000;

    private final ResourceProvider resourceProvider;
    private final SkillFileManager fileManager;
    private final SkillPromptProvider promptProvider;
    private final AgentExtensionRepository agentRepository;
    private final McpExtensionRepository mcpRepository;

    public SkillRepository(LineCodeDatabase database, ResourceProvider resourceProvider, SkillFileManager fileManager, AgentExtensionRepository agentRepository, McpExtensionRepository mcpRepository, SkillPromptProvider promptProvider) {
        super(database);
        this.resourceProvider = resourceProvider;
        this.fileManager = fileManager;
        this.promptProvider = promptProvider;
        this.agentRepository = agentRepository;
        this.mcpRepository = mcpRepository;
    }

    public synchronized List<SkillRecord> getSkills(String homePath) {
        fileManager.ensureSkillRoots(homePath);
        upsertDiscoveredSkills(fileManager.discoverSkills(homePath));
        return readSkills();
    }

    public synchronized SkillRecord createSkill(String homePath, String location, String name, String description, String content) {
        fileManager.ensureSkillRoots(homePath);
        String safeName = safe(name).trim();
        if (safeName.length() == 0) {
            safeName = "linecode-skill-" + System.currentTimeMillis();
        }
        String normalizedLocation = SkillRecord.normalizeLocation(location);
        File root = fileManager.localSkillRoot(homePath, normalizedLocation);
        File skillDir = fileManager.uniqueChild(root, fileManager.sanitizeFileName(safeName));
        if (!skillDir.exists()) {
            skillDir.mkdirs();
        }
        File skillFile = new File(skillDir, "SKILL.md");
        fileManager.writeUtf8(skillFile, fileManager.buildSkillMarkdown(safeName, description, content));
        SkillRecord record = fileManager.parseSkill(skillDir, skillFile, normalizedLocation);
        upsertDiscoveredSkills(Collections.singletonList(record));
        return record;
    }

    public synchronized SkillRecord installSkill(String homePath, String location, String sourcePath, String name) throws Exception {
        fileManager.ensureSkillRoots(homePath);
        File source = new File(safe(sourcePath).trim()).getCanonicalFile();
        if (!source.exists()) {
            throw new IllegalArgumentException(resourceProvider.getString(R.string.skill_source_not_found, sourcePath));
        }
        String normalizedLocation = SkillRecord.normalizeLocation(location);
        File root = fileManager.localSkillRoot(homePath, normalizedLocation);
        String baseName = fileManager.sanitizeFileName(safe(name).trim().length() == 0 ? fileManager.stripExtension(source.getName()) : name);
        File target = fileManager.uniqueChild(root, baseName);
        if (source.isDirectory()) {
            fileManager.copyDirectory(source, target);
        } else if (source.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileManager.unzip(source, target);
        } else if ("skill.md".equalsIgnoreCase(source.getName())) {
            target.mkdirs();
            fileManager.copyFile(source, new File(target, "SKILL.md"));
        } else {
            throw new IllegalArgumentException("仅支持目录、SKILL.md 或 .zip 技能包。");
        }
        File skillMd = fileManager.findSkillMd(target, 0);
        if (skillMd == null) {
            throw new IllegalArgumentException("安装完成，但没有找到 SKILL.md。");
        }
        SkillRecord record = fileManager.parseSkill(skillMd.getParentFile(), skillMd, normalizedLocation);
        upsertDiscoveredSkills(Collections.singletonList(record));
        return record;
    }

    public synchronized SkillRecord installSkillFromUri(String homePath, String location, String uri, String displayName) throws Exception {
        fileManager.ensureSkillRoots(homePath);
        String fileName = fileManager.skillImportFileName(displayName);
        File tempRoot = new File(fileManager.getWorkspacePaths().getLinecodeRoot(), "tmp/skills");
        File tempDir = fileManager.uniqueChild(tempRoot, fileManager.stripExtension(fileName));
        File tempFile = new File(tempDir, fileName.toLowerCase(Locale.ROOT).endsWith(".zip") ? fileName : "SKILL.md");
        fileManager.copyUriToFile(uri, tempFile);
        try {
            return installSkill(homePath, location, tempFile.getAbsolutePath(), fileManager.stripExtension(fileName));
        } finally {
            fileManager.deleteRecursive(tempDir);
        }
    }

    public synchronized SkillRecord installSkillFromGitHub(String homePath, String location, String githubUrl) throws Exception {
        fileManager.ensureSkillRoots(homePath);
        GitHubSkillInstaller installer = new GitHubSkillInstaller(
                resourceProvider,
                fileManager.getWorkspacePaths(),
                fileManager
        );
        File downloaded = installer.downloadToTemp(githubUrl);
        try {
            return installSkill(homePath, location, downloaded.getAbsolutePath(), downloaded.getName());
        } finally {
            fileManager.deleteRecursive(downloaded);
        }
    }

    public synchronized SkillRecord installSkillFromSkillHub(
            String homePath,
            String location,
            String slug,
            String version
    ) throws Exception {
        fileManager.ensureSkillRoots(homePath);
        File tempRoot = new File(fileManager.getWorkspacePaths().getLinecodeRoot(), "tmp/skills-skillhub");
        File tempDir = fileManager.uniqueChild(tempRoot, fileManager.sanitizeFileName(slug));
        File archive = new File(tempDir, "skill.zip");
        tempDir.mkdirs();
        try {
            byte[] bytes = new SkillHubClient().download(slug, version);
            FileOutputStream output = new FileOutputStream(archive, false);
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
            return installSkill(homePath, location, archive.getAbsolutePath(), slug);
        } finally {
            fileManager.deleteRecursive(tempDir);
        }
    }

    public synchronized void deleteSkills(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            deleteSkill(id);
        }
    }

    public synchronized void setSkillEnabled(String id, boolean enabled) {
        updateEnabled("skills", id, enabled);
    }

    public synchronized void deleteSkill(String id) {
        SkillRecord target = findSkill(id);
        database.getWritableDatabase().delete("skills", "id = ?", new String[] {safe(id)});
        if (target != null && !SkillRecord.LOCATION_SSH.equals(target.getLocation()) && target.getRootPath().length() > 0) {
            fileManager.deleteRecursive(new File(target.getRootPath()));
        }
    }

    public synchronized String buildExtensionPrompt(String homePath) {
        List<ExtensionAgentConfig> agents = agentRepository.getAgentExtensions();
        List<ExtensionMcpConfig> mcps = mcpRepository.getMcpExtensions();
        List<SkillRecord> skills = getSkills(homePath);
        StringBuilder builder = new StringBuilder();
        boolean hasContent = false;
        builder.append("## 扩展\n以下扩展来自设置里的\u300c扩展\u300d页面。自定义 Agent、HTTP MCP 和 Skills 都由 SQLite 配置动态注入。\n");

        ArrayList<ExtensionAgentConfig> enabledAgents = new ArrayList<>();
        for (ExtensionAgentConfig agent : agents) {
            if (agent.isEnabled()) {
                enabledAgents.add(agent);
            }
        }
        if (!enabledAgents.isEmpty()) {
            hasContent = true;
            builder.append("\n### 自定义 Agent\n");
            for (ExtensionAgentConfig agent : enabledAgents) {
                builder.append("- ").append(agent.getName()).append(" (").append(agent.getSlug()).append(")\n");
                if (agent.getTrigger().length() > 0) {
                    builder.append("  - 触发条件: ").append(agent.getTrigger()).append('\n');
                }
                if (agent.getPrompt().length() > 0) {
                    builder.append("  - Agent 提示词: ").append(limitInline(agent.getPrompt(), 1600)).append('\n');
                }
                builder.append("  - 工具: ").append(join(agent.getToolNames(), ", ", "无")).append('\n');
                builder.append("  - MCP: ").append(join(agent.getMcpIds(), ", ", "无")).append('\n');
            }
        }

        ArrayList<ExtensionMcpConfig> enabledMcps = new ArrayList<>();
        for (ExtensionMcpConfig mcp : mcps) {
            if (mcp.isEnabled()) {
                enabledMcps.add(mcp);
            }
        }
        if (!enabledMcps.isEmpty()) {
            hasContent = true;
            builder.append("\n### 自定义 HTTP MCP\n");
            for (ExtensionMcpConfig mcp : enabledMcps) {
                builder.append("- ").append(mcp.getName()).append(": ").append(mcp.getUrl())
                        .append(" (").append(enabledToolNames(mcp)).append(")\n");
            }
        }

        ArrayList<SkillRecord> enabledSkills = new ArrayList<>();
        for (SkillRecord skill : skills) {
            if (skill.isEnabled()) {
                enabledSkills.add(skill);
            }
        }
        if (!enabledSkills.isEmpty()) {
            hasContent = true;
            builder.append("\n### 已安装 Skills\n");
            int usedChars = 0;
            for (SkillRecord skill : enabledSkills) {
                String skillContent = fileManager.readSkillPrompt(skill);
                String block = promptProvider.buildExtensionPrompt(
                        skill.getName(), skillContent, skill.getRootPath());
                if (usedChars + block.length() > MAX_SKILL_PROMPT_CHARS) {
                    builder.append("#### Skills 提示词已截断\n已达到提示词长度上限，剩余 Skills 仅按路径和工具描述处理。\n");
                    break;
                }
                builder.append(block).append("\n\n");
                usedChars += block.length();
            }
        }

        return hasContent ? builder.toString().trim() : "";
    }

    private String enabledToolNames(ExtensionMcpConfig mcp) {
        ArrayList<String> names = new ArrayList<>();
        for (McpToolSummary tool : mcp.getTools()) {
            if (tool.isEnabled()) {
                names.add(tool.getName());
            }
        }
        return join(names, ", ", "未启用 tools");
    }

    private String join(List<String> values, String separator, String empty) {
        if (values == null || values.isEmpty()) {
            return empty;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value);
        }
        return builder.length() == 0 ? empty : builder.toString();
    }

    private String limitInline(String value, int maxChars) {
        String text = safe(value).replace('\r', '\n').replace("\n", "\\n").trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }

    public ArrayList<String> skillWriteRoots(String homePath) {
        fileManager.ensureSkillRoots(homePath);
        return fileManager.skillWriteRoots(homePath);
    }

    // ── 数据库操作 ──

    private void upsertDiscoveredSkills(List<SkillRecord> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return;
        }
        HashMap<String, Boolean> existingEnabled = existingSkillEnabled();
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            for (SkillRecord skill : discovered) {
                ContentValues values = skillValues(skill, existingEnabled.containsKey(skill.getId()) ? existingEnabled.get(skill.getId()) : skill.isEnabled());
                db.insertWithOnConflict("skills", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private List<SkillRecord> readSkills() {
        ArrayList<SkillRecord> skills = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, name, scope, path, description, enabled, updated_at, raw_json FROM skills ORDER BY updated_at DESC",
                new String[0]
        );
        try {
            while (cursor.moveToNext()) {
                String raw = value(cursor, "raw_json");
                JSONObject json = parseJson(raw);
                String rootPath = value(cursor, "path");
                String skillMdPath = json.optString("skillMdPath", rootPath.length() == 0 ? "" : new File(rootPath, "SKILL.md").getAbsolutePath());
                long discoveredAt = json.optLong("discoveredAt", longValue(cursor, "updated_at"));
                skills.add(new SkillRecord(
                        value(cursor, "id"),
                        value(cursor, "name"),
                        value(cursor, "description"),
                        rootPath,
                        skillMdPath,
                        value(cursor, "scope"),
                        intValue(cursor, "enabled") != 0,
                        discoveredAt,
                        longValue(cursor, "updated_at")
                ));
            }
        } finally {
            cursor.close();
        }
        Collections.sort(skills, new Comparator<SkillRecord>() {
            @Override
            public int compare(SkillRecord left, SkillRecord right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return skills;
    }

    private SkillRecord findSkill(String id) {
        for (SkillRecord skill : readSkills()) {
            if (skill.getId().equals(id)) {
                return skill;
            }
        }
        return null;
    }

    private ContentValues skillValues(SkillRecord skill, boolean enabled) {
        JSONObject raw = new JSONObject();
        try {
            raw.put("skillMdPath", skill.getSkillMdPath());
            raw.put("rootPath", skill.getRootPath());
            raw.put("location", skill.getLocation());
            raw.put("discoveredAt", skill.getDiscoveredAt());
        } catch (Exception ignored) {
        }
        ContentValues values = new ContentValues();
        values.put("id", skill.getId());
        values.put("name", skill.getName());
        values.put("scope", skill.getLocation());
        values.put("path", skill.getRootPath());
        values.put("description", skill.getDescription());
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", skill.getUpdatedAt());
        values.put("raw_json", raw.toString());
        return values;
    }

    private HashMap<String, Boolean> existingSkillEnabled() {
        HashMap<String, Boolean> values = new HashMap<>();
        Cursor cursor = database.getReadableDatabase().rawQuery("SELECT id, enabled FROM skills", new String[0]);
        try {
            while (cursor.moveToNext()) {
                values.put(value(cursor, "id"), intValue(cursor, "enabled") != 0);
            }
        } finally {
            cursor.close();
        }
        return values;
    }

    private JSONObject parseJson(String raw) {
        try {
            return new JSONObject(safe(raw).length() == 0 ? "{}" : raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
