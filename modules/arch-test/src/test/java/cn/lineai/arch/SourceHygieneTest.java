package cn.lineai.arch;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

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
            Pattern.compile("\"(pending|streaming|accepted|running|complete|failed|cancelled)\"");
    private static final Pattern ENUM_BODY = Pattern.compile("\\benum\\s+\\w+\\b[^;{]*\\{");

    @Test
    public void filesStayWithinLineBudget() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) {
                continue;
            }
            for (File file : Modules.javaFiles(module)) {
                int lines = Modules.read(file).split("\n").length;
                if (lines > MAX_FILE_LINES) {
                    violations.append(file.getAbsolutePath()).append(':').append(MAX_FILE_LINES + 1)
                            .append(" = ").append(lines).append('\n');
                }
            }
        }
        assertEquals("Files over budget:\n" + violations, 0, violations.length());
    }

    @Test
    public void noRawThreadsOrBlockingLoops() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test") || module.equals("platform-android")) {
                continue;
            }
            for (File file : Modules.javaFiles(module)) {
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
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) continue;
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.codeOnly(Modules.read(file));
                Matcher matcher = STATIC_MUTABLE.matcher(source);
                while (matcher.find()) {
                    violations.append(Modules.location(file, source, matcher.start()))
                            .append(' ').append(matcher.group(1)).append('\n');
                }
            }
        }
        assertEquals("Static mutable fields:\n" + violations, 0, violations.length());
    }

    @Test
    public void noEmptyCatchBlocks() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) continue;
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.codeOnly(Modules.read(file));
                Matcher matcher = EMPTY_CATCH.matcher(source);
                while (matcher.find()) {
                    violations.append(Modules.location(file, source, matcher.start())).append('\n');
                }
            }
        }
        assertEquals("Empty catch blocks:\n" + violations, 0, violations.length());
    }

    @Test
    public void noRawJsonState() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) continue;
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.codeOnly(Modules.read(file));
                Matcher matcher = RAW_JSON.matcher(source);
                while (matcher.find()) {
                    violations.append(Modules.location(file, source, matcher.start())).append('\n');
                }
            }
        }
        assertEquals("rawJson state leaked:\n" + violations, 0, violations.length());
    }

    @Test
    public void noHandWrittenJsonObjects() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) continue;
            for (File file : Modules.javaFiles(module)) {
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
        }
        assertEquals("Hand-written JSON object literals (use ToolSchemaBuilder or org.json):\n"
                + violations, 0, violations.length());
    }

    @Test
    public void noCjkLiteralsInJavaSources() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) {
                continue;
            }
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.stripComments(Modules.read(file));
                Matcher matcher = Modules.LITERAL.matcher(source);
                while (matcher.find()) {
                    if (looksCjk(matcher.group())) {
                        violations.append(Modules.location(file, source, matcher.start()))
                                .append(' ').append(matcher.group()).append('\n');
                    }
                }
            }
        }
        assertEquals("CJK literals:\n" + violations, 0, violations.length());
    }

    @Test
    public void statusLiteralsOnlyInsideEnums() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) {
                continue;
            }
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.read(file);
                for (int offset : statusLiteralOffsets(source)) {
                    violations.append(Modules.location(file, source, offset)).append('\n');
                }
            }
        }
        assertEquals("Stringly-typed state:\n" + violations, 0, violations.length());
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
