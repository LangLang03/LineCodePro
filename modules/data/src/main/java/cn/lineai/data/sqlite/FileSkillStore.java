package cn.lineai.data.sqlite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.Skill;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.data.api.SkillStore;

/** File-based SKILL.md repository; the enable flag lives in settings. */
public final class FileSkillStore implements SkillStore {

    static final String KEY_DISABLED = "skills.disabled";

    private final File root;
    private final SettingsStore settings;
    private final Clock clock;

    public FileSkillStore(File root, SettingsStore settings, Clock clock) {
        this.root = root;
        this.settings = settings;
        this.clock = clock;
    }

    @Override public List<Skill> list() {
        List<Skill> skills = new ArrayList<>();
        ensureRoot();
        File[] children = root.listFiles();
        if (children == null) return skills;
        Set<String> disabled = disabledIds();
        for (File child : children) {
            File skillMd = child.isFile() ? child : new File(child, "SKILL.md");
            if (!skillMd.isFile()) continue;
            Skill skill = parse(child.isDirectory() ? child : root, skillMd);
            if (skill == null) continue;
            skills.add(skill.toBuilder().enabled(!disabled.contains(skill.id)).build());
        }
        skills.sort(Comparator.comparing((Skill skill) -> skill.name.toLowerCase(Locale.ROOT)));
        return skills;
    }

    @Override public List<Skill> enabled() {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : list()) {
            if (skill.enabled) result.add(skill);
        }
        return result;
    }

    @Override public Skill save(Skill input) {
        if (input == null || input.name.trim().isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "skill name is required");
        }
        ensureRoot();
        File dir = input.id.isEmpty() ? uniqueDir(input.name) : new File(root, input.id);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DomainException(ErrorCode.STORAGE, "cannot create skill directory");
        }
        File file = new File(dir, "SKILL.md");
        try {
            Files.write(file.toPath(), markdown(input).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DomainException(ErrorCode.STORAGE, "write SKILL.md", e);
        }
        Skill saved = parse(dir, file);
        if (saved == null) throw new DomainException(ErrorCode.STORAGE, "cannot parse saved skill");
        setEnabled(saved.id, input.enabled);
        return saved.toBuilder().enabled(input.enabled).build();
    }

    @Override public synchronized Skill importSkill(InputStream input, String displayName)
            throws IOException {
        ensureRoot();
        File temp = new File(root, ".import-" + System.currentTimeMillis());
        if (!temp.mkdirs()) throw new IOException("cannot create import directory");
        try {
            String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
            if (name.endsWith(".zip")) {
                unzip(input, temp);
            } else if (name.endsWith(".md")) {
                File target = new File(temp, "SKILL.md");
                copy(input, target);
            } else {
                throw new IOException("Only .zip or SKILL.md can be imported");
            }
            File skillMd = findSkillMd(temp, 0);
            if (skillMd == null) throw new IOException("No SKILL.md found in the imported files");
            Skill parsed = parse(skillMd.getParentFile(), skillMd);
            if (parsed == null) throw new IOException("Cannot parse SKILL.md");
            File targetDir = uniqueDir(parsed.name);
            File sourceDir = skillMd.getParentFile();
            if (sourceDir.equals(temp)) {
                if (!temp.renameTo(targetDir)) throw new IOException("cannot move imported skill");
                temp = null;
            } else {
                copyDirectory(sourceDir, targetDir);
            }
            File installed = new File(targetDir, "SKILL.md");
            Skill result = parse(targetDir, installed);
            if (result == null) throw new IOException("Cannot read imported skill");
            return result.toBuilder().enabled(true).build();
        } finally {
            if (temp != null && temp.exists()) deleteRecursive(temp);
        }
    }

    @Override public void setEnabled(String id, boolean enabled) {
        Set<String> disabled = disabledIds();
        if (enabled) {
            disabled.remove(id);
        } else {
            disabled.add(id);
        }
        StringBuilder value = new StringBuilder();
        for (String item : disabled) {
            if (item.isEmpty()) continue;
            if (value.length() > 0) value.append('\n');
            value.append(item);
        }
        settings.put(KEY_DISABLED, value.toString());
    }

    @Override public void delete(String id) {
        File dir = new File(root, id);
        if (dir.isDirectory()) deleteRecursive(dir);
        setEnabled(id, true);
    }

    private Skill parse(File dir, File skillMd) {
        String content;
        try {
            content = new String(Files.readAllBytes(skillMd.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
        String id = dir.getAbsoluteFile().equals(root.getAbsoluteFile())
                ? stripExtension(skillMd.getName()) : dir.getName();
        String name = firstNonEmpty(frontmatter(content, "name"), markdownTitle(content), id);
        String description = firstNonEmpty(frontmatter(content, "description"),
                descriptionLine(content), "");
        return Skill.builder()
                .id(id)
                .name(name)
                .description(description)
                .content(content)
                .updatedAt(skillMd.lastModified() > 0 ? skillMd.lastModified() : clock.nowMs())
                .build();
    }

    private String markdown(Skill input) {
        StringBuilder out = new StringBuilder();
        out.append("---\n");
        out.append("name: ").append(singleLine(input.name)).append('\n');
        if (!input.description.trim().isEmpty()) {
            out.append("description: ").append(singleLine(input.description)).append('\n');
        }
        out.append("---\n\n# ").append(singleLine(input.name)).append("\n\n");
        out.append(input.content == null ? "" : input.content.trim()).append('\n');
        return out.toString();
    }

    private static void unzip(InputStream input, File target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            boolean scanning = true;
            while (scanning) {
                entry = zip.getNextEntry();
                if (entry == null) {
                    scanning = false;
                } else {
                    String entryName = entry.getName().replace('\\', '/');
                    if (entryName.contains("..") || entryName.startsWith("/")) {
                        throw new IOException("Unsafe zip entry: " + entryName);
                    }
                    File file = new File(target, entryName);
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        File parent = file.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (FileOutputStream out = new FileOutputStream(file)) {
                            int count;
                            while ((count = zip.read(buffer)) >= 0) {
                                out.write(buffer, 0, count);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void copy(InputStream input, File target) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }
        }
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (!target.exists() && !target.mkdirs()) throw new IOException("cannot create " + target);
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File next = new File(target, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, next);
            } else {
                try (InputStream in = Files.newInputStream(child.toPath())) {
                    copy(in, next);
                }
            }
        }
    }

    private static File findSkillMd(File dir, int depth) {
        if (dir == null || depth > 2) return null;
        File direct = new File(dir, "SKILL.md");
        if (direct.isFile()) return direct;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            File found = findSkillMd(child, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private File uniqueDir(String name) {
        String base = sanitize(name);
        File dir = new File(root, base);
        int index = 2;
        while (dir.exists()) {
            dir = new File(root, base + "-" + index);
            index++;
        }
        return dir;
    }

    private void ensureRoot() {
        if (!root.exists() && !root.mkdirs()) {
            throw new DomainException(ErrorCode.STORAGE, "cannot create skills directory");
        }
    }

    private Set<String> disabledIds() {
        Set<String> result = new HashSet<>();
        String raw = settings.get(KEY_DISABLED, "");
        for (String line : raw.split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String sanitize(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < 64; i++) {
            char ch = name.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                out.append(ch);
            } else if (Character.isWhitespace(ch) && out.length() > 0
                    && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        String value = out.toString().replaceAll("-+$", "");
        return value.isEmpty() ? "skill-" + System.currentTimeMillis() : value;
    }

    private static String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    private static String frontmatter(String content, String key) {
        if (!content.startsWith("---")) return "";
        int end = content.indexOf("\n---", 3);
        if (end < 0) return "";
        String block = content.substring(3, end);
        for (String line : block.split("\n")) {
            int split = line.indexOf(':');
            if (split <= 0) continue;
            if (!key.equalsIgnoreCase(line.substring(0, split).trim())) continue;
            return line.substring(split + 1).trim();
        }
        return "";
    }

    private static String markdownTitle(String content) {
        String body = stripFrontmatter(content);
        for (String line : ofLines(body)) {
            String value = line.trim();
            if (value.startsWith("# ")) return value.substring(2).trim();
            if (!value.isEmpty()) return "";
        }
        return "";
    }

    private static String descriptionLine(String content) {
        String body = stripFrontmatter(content);
        boolean afterTitle = false;
        for (String line : ofLines(body)) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (!afterTitle && value.startsWith("# ")) {
                afterTitle = true;
                continue;
            }
            if (afterTitle) return value;
        }
        return "";
    }

    private static String stripFrontmatter(String content) {
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("\n---", 3);
        return end < 0 ? content : content.substring(end + 4);
    }

    private static List<String> ofLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) lines.add(line);
        return lines;
    }

    private static String firstNonEmpty(String first, String second, String fallback) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return fallback;
    }
}
