package cn.lineai.data.service;

import android.content.Context;
import android.net.Uri;
import cn.lineai.R;
import cn.lineai.model.SkillRecord;
import cn.lineai.model.Strings;
import cn.lineai.resource.ResourceProvider;
import cn.lineai.workspace.WorkspacePaths;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 文件系统操作管理器。
 * 负责 Skill 文件扫描、ZIP 解压、目录复制、URI 导入、递归删除等文件 I/O 操作。
 */
public final class SkillFileManager {

    private static final int MAX_SCAN_DEPTH = 4;

    private final Context context;
    private final WorkspacePaths workspacePaths;
    private final ResourceProvider resourceProvider;

    public SkillFileManager(Context context) {
        this.context = context.getApplicationContext();
        this.workspacePaths = new WorkspacePaths(this.context);
        this.resourceProvider = null;
    }

    public SkillFileManager(Context context, ResourceProvider resourceProvider) {
        this.context = context.getApplicationContext();
        this.workspacePaths = new WorkspacePaths(this.context);
        this.resourceProvider = resourceProvider;
    }

    public SkillFileManager(WorkspacePaths workspacePaths, Context context, ResourceProvider resourceProvider) {
        this.context = context.getApplicationContext();
        this.workspacePaths = workspacePaths;
        this.resourceProvider = resourceProvider;
    }

    public WorkspacePaths getWorkspacePaths() {
        return workspacePaths;
    }

    // ── Skill 发现 ──

    /**
     * 扫描应用全局和项目目录下的所有 Skill。
     */
    public List<SkillRecord> discoverSkills(String homePath) {
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

    /**
     * 从 SKILL.md 文件解析一条 SkillRecord。
     */
    public SkillRecord parseSkill(File root, File skillMd, String location) {
        String content = readUtf8(skillMd, 20000);
        String fallbackName = root == null ? "Skill" : root.getName();
        String name = firstNonEmpty(
                SkillFrontmatterParser.frontmatterValue(content, "name"),
                SkillFrontmatterParser.markdownTitle(content),
                fallbackName);
        String description = firstNonEmpty(
                SkillFrontmatterParser.frontmatterValue(content, "description"),
                SkillFrontmatterParser.descriptionLine(content),
                "");
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

    // ── Skill 目录/根路径 ──

    /**
     * 返回指定 location 对应的本地 Skill 存储根目录。
     */
    public File localSkillRoot(String homePath, String location) {
        if (SkillRecord.LOCATION_PROJECT.equals(location)) {
            if (safe(homePath).trim().length() == 0) {
                throw new IllegalArgumentException("当前工作区路径为空，无法安装到项目 Skills。");
            }
            return new File(homePath, ".linecode/skills");
        }
        if (SkillRecord.LOCATION_SSH.equals(location)) {
            String hint = resourceProvider != null
                    ? resourceProvider.getString(R.string.skill_ssh_directory_hint)
                    : "SSH 模式下不支持直接写入 Skill 目录。";
            throw new IllegalArgumentException(hint);
        }
        return workspacePaths.getSkillsRoot();
    }

    /**
     * 确保应用全局和项目 Skills 根目录存在，并同步内置 Skill。
     */
    public void ensureSkillRoots(String homePath) {
        workspacePaths.ensurePrivateRoots();
        ensureBuiltInSkills();
        if (safe(homePath).trim().length() > 0) {
            new File(homePath, ".linecode/skills").mkdirs();
        }
    }

    /**
     * 列出当前 home 路径下允许写入的 Skill 根目录。
     */
    public ArrayList<String> skillWriteRoots(String homePath) {
        ArrayList<String> roots = new ArrayList<>();
        roots.add(workspacePaths.getSkillsRoot().getAbsolutePath());
        if (safe(homePath).trim().length() > 0) {
            roots.add(new File(homePath, ".linecode/skills").getAbsolutePath());
        }
        return roots;
    }

    // ── 内置 Skill 同步 ──

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

    // ── Skill 查找 ──

    /**
     * 在给定路径下递归查找 SKILL.md 文件。
     */
    public File findSkillMd(File path, int depth) {
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

    // ── 文件工具方法 ──

    /**
     * 在 root 下创建不冲突的子目录（名称冲突时追加时间戳）。
     */
    public File uniqueChild(File root, String baseName) {
        root.mkdirs();
        File child = new File(root, baseName);
        if (!child.exists()) {
            return child;
        }
        return new File(root, baseName + "_" + System.currentTimeMillis());
    }

    /**
     * 清理文件名，只保留字母、数字和部分特殊字符。
     */
    public String sanitizeFileName(String name) {
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

    /**
     * 去掉文件扩展名。
     */
    public String stripExtension(String name) {
        String value = safe(name);
        int index = value.lastIndexOf('.');
        return index > 0 ? value.substring(0, index) : value;
    }

    /**
     * 根据显示名推导导入文件名。
     */
    public String skillImportFileName(String displayName) {
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

    /**
     * 生成 Skill 唯一 ID。
     */
    public String skillId(String location, String skillMdPath) {
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

    /**
     * 读取 Skill 的提示词正文。
     */
    public String readSkillPrompt(SkillRecord skill) {
        if (skill == null || SkillRecord.LOCATION_SSH.equals(skill.getLocation())) {
            return "";
        }
        File skillMd = new File(skill.getSkillMdPath());
        if (!skillMd.exists() || !skillMd.isFile()) {
            return "";
        }
        return readUtf8(skillMd, 6000);
    }

    /**
     * 构造 Skill 的 SKILL.md 内容。
     */
    public String buildSkillMarkdown(String name, String description, String content) {
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

    // ── 文件 I/O ──

    public String readUtf8(File file, int maxChars) {
        try {
            String text = readStream(new FileInputStream(file), (long) maxChars + 4);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        } catch (Exception ignored) {
            return "";
        }
    }

    public void writeUtf8(File file, String content) {
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

    private String readStream(InputStream input, long maxBytes) throws Exception {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while (total < maxBytes && (read = input.read(buffer)) != -1) {
                int toWrite = (int) Math.min(read, maxBytes - total);
                output.write(buffer, 0, toWrite);
                total += toWrite;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }

    public void copyDirectory(File source, File target) throws Exception {
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

    public void copyFile(File source, File target) throws Exception {
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

    public void copyUriToFile(String uri, File target) throws Exception {
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

    public void unzip(File source, File target) throws Exception {
        target.mkdirs();
        File canonicalTarget = target.getCanonicalFile();
        ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(source)));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File out = new File(target, entry.getName()).getCanonicalFile();
                if (!out.getPath().equals(canonicalTarget.getPath())
                        && !out.getPath().startsWith(canonicalTarget.getPath() + File.separator)) {
                    throw new IllegalArgumentException(resourceProvider != null
                            ? resourceProvider.getString(R.string.skill_zip_entry_out_of_bounds, entry.getName())
                            : "ZIP 条目越界: " + entry.getName());
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

    public void deleteRecursive(File file) {
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

    // ── 内部辅助 ──

    private String firstNonEmpty(String first, String second, String third) {
        if (safe(first).trim().length() > 0) {
            return first.trim();
        }
        if (safe(second).trim().length() > 0) {
            return second.trim();
        }
        return safe(third).trim();
    }

    private String safe(String value) {
        return Strings.nullToEmpty(value);
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
}
