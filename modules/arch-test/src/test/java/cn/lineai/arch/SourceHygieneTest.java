package cn.lineai.arch;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Mechanical hygiene: budgets, forbidden constructs and literal scans. */
public class SourceHygieneTest {

    private static final int MAX_FILE_LINES = 600;
    static final Pattern THREAD_PATTERN = Pattern.compile(
            "\\bnew\\s+(?:java\\s*\\.\\s*lang\\s*\\.\\s*)?Thread\\s*\\("
                    + "|\\bExecutors\\s*\\."
                    + "|\\bnew\\s+(?:android\\s*\\.\\s*os\\s*\\.\\s*)?Handler\\s*\\("
                    + "|\\bThread\\s*\\.\\s*sleep\\s*\\("
                    + "|\\bCountDownLatch\\b"
                    + "|\\bwhile\\s*\\(\\s*true\\s*\\)");
    private static final Pattern STATIC_MUTABLE = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+)?static\\s+"
                    + "(?!final\\b)(?:volatile\\s+)?(?:[\\w.$<>\\[\\]]+\\s+)+(\\w+)\\s*(?:=|;)");
    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    private static final Pattern RAW_JSON = Pattern.compile("rawJson|raw_json");
    /** A Java string literal that starts with {@code {"} — i.e. a hand-written JSON object. */
    private static final Pattern JSON_OBJECT_LITERAL = Pattern.compile("^\"\\{\\s*\\\\\"");
    private static final Pattern STATE_LITERAL =
            Pattern.compile("\"(pending|streaming|accepted|running|complete|completed"
                    + "|failed|cancelled|killed)\"");
    private static final Pattern ENUM_BODY = Pattern.compile("\\benum\\s+\\w+\\b[^;{]*\\{");

    /** Modules plus the :app shell and the frozen :ipc/:terminal-provider providers. */
    private static List<File> scannedFiles() {
        List<File> files = new ArrayList<>();
        for (String root : Modules.scannedRoots()) {
            files.addAll(Modules.sourcesOf(root));
        }
        return files;
    }

    private static boolean isTestRoot(String root) {
        return root.equals("arch-test");
    }

    @Test
    public void filesStayWithinLineBudget() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            int lines = Modules.read(file).split("\n").length;
            if (lines > MAX_FILE_LINES) {
                violations.append(file.getAbsolutePath()).append(':').append(MAX_FILE_LINES + 1)
                        .append(" = ").append(lines).append('\n');
            }
        }
        assertEquals("Files over budget:\n" + violations, 0, violations.length());
    }

    @Test
    public void noRawThreadsOrBlockingLoops() {
        StringBuilder violations = new StringBuilder();
        for (String root : Modules.scannedRoots()) {
            if (isTestRoot(root) || root.equals("platform-android")
                    || root.equals("ipc") || root.equals("terminal-provider")) {
                // platform-android owns the scheduler; the two legacy providers are platform
                // service processes with their own sanctioned pump threads.
                continue;
            }
            for (File file : Modules.sourcesOf(root)) {
                // Documented process-drain exceptions (mirrored in LineCodeVerifyPlugin):
                // blocking process pipes cannot be drained with a timeout without a
                // dedicated reader thread. The list must stay minimal.
                if (file.getName().equals("LocalShellBackend.java")
                        || file.getName().equals("ShellTool.java")
                        || file.getName().equals("NestedAgentRunner.java")) {
                    continue;
                }
                String source = Modules.codeOnly(Modules.read(file));
                Matcher matcher = THREAD_PATTERN.matcher(source);
                while (matcher.find()) {
                    violations.append(Modules.location(file, source, matcher.start())).append('\n');
                }
            }
        }
        assertEquals("Raw threads / blocking loops:\n" + violations, 0, violations.length());
    }

    @Test
    public void noStaticMutableFields() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            String source = Modules.codeOnly(Modules.read(file));
            Matcher matcher = STATIC_MUTABLE.matcher(source);
            while (matcher.find()) {
                violations.append(Modules.location(file, source, matcher.start()))
                        .append(' ').append(matcher.group(1)).append('\n');
            }
        }
        assertEquals("Static mutable fields:\n" + violations, 0, violations.length());
    }

    @Test
    public void noEmptyCatchBlocks() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            String source = Modules.codeOnly(Modules.read(file));
            Matcher matcher = EMPTY_CATCH.matcher(source);
            while (matcher.find()) {
                violations.append(Modules.location(file, source, matcher.start())).append('\n');
            }
        }
        assertEquals("Empty catch blocks:\n" + violations, 0, violations.length());
    }

    @Test
    public void noRawJsonState() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            String source = Modules.codeOnly(Modules.read(file));
            Matcher matcher = RAW_JSON.matcher(source);
            while (matcher.find()) {
                violations.append(Modules.location(file, source, matcher.start())).append('\n');
            }
        }
        assertEquals("rawJson state leaked:\n" + violations, 0, violations.length());
    }

    @Test
    public void noHandWrittenJsonObjects() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            // The preview page intentionally feeds fake wire payloads into the real cards.
            if (file.getName().equals("ToolPreviewSamples.java")) continue;
            String source = Modules.stripComments(Modules.read(file));
            Matcher matcher = Modules.LITERAL.matcher(source);
            while (matcher.find()) {
                if (JSON_OBJECT_LITERAL.matcher(matcher.group()).find()) {
                    violations.append(Modules.location(file, source, matcher.start()))
                            .append('\n');
                }
            }
        }
        assertEquals("Hand-written JSON object literals (use ToolSchemaBuilder or org.json):\n"
                + violations, 0, violations.length());
    }

    /** No CJK anywhere in main sources: literals, comments and identifiers included. */
    @Test
    public void noCjkLiteralsInJavaSources() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            String source = Modules.read(file);
            for (int i = 0; i < source.length(); i++) {
                if (looksCjk(String.valueOf(source.charAt(i)))) {
                    violations.append(Modules.location(file, source, i)).append(' ')
                            .append(source.charAt(i)).append('\n');
                    break; // one report per file is enough to locate the block
                }
            }
        }
        assertEquals("CJK is forbidden in main sources, comments included:\n" + violations,
                0, violations.length());
    }

    @Test
    public void statusLiteralsOnlyInsideEnums() {
        StringBuilder violations = new StringBuilder();
        for (File file : scannedFiles()) {
            String source = Modules.read(file);
            for (int offset : statusLiteralOffsets(source)) {
                violations.append(Modules.location(file, source, offset)).append('\n');
            }
        }
        assertEquals("Stringly-typed state:\n" + violations, 0, violations.length());
    }

    /** Feature/runtime code must use the scheduler, not hand-rolled lock concurrency. */
    @Test
    public void locksOnlyInSanctionedFiles() {
        Pattern pattern = Pattern.compile(
                "\\bsynchronized\\b|\\bReentrantLock\\b|\\bCountDownLatch\\b");
        List<String> sanctioned = List.of(
                // feature-chat: the hand-rolled ordered queues behind the event loop.
                "SerialEvents.java", "SerialWrites.java",
                // runtime shell infrastructure: task state shared with pump/killer threads.
                "MemoryTodoStore.java", "BackgroundShellRegistry.java", "LocalShellBackend.java",
                "SshShellBackend.java", "TerminalProviderGateway.java");
        StringBuilder violations = new StringBuilder();
        for (String root : Modules.modules()) {
            if (!root.startsWith("feature-") && !root.equals("runtime")) {
                continue;
            }
            for (File file : Modules.javaFiles(root)) {
                if (sanctioned.contains(file.getName())) {
                    continue;
                }
                String source = Modules.codeOnly(Modules.read(file));
                Matcher matcher = pattern.matcher(source);
                while (matcher.find()) {
                    violations.append(Modules.location(file, source, matcher.start()))
                            .append(' ').append(matcher.group()).append('\n');
                }
            }
        }
        assertEquals("Locks outside sanctioned files:\n" + violations, 0, violations.length());
    }

    /** AGENT.md budgets the :app shell at 1500 Java lines total (no page construction). */
    @Test
    public void appShellStaysWithinTotalBudget() {
        int total = 0;
        for (File file : Modules.sourcesOf("app")) {
            total += Modules.read(file).split("\n").length;
        }
        assertTrue("app shell over budget: " + total + " lines (max 1500)", total <= 1500);
    }

    static List<Integer> statusLiteralOffsets(String source) {
        String code = Modules.codeOnly(source);
        List<int[]> enums = new ArrayList<>();
        Matcher declarations = ENUM_BODY.matcher(code);
        while (declarations.find()) {
            int depth = 1;
            int end = declarations.end();
            while (end < code.length() && depth > 0) {
                char ch = code.charAt(end++);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                }
            }
            enums.add(new int[]{declarations.end(), end});
        }
        List<Integer> violations = new ArrayList<>();
        Matcher literals = Modules.LITERAL.matcher(Modules.stripComments(source));
        while (literals.find()) {
            if (!STATE_LITERAL.matcher(literals.group()).matches()) {
                continue;
            }
            boolean insideEnum = false;
            for (int[] range : enums) {
                if (literals.start() >= range[0] && literals.start() < range[1]) {
                    insideEnum = true;
                    break;
                }
            }
            if (!insideEnum) {
                violations.add(literals.start());
            }
        }
        return violations;
    }

    static boolean looksCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                return true;
            }
        }
        return false;
    }
}
