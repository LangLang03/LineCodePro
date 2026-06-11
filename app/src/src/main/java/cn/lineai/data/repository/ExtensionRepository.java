package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import cn.lineai.security.UrlPolicy;
import cn.lineai.workspace.WorkspacePaths;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ExtensionRepository {
    private static final int MAX_SCAN_DEPTH = 4;
    private static final int MAX_SKILL_PROMPT_CHARS = 18000;

    private final Context context;
    private final LineCodeDatabase database;
    private final WorkspacePaths workspacePaths;

    public ExtensionRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = LineCodeDatabase.getInstance(this.context);
        this.workspacePaths = new WorkspacePaths(this.context);
    }

    public synchronized ExtensionOverviewState getOverview(String homePath) {
        List<SkillRecord> skills = getSkills(homePath);
        return new ExtensionOverviewState(getAgentExtensions(), getMcpExtensions(), skills);
    }

    public synchronized List<ExtensionAgentConfig> getAgentExtensions() {
        ArrayList<ExtensionAgentConfig> agents = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, enabled, name, slug, prompt, trigger, tool_names_json, mcp_ids_json, created_at, updated_at "
                        + "FROM extension_agents ORDER BY updated_at DESC",
                new String[0]
        );
        try {
            while (cursor.moveToNext()) {
                agents.add(new ExtensionAgentConfig(
                        value(cursor, "id"),
                        intValue(cursor, "enabled") != 0,
                        value(cursor, "name"),
                        value(cursor, "slug"),
                        value(cursor, "prompt"),
                        value(cursor, "trigger"),
                        jsonStringList(value(cursor, "tool_names_json")),
                        jsonStringList(value(cursor, "mcp_ids_json")),
                        longValue(cursor, "created_at"),
                        longValue(cursor, "updated_at")
                ));
            }
        } finally {
            cursor.close();
        }
        return agents;
    }

    public synchronized ExtensionAgentConfig saveAgentExtension(ExtensionAgentConfig input) {
        long now = System.currentTimeMillis();
        String id = input == null || input.getId().length() == 0 ? nextId("agent") : input.getId();
        long createdAt = input == null || input.getCreatedAt() <= 0 ? now : input.getCreatedAt();
        ExtensionAgentConfig saved = new ExtensionAgentConfig(
                id,
                input == null || input.isEnabled(),
                input == null ? "" : input.getName(),
                normalizeSlug(input == null ? "" : input.getSlug(), input == null ? "" : input.getName()),
                input == null ? "" : input.getPrompt(),
                input == null ? "" : input.getTrigger(),
                input == null ? Collections.emptyList() : input.getToolNames(),
                input == null ? Collections.emptyList() : input.getMcpIds(),
                createdAt,
                now
        );
        ContentValues values = new ContentValues();
        values.put("id", saved.getId());
        values.put("enabled", saved.isEnabled() ? 1 : 0);
        values.put("name", saved.getName());
        values.put("slug", saved.getSlug());
        values.put("prompt", saved.getPrompt());
        values.put("trigger", saved.getTrigger());
        values.put("tool_names_json", jsonArray(saved.getToolNames()).toString());
        values.put("mcp_ids_json", jsonArray(saved.getMcpIds()).toString());
        values.put("created_at", saved.getCreatedAt());
        values.put("updated_at", saved.getUpdatedAt());
        values.put("raw_json", "");
        database.getWritableDatabase().insertWithOnConflict("extension_agents", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return saved;
    }

    public synchronized void setAgentEnabled(String id, boolean enabled) {
        updateEnabled("extension_agents", id, enabled);
    }

    public synchronized void deleteAgent(String id) {
        database.getWritableDatabase().delete("extension_agents", "id = ?", new String[] {safe(id)});
    }

    public synchronized List<ExtensionMcpConfig> getMcpExtensions() {
        ArrayList<ExtensionMcpConfig> mcps = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, enabled, name, url, request_headers_json, tools_json, created_at, updated_at "
                        + "FROM extension_mcps ORDER BY updated_at DESC",
                new String[0]
        );
        try {
            while (cursor.moveToNext()) {
                mcps.add(new ExtensionMcpConfig(
                        value(cursor, "id"),
                        intValue(cursor, "enabled") != 0,
                        value(cursor, "name"),
                        value(cursor, "url"),
                        headersFromJson(value(cursor, "request_headers_json")),
                        toolsFromJson(value(cursor, "tools_json")),
                        longValue(cursor, "created_at"),
                        longValue(cursor, "updated_at")
                ));
            }
        } finally {
            cursor.close();
        }
        return mcps;
    }

    public synchronized ExtensionMcpConfig saveMcpExtension(ExtensionMcpConfig input) {
        long now = System.currentTimeMillis();
        String id = input == null || input.getId().length() == 0 ? nextId("mcp") : input.getId();
        long createdAt = input == null || input.getCreatedAt() <= 0 ? now : input.getCreatedAt();
        ExtensionMcpConfig saved = new ExtensionMcpConfig(
                id,
                input == null || input.isEnabled(),
                input == null ? "" : input.getName(),
                normalizeHttpUrl(input == null ? "" : input.getUrl()),
                input == null ? Collections.emptyList() : input.getRequestHeaders(),
                input == null ? Collections.emptyList() : input.getTools(),
                createdAt,
                now
        );
        ContentValues values = new ContentValues();
        values.put("id", saved.getId());
        values.put("enabled", saved.isEnabled() ? 1 : 0);
        values.put("name", saved.getName());
        values.put("url", saved.getUrl());
        values.put("request_headers_json", headersToJson(saved.getRequestHeaders()).toString());
        values.put("tools_json", toolsToJson(saved.getTools()).toString());
        values.put("created_at", saved.getCreatedAt());
        values.put("updated_at", saved.getUpdatedAt());
        values.put("raw_json", "");
        database.getWritableDatabase().insertWithOnConflict("extension_mcps", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return saved;
    }

    public synchronized void setMcpEnabled(String id, boolean enabled) {
        updateEnabled("extension_mcps", id, enabled);
    }

    public synchronized void deleteMcp(String id) {
        database.getWritableDatabase().delete("extension_mcps", "id = ?", new String[] {safe(id)});
    }

    public List<McpToolSummary> queryMcpTools(String url, List<McpRequestHeader> headers) throws Exception {
        String endpoint = normalizeHttpUrl(url);
        JSONObject body = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", "linecode_" + System.currentTimeMillis())
                .put("method", "tools/list")
                .put("params", new JSONObject());
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("Content-Type", "application/json");
            for (McpRequestHeader header : headers == null ? Collections.<McpRequestHeader>emptyList() : headers) {
                if (header.getName().length() > 0) {
                    connection.setRequestProperty(header.getName(), header.getValue());
                }
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(bytes);
            int code = connection.getResponseCode();
            String text = readStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IllegalStateException(code + ": " + text);
            }
            List<McpToolSummary> tools = parseMcpToolResponse(text);
            if (tools.isEmpty()) {
                throw new IllegalStateException("没有在 MCP 响应中找到 tools 列表。");
            }
            return tools;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public synchronized List<SkillRecord> getSkills(String homePath) {
        ensureSkillRoots(homePath);
        upsertDiscoveredSkills(discoverSkills(homePath));
        return readSkills();
    }

    public synchronized SkillRecord createSkill(String homePath, String location, String name, String description, String content) {
        ensureSkillRoots(homePath);
        String safeName = safe(name).trim();
        if (safeName.length() == 0) {
            safeName = "linecode-skill-" + System.currentTimeMillis();
        }
        String normalizedLocation = SkillRecord.normalizeLocation(location);
        File root = localSkillRoot(homePath, normalizedLocation);
        File skillDir = uniqueChild(root, sanitizeFileName(safeName));
        if (!skillDir.exists()) {
            skillDir.mkdirs();
        }
        File skillFile = new File(skillDir, "SKILL.md");
        writeUtf8(skillFile, buildSkillMarkdown(safeName, description, content));
        SkillRecord record = parseSkill(skillDir, skillFile, normalizedLocation);
        upsertDiscoveredSkills(Collections.singletonList(record));
        return record;
    }

    public synchronized SkillRecord installSkill(String homePath, String location, String sourcePath, String name) throws Exception {
        ensureSkillRoots(homePath);
        File source = new File(safe(sourcePath).trim()).getCanonicalFile();
        if (!source.exists()) {
            throw new IllegalArgumentException("Skill 来源不存在: " + sourcePath);
        }
        String normalizedLocation = SkillRecord.normalizeLocation(location);
        File root = localSkillRoot(homePath, normalizedLocation);
        String baseName = sanitizeFileName(safe(name).trim().length() == 0 ? stripExtension(source.getName()) : name);
        File target = uniqueChild(root, baseName);
        if (source.isDirectory()) {
            copyDirectory(source, target);
        } else if (source.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            unzip(source, target);
        } else if ("skill.md".equalsIgnoreCase(source.getName())) {
            target.mkdirs();
            copyFile(source, new File(target, "SKILL.md"));
        } else {
            throw new IllegalArgumentException("仅支持目录、SKILL.md 或 .zip 技能包。");
        }
        File skillMd = findSkillMd(target, 0);
        if (skillMd == null) {
            throw new IllegalArgumentException("安装完成，但没有找到 SKILL.md。");
        }
        SkillRecord record = parseSkill(skillMd.getParentFile(), skillMd, normalizedLocation);
        upsertDiscoveredSkills(Collections.singletonList(record));
        return record;
    }

    public synchronized SkillRecord installSkillFromUri(String homePath, String location, String uri, String displayName) throws Exception {
        ensureSkillRoots(homePath);
        String fileName = skillImportFileName(displayName);
        File tempRoot = new File(workspacePaths.getLinecodeRoot(), "tmp/skills");
        File tempDir = uniqueChild(tempRoot, stripExtension(fileName));
        File tempFile = new File(tempDir, fileName.toLowerCase(Locale.ROOT).endsWith(".zip") ? fileName : "SKILL.md");
        copyUriToFile(uri, tempFile);
        try {
            return installSkill(homePath, location, tempFile.getAbsolutePath(), stripExtension(fileName));
        } finally {
            deleteRecursive(tempDir);
        }
    }

    public synchronized void setSkillEnabled(String id, boolean enabled) {
        updateEnabled("skills", id, enabled);
    }

    public synchronized void deleteSkill(String id) {
        SkillRecord target = findSkill(id);
        database.getWritableDatabase().delete("skills", "id = ?", new String[] {safe(id)});
        if (target != null && !SkillRecord.LOCATION_SSH.equals(target.getLocation()) && target.getRootPath().length() > 0) {
            deleteRecursive(new File(target.getRootPath()));
        }
    }

    public synchronized String buildExtensionPrompt(String homePath) {
        StringBuilder builder = new StringBuilder();
        List<ExtensionAgentConfig> agents = getAgentExtensions();
        List<ExtensionMcpConfig> mcps = getMcpExtensions();
        List<SkillRecord> skills = getSkills(homePath);
        boolean hasContent = false;

        builder.append("## 扩展\n以下扩展来自设置里的“扩展”页面。自定义 Agent、HTTP MCP 和 Skills 都由 SQLite 配置动态注入。\n");
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
                String header = "#### Skill: " + skill.getName()
                        + "\n安装位置: " + skill.getLocationLabel()
                        + "\nSKILL.md: " + skill.getSkillMdPath()
                        + "\nRoot: " + skill.getRootPath();
                String body = readSkillPrompt(skill);
                String block = body.length() == 0 ? header : header + "\n\n" + body;
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

    public ArrayList<String> skillWriteRoots(String homePath) {
        ArrayList<String> roots = new ArrayList<>();
        ensureSkillRoots(homePath);
        roots.add(workspacePaths.getSkillsRoot().getAbsolutePath());
        if (safe(homePath).trim().length() > 0) {
            roots.add(new File(homePath, ".linecode/skills").getAbsolutePath());
        }
        return roots;
    }

    private void ensureSkillRoots(String homePath) {
        workspacePaths.ensurePrivateRoots();
        ensureBuiltInSkills();
        if (safe(homePath).trim().length() > 0) {
            new File(homePath, ".linecode/skills").mkdirs();
        }
    }

    private void ensureBuiltInSkills() {
        File root = workspacePaths.getSkillsRoot();
        File creator = new File(root, "skill-creator/SKILL.md");
        syncBuiltInSkill(creator, builtInSkillCreatorContent());
        File installer = new File(root, "skill-installer/SKILL.md");
        syncBuiltInSkill(installer, builtInSkillInstallerContent());
    }

    private void syncBuiltInSkill(File skillMd, String content) {
        if (!skillMd.exists()) {
            writeUtf8(skillMd, content);
            return;
        }
        String existing = readUtf8(skillMd, 12000);
        if (existing.contains("skill" + "_create") || existing.contains("skill" + "_install")) {
            writeUtf8(skillMd, content);
        }
    }

    private String builtInSkillCreatorContent() {
        return "---\n"
                + "name: skill-creator\n"
                + "description: 创建和维护 LineCode SKILL.md 技能。\n"
                + "---\n\n"
                + "# Skill Creator\n\n"
                + "当用户要求沉淀流程、复用经验或创建新 Skill 时使用。\n\n"
                + "## 步骤\n"
                + "- Skills 的创建属于扩展系统，不是可调用 Tool；优先通过扩展页创建，学习模式也可以自动沉淀。\n"
                + "- 明确触发条件、适用范围、输入、输出和验证方式。\n"
                + "- 需要维护已授权 Skills 目录时，只使用普通文件读写、搜索和列目录工具操作 `SKILL.md`。\n"
                + "- `SKILL.md` 应包含 name、description、触发条件、步骤、常见坑和验证方式。\n"
                + "- 不要写入 API key、token、密码或一次性任务进度。\n";
    }

    private String builtInSkillInstallerContent() {
        return "---\n"
                + "name: skill-installer\n"
                + "description: 安装本地目录、SKILL.md 或 ZIP 技能包。\n"
                + "---\n\n"
                + "# Skill Installer\n\n"
                + "当用户提供技能包路径，或需要把当前工作区的技能安装到全局/项目 Skills 目录时使用。\n\n"
                + "## 步骤\n"
                + "- Skills 的安装属于扩展系统，不是可调用 Tool；优先通过扩展页安装本地目录、`SKILL.md` 或 `.zip`。\n"
                + "- `location=app` 安装到应用私有全局 Skills 目录。\n"
                + "- `location=project` 安装到当前工作区 `.linecode/skills`。\n"
                + "- SSH 模式的目标路径是 `~/.linecode/skills`，可通过 SSH Shell 操作。\n"
                + "- 安装后检查 `SKILL.md` 可读，并确认扩展页列表中已启用。\n";
    }

    private List<SkillRecord> discoverSkills(String homePath) {
        ArrayList<SkillRecord> found = new ArrayList<>();
        scanSkillRoot(workspacePaths.getSkillsRoot(), SkillRecord.LOCATION_APP, found);
        if (safe(homePath).trim().length() > 0) {
            scanSkillRoot(new File(homePath, ".linecode/skills"), SkillRecord.LOCATION_PROJECT, found);
        }
        return found;
    }

    private void scanSkillRoot(File root, String location, ArrayList<SkillRecord> found) {
        if (root == null || !root.exists() || !root.isDirectory()) {
            return;
        }
        scanDir(root, location, found, 0);
    }

    private void scanDir(File dir, String location, ArrayList<SkillRecord> found, int depth) {
        if (dir == null || depth > MAX_SCAN_DEPTH) {
            return;
        }
        File skillMd = new File(dir, "SKILL.md");
        if (skillMd.exists() && skillMd.isFile()) {
            found.add(parseSkill(dir, skillMd, location));
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                scanDir(child, location, found, depth + 1);
            }
        }
    }

    private SkillRecord parseSkill(File root, File skillMd, String location) {
        String content = readUtf8(skillMd, 20000);
        String fallbackName = root == null ? "Skill" : root.getName();
        String name = firstNonEmpty(frontmatterValue(content, "name"), markdownTitle(content), fallbackName);
        String description = firstNonEmpty(frontmatterValue(content, "description"), descriptionLine(content), "");
        long updatedAt = skillMd.lastModified() <= 0 ? System.currentTimeMillis() : skillMd.lastModified();
        return new SkillRecord(
                skillId(location, skillMd.getAbsolutePath()),
                name,
                description,
                root == null ? "" : root.getAbsolutePath(),
                skillMd.getAbsolutePath(),
                location,
                true,
                updatedAt,
                updatedAt
        );
    }

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

    private File localSkillRoot(String homePath, String location) {
        if (SkillRecord.LOCATION_PROJECT.equals(location)) {
            if (safe(homePath).trim().length() == 0) {
                throw new IllegalArgumentException("当前工作区路径为空，无法安装到项目 Skills。");
            }
            return new File(homePath, ".linecode/skills");
        }
        if (SkillRecord.LOCATION_SSH.equals(location)) {
            throw new IllegalArgumentException("SSH Skills 目录请在 SSH Shell 中使用 ~/.linecode/skills 操作。");
        }
        return workspacePaths.getSkillsRoot();
    }

    private String readSkillPrompt(SkillRecord skill) {
        if (skill == null || SkillRecord.LOCATION_SSH.equals(skill.getLocation())) {
            return "";
        }
        File skillMd = new File(skill.getSkillMdPath());
        if (!skillMd.exists() || !skillMd.isFile()) {
            return "";
        }
        return readUtf8(skillMd, 6000);
    }

    private String buildSkillMarkdown(String name, String description, String content) {
        String body = safe(content).trim();
        if (body.length() == 0) {
            body = "# " + safe(name).trim() + "\n\n"
                    + "## 触发条件\n"
                    + "- 当任务与 " + safe(name).trim() + " 相关时使用。\n\n"
                    + "## 步骤\n"
                    + "- 阅读当前任务和项目上下文。\n"
                    + "- 按项目既有规范执行。\n"
                    + "- 完成后给出验证结果。\n";
        }
        return "---\n"
                + "name: " + safe(name).trim() + "\n"
                + "description: " + safe(description).trim() + "\n"
                + "---\n\n"
                + body + "\n";
    }

    private ArrayList<String> jsonStringList(String raw) {
        ArrayList<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(safe(raw).length() == 0 ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (value.length() > 0) {
                    values.add(value);
                }
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    private JSONArray jsonArray(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                if (value != null && value.trim().length() > 0) {
                    array.put(value.trim());
                }
            }
        }
        return array;
    }

    private JSONArray headersToJson(List<McpRequestHeader> headers) {
        JSONArray array = new JSONArray();
        if (headers == null) {
            return array;
        }
        for (McpRequestHeader header : headers) {
            if (header.getName().length() == 0) {
                continue;
            }
            try {
                JSONObject item = new JSONObject();
                item.put("name", header.getName());
                item.put("value", header.getValue());
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        return array;
    }

    private ArrayList<McpRequestHeader> headersFromJson(String raw) {
        ArrayList<McpRequestHeader> headers = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(safe(raw).length() == 0 ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null && item.optString("name").trim().length() > 0) {
                    headers.add(new McpRequestHeader(item.optString("name"), item.optString("value")));
                }
            }
        } catch (Exception ignored) {
        }
        return headers;
    }

    private JSONArray toolsToJson(List<McpToolSummary> tools) {
        JSONArray array = new JSONArray();
        if (tools == null) {
            return array;
        }
        for (McpToolSummary tool : tools) {
            if (tool.getName().length() == 0) {
                continue;
            }
            try {
                JSONObject item = new JSONObject();
                item.put("name", tool.getName());
                item.put("enabled", tool.isEnabled());
                item.put("description", tool.getDescription());
                if (tool.getInputSchemaJson().length() > 0) {
                    item.put("inputSchema", new JSONObject(tool.getInputSchemaJson()));
                }
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        return array;
    }

    private ArrayList<McpToolSummary> toolsFromJson(String raw) {
        try {
            return parseToolList(safe(raw).length() == 0 ? new JSONArray() : new JSONArray(raw));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<McpToolSummary> parseMcpToolResponse(String text) throws Exception {
        String jsonText = extractJsonFromEventStream(text);
        JSONObject parsed = new JSONObject(jsonText);
        JSONObject result = parsed.optJSONObject("result");
        JSONObject data = parsed.optJSONObject("data");
        Object rawTools = result == null ? null : result.opt("tools");
        if (rawTools == null) {
            rawTools = parsed.opt("tools");
        }
        if (rawTools == null && data != null) {
            rawTools = data.opt("tools");
        }
        if (rawTools == null && result != null) {
            rawTools = result.opt("servers");
        }
        if (rawTools == null) {
            rawTools = parsed.opt("servers");
        }
        return rawTools instanceof JSONArray ? parseToolList((JSONArray) rawTools) : Collections.emptyList();
    }

    private ArrayList<McpToolSummary> parseToolList(JSONArray array) {
        ArrayList<McpToolSummary> tools = new ArrayList<>();
        if (array == null) {
            return tools;
        }
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw instanceof String) {
                tools.add(new McpToolSummary((String) raw, true, "", ""));
            } else if (raw instanceof JSONObject) {
                JSONObject item = (JSONObject) raw;
                String name = item.optString("name").trim();
                if (name.length() == 0) {
                    continue;
                }
                JSONObject schema = item.optJSONObject("inputSchema");
                if (schema == null) {
                    schema = item.optJSONObject("input_schema");
                }
                if (schema == null) {
                    schema = item.optJSONObject("schema");
                }
                tools.add(new McpToolSummary(
                        name,
                        item.optBoolean("enabled", true),
                        item.optString("description"),
                        schema == null ? "" : schema.toString()
                ));
            }
        }
        return tools;
    }

    private String extractJsonFromEventStream(String text) {
        StringBuilder data = new StringBuilder();
        String[] lines = safe(text).split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String value = line.substring("data:".length()).trim();
            if (value.length() == 0 || "[DONE]".equals(value)) {
                continue;
            }
            data.append(value);
        }
        return data.length() == 0 ? safe(text) : data.toString();
    }

    private void updateEnabled(String table, String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update(table, values, "id = ?", new String[] {safe(id)});
    }

    private String normalizeHttpUrl(String raw) {
        String value = UrlPolicy.requireHttpOrLocalCleartextUrl(raw, "MCP 地址");
        while (value.endsWith("/") && value.length() > "https://x".length()) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeSlug(String raw, String fallback) {
        String source = safe(raw).trim().length() == 0 ? safe(fallback) : safe(raw);
        String lower = source.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                builder.append(ch);
            } else if (Character.isWhitespace(ch)) {
                builder.append('-');
            }
        }
        String value = builder.toString();
        while (value.contains("--")) {
            value = value.replace("--", "-");
        }
        value = trim(value, '-');
        return value.length() == 0 ? "agent-" + System.currentTimeMillis() : value;
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

    private String firstNonEmpty(String first, String second, String third) {
        if (safe(first).trim().length() > 0) {
            return first.trim();
        }
        if (safe(second).trim().length() > 0) {
            return second.trim();
        }
        return safe(third).trim();
    }

    private String frontmatterValue(String content, String key) {
        String[] lines = safe(content).split("\\r?\\n");
        boolean inFrontmatter = lines.length > 0 && "---".equals(lines[0].trim());
        int start = inFrontmatter ? 1 : 0;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (inFrontmatter && "---".equals(line)) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (key.equalsIgnoreCase(line.substring(0, colon).trim())) {
                return unquote(line.substring(colon + 1).trim());
            }
        }
        return "";
    }

    private String markdownTitle(String content) {
        String[] lines = safe(content).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    private String descriptionLine(String content) {
        String[] lines = safe(content).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("---") || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("description:")) {
                return trimmed.substring("description:".length()).trim();
            }
            return trimmed;
        }
        return "";
    }

    private File findSkillMd(File path, int depth) {
        if (path == null || depth > MAX_SCAN_DEPTH || !path.exists()) {
            return null;
        }
        if (path.isFile()) {
            return "skill.md".equalsIgnoreCase(path.getName()) ? path : null;
        }
        File direct = new File(path, "SKILL.md");
        if (direct.exists() && direct.isFile()) {
            return direct;
        }
        File[] children = path.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findSkillMd(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private File uniqueChild(File root, String baseName) {
        root.mkdirs();
        File child = new File(root, baseName);
        if (!child.exists()) {
            return child;
        }
        return new File(root, baseName + "_" + System.currentTimeMillis());
    }

    private String sanitizeFileName(String name) {
        String value = safe(name).trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length() && builder.length() < 96; i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '_' || ch == '-') {
                builder.append(ch);
            } else if (Character.isWhitespace(ch)) {
                builder.append('-');
            }
        }
        String clean = trim(builder.toString(), '-');
        return clean.length() == 0 ? "skill_" + System.currentTimeMillis() : clean;
    }

    private String stripExtension(String name) {
        String value = safe(name);
        int index = value.lastIndexOf('.');
        return index > 0 ? value.substring(0, index) : value;
    }

    private String skillImportFileName(String displayName) {
        String value = safe(displayName).trim();
        if (value.length() == 0) {
            return "skill.zip";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return sanitizeFileName(value);
        }
        if (lower.endsWith(".md")) {
            return "SKILL.md";
        }
        return sanitizeFileName(value) + ".zip";
    }

    private String skillId(String location, String skillMdPath) {
        String source = SkillRecord.normalizeLocation(location) + ":" + safe(skillMdPath);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == ':' || ch == '/' || ch == '.' || ch == '-') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private JSONObject parseJson(String raw) {
        try {
            return new JSONObject(safe(raw).length() == 0 ? "{}" : raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private String readStream(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }

    private String readUtf8(File file, int maxChars) {
        try {
            String text = readStream(new FileInputStream(file));
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void writeUtf8(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileOutputStream output = new FileOutputStream(file, false);
            try {
                output.write(safe(content).getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("写入 Skill 失败: " + file.getPath(), e);
        }
    }

    private void copyDirectory(File source, File target) throws Exception {
        if (!target.exists()) {
            target.mkdirs();
        }
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File next = new File(target, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, next);
            } else {
                copyFile(child, next);
            }
        }
    }

    private void copyFile(File source, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
        try {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target, false));
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private void copyUriToFile(String uri, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        InputStream rawInput = context.getContentResolver().openInputStream(Uri.parse(safe(uri)));
        if (rawInput == null) {
            throw new IllegalArgumentException("无法读取选择的 Skill 文件。");
        }
        BufferedInputStream input = new BufferedInputStream(rawInput);
        try {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target, false));
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private void unzip(File source, File target) throws Exception {
        target.mkdirs();
        File canonicalTarget = target.getCanonicalFile();
        ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(source)));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File out = new File(target, entry.getName()).getCanonicalFile();
                if (!out.getPath().equals(canonicalTarget.getPath())
                        && !out.getPath().startsWith(canonicalTarget.getPath() + File.separator)) {
                    throw new IllegalArgumentException("ZIP 包含越界路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(out, false));
                    try {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    } finally {
                        output.close();
                    }
                }
                input.closeEntry();
            }
        } finally {
            input.close();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private String unquote(String value) {
        String text = safe(value).trim();
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private String trim(String value, char ch) {
        String text = safe(value);
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == ch) {
            start++;
        }
        while (end > start && text.charAt(end - 1) == ch) {
            end--;
        }
        return text.substring(start, end);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String value(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private int intValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? 0L : cursor.getLong(index);
    }
}
