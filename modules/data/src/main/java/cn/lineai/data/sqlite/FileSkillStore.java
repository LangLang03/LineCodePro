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

/**
 * File-based SKILL.md repository; the enable flag lives in settings.
 *
 * <p>Each skill is a directory holding one SKILL.md (or a single top-level SKILL.md file), so the
 * files stay plain and user-editable. Only the disabled set is persisted, keeping uninstalled
 * skills enabled by default. Operations are synchronous; callers own the scheduler.</p>
 */
public final class FileSkillStore implements SkillStore {

    /** Settings key holding the newline-separated identifiers of disabled skills. */
    static final String KEY_DISABLED = "skills.disabled";

    private final File root;
    private final SettingsStore settings;
    private final Clock clock;

    /**
     * Creates the store.
     *
     * @param root directory containing the skill folders
     * @param settings settings store backing the disabled set
     * @param clock clock used when file timestamps are unavailable
     */
    public FileSkillStore(File root, SettingsStore settings, Clock clock) {
        this.root = root;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Scans the skill root, parses every SKILL.md and applies the disabled set.
     *
     * @return all installed skills sorted alphabetically by name
     */
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

    /**
     * Filters {@link #list()} down to the enabled skills.
     *
     * @return skills offered to the model
     */
    @Override public List<Skill> enabled() {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : list()) {
            if (skill.enabled) result.add(skill);
        }
        return result;
    }

    /**
     * Serializes the skill to a SKILL.md file in a unique directory and re-parses it, so callers
     * see the stored form rather than the input.
     *
     * @param input skill to persist; a blank name is rejected
     * @return the stored skill with the requested enabled flag
     * @throws DomainException with {@code ErrorCode.INVALID_INPUT} when the name is blank, or
     *         {@code ErrorCode.STORAGE} when the directory or file cannot be written
     */
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

    /**
     * Imports a ZIP archive or a single SKILL.md into a fresh skill directory. The payload is
     * unpacked into a temporary directory and installed only after a SKILL.md was found and
     * parsed, so a failed import leaves no partial skill behind.
     *
     * @param input stream holding a ZIP archive or a SKILL.md document
     * @param displayName original file name; the extension selects the import format
     * @return the imported skill, installed and enabled
     * @throws IOException when the format is unsupported, no SKILL.md can be located or parsed,
     *         or the extraction fails
     */
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

    /**
     * Updates the disabled set in settings; enabling removes the identifier, disabling adds it.
     *
     * @param id skill identifier
     * @param enabled whether the skill is offered to the model
     */
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

    /**
     * Removes the skill directory and re-enables the identifier so a future re-import starts
     * enabled.
     *
     * @param id skill identifier; unknown identifiers are ignored
     */
    @Override public void delete(String id) {
        File dir = new File(root, id);
        if (dir.isDirectory()) deleteRecursive(dir);
        setEnabled(id, true);
    }

    /** Reads and parses one SKILL.md into a skill; returns null when the file is unreadable. */
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

    /** Serializes a skill into the front-matter markdown format written to disk. */
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

    /** Extracts a ZIP stream, rejecting entries with absolute or parent-traversing paths. */
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

    /** Copies a stream to a file. */
    private static void copy(InputStream input, File target) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }
        }
    }

    /** Copies a directory tree recursively. */
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

    /** Locates a SKILL.md within two directory levels, tolerating nested archive wrappers. */
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

    /** Picks an unused directory name derived from the skill name. */
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

    /** Creates the skill root on demand. */
    private void ensureRoot() {
        if (!root.exists() && !root.mkdirs()) {
            throw new DomainException(ErrorCode.STORAGE, "cannot create skills directory");
        }
    }

    /** Parses the settings value back into the disabled identifier set. */
    private Set<String> disabledIds() {
        Set<String> result = new HashSet<>();
        String raw = settings.get(KEY_DISABLED, "");
        for (String line : raw.split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    /** Deletes a directory tree recursively; missing paths are ignored. */
    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    /** Flattens a value onto one line so it cannot break the front-matter format. */
    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** Reduces a name to a filesystem-safe directory name of bounded length. */
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

    /** Removes a single trailing file extension. */
    private static String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    /** Reads one key from the leading YAML-style front-matter block, if present. */
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

    /** Extracts the first level-one heading as the fallback display name. */
    private static String markdownTitle(String content) {
        String body = stripFrontmatter(content);
        for (String line : ofLines(body)) {
            String value = line.trim();
            if (value.startsWith("# ")) return value.substring(2).trim();
            if (!value.isEmpty()) return "";
        }
        return "";
    }

    /** Extracts the first non-heading body line as the fallback description. */
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

    /** Removes the leading front-matter block, if present. */
    private static String stripFrontmatter(String content) {
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("\n---", 3);
        return end < 0 ? content : content.substring(end + 4);
    }

    /** Splits text into lines without dropping trailing empty ones. */
    private static List<String> ofLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) lines.add(line);
        return lines;
    }

    /** Returns the first non-empty argument, else the fallback. */
    private static String firstNonEmpty(String first, String second, String fallback) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return fallback;
    }
}
