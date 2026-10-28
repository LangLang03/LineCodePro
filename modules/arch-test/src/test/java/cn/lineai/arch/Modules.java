package cn.lineai.arch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File-system helpers shared by the architecture tests. No ArchUnit, no project dependencies. */
final class Modules {

    static final String ROOT_PROPERTY = "linecode.arch.root";
    static final File ROOT = validateRoot(System.getProperty(ROOT_PROPERTY));

    private Modules() {
    }

    static File validateRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.trim().isEmpty()) {
            throw new IllegalStateException("Missing system property: " + ROOT_PROPERTY);
        }
        File root = new File(configuredRoot);
        if (!root.isAbsolute()) {
            throw new IllegalStateException(ROOT_PROPERTY + " must be absolute: " + root);
        }
        try {
            root = root.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot resolve repository root: " + root, e);
        }
        requireDirectory(root);
        requireFile(new File(root, "settings.gradle.kts"));
        requireDirectory(new File(root, "modules"));
        requireFile(new File(root, "modules/arch-test/build.gradle.kts"));
        return root;
    }

    private static void requireDirectory(File dir) {
        if (!dir.isDirectory() || !dir.canRead()) {
            throw new IllegalStateException("Missing or unreadable scan directory: " + dir);
        }
    }

    private static void requireFile(File file) {
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalStateException("Missing or unreadable scan file: " + file);
        }
    }

    static Map<String, String> packageRoots() {
        Map<String, String> roots = new LinkedHashMap<>();
        roots.put("core-model", "cn.lineai.core.model");
        roots.put("core-kernel", "cn.lineai.core.kernel");
        roots.put("core-mvi", "cn.lineai.core.mvi");
        roots.put("core-service", "cn.lineai.core.service");
        roots.put("engine-protocol", "cn.lineai.engine.protocol");
        roots.put("engine-prompt", "cn.lineai.engine.prompt");
        roots.put("engine-ai", "cn.lineai.engine.ai");
        roots.put("engine-agent", "cn.lineai.engine.agent");
        roots.put("tool-api", "cn.lineai.tool.api");
        roots.put("tool-builtin", "cn.lineai.tool.workspace");
        roots.put("tool-mcp", "cn.lineai.tool.mcp");
        roots.put("tool-media", "cn.lineai.tool.media");
        roots.put("tool-agent", "cn.lineai.tool.agent");
        roots.put("tool-phone", "cn.lineai.tool.phone");
        roots.put("engine-skillhub", "cn.lineai.engine.skillhub");
        roots.put("engine-local", "cn.lineai.engine.local");
        roots.put("data", "cn.lineai.data");
        roots.put("ui-tokens", "cn.lineai.ui.tokens");
        roots.put("ui-i18n", "cn.lineai.ui.i18n");
        roots.put("ui-kit", "cn.lineai.ui.kit");
        roots.put("ui-markdown", "cn.lineai.ui.markdown");
        roots.put("feature-chat", "cn.lineai.feature.chat");
        roots.put("feature-settings", "cn.lineai.feature.settings");
        roots.put("feature-models", "cn.lineai.feature.models");
        roots.put("feature-extensions", "cn.lineai.feature.extensions");
        roots.put("feature-workspace", "cn.lineai.feature.workspace");
        roots.put("feature-terminal", "cn.lineai.feature.terminal");
        roots.put("platform-android", "cn.lineai.platform");
        roots.put("runtime", "cn.lineai.runtime");
        return roots;
    }

    static List<File> javaFiles(String module) {
        requireFile(new File(ROOT, "modules/" + module + "/build.gradle.kts"));
        List<File> result = new ArrayList<>();
        File main = new File(ROOT, "modules/" + module + "/src/main/java");
        if (main.isDirectory()) {
            collect(main, result);
        }
        File local = new File(ROOT, "modules/" + module + "/src/local/java");
        if (local.isDirectory()) {
            collect(local, result);
        }
        // Resource-only modules legitimately have no Java directory in a clean checkout.
        return result;
    }

    static List<File> javaFilesIn(File dir) {
        List<File> result = new ArrayList<>();
        collect(dir, result);
        return result;
    }

    private static void collect(File dir, List<File> result) {
        for (File child : listDirectory(dir)) {
            if (child.isDirectory()) {
                collect(child, result);
            } else if (child.getName().endsWith(".java")) {
                requireFile(child);
                result.add(child);
            }
        }
    }

    private static File[] listDirectory(File dir) {
        requireDirectory(dir);
        File[] children = dir.listFiles();
        if (children == null) {
            throw new IllegalStateException("Cannot list scan directory: " + dir);
        }
        java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
        return children;
    }

    static String read(File file) {
        requireFile(file);
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read scan file: " + file, e);
        }
    }

    static String location(File file, String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return file.getAbsolutePath() + ":" + line;
    }

    // Match literals before comments so URLs, escaped quotes and comment-like text stay intact.
    static final Pattern LITERAL = Pattern.compile("\"(?:\\\\[\\s\\S]|[^\"\\\\])*\"|'(?:\\\\[\\s\\S]|[^'\\\\])*'");
    private static final Pattern LITERAL_OR_COMMENT = Pattern.compile(
            LITERAL.pattern() + "|/\\*[\\s\\S]*?\\*/|//[^\\r\\n]*");

    static String stripComments(String source) {
        return mask(source, false);
    }

    static String codeOnly(String source) {
        return mask(source, true);
    }

    private static String mask(String source, boolean maskLiterals) {
        StringBuilder result = new StringBuilder(source);
        Matcher matcher = LITERAL_OR_COMMENT.matcher(source);
        while (matcher.find()) {
            if (maskLiterals || source.charAt(matcher.start()) == '/') {
                for (int i = matcher.start(); i < matcher.end(); i++) {
                    char ch = source.charAt(i);
                    if (ch != '\n' && ch != '\r') {
                        result.setCharAt(i, ' ');
                    }
                }
            }
        }
        return result.toString();
    }

    static String packageOf(String source) {
        Matcher matcher = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;").matcher(codeOnly(source));
        return matcher.find() ? matcher.group(1) : "";
    }

    /** All modules (directory names) that declare a build script. */
    static List<String> modules() {
        List<String> result = new ArrayList<>();
        File dir = new File(ROOT, "modules");
        for (File child : listDirectory(dir)) {
            if (child.isDirectory() && new File(child, "build.gradle.kts").isFile()) {
                result.add(child.getName());
            }
        }
        for (String expected : packageRoots().keySet()) {
            requireFile(new File(dir, expected + "/build.gradle.kts"));
        }
        requireFile(new File(dir, "arch-test/build.gradle.kts"));
        if (result.isEmpty()) {
            throw new IllegalStateException("No modules scanned in " + dir);
        }
        return result;
    }
}
