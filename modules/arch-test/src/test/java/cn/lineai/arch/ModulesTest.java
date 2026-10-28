package cn.lineai.arch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Guard against architecture checks passing without scanning the rewritten tree. */
public class ModulesTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder(
            new File(Modules.ROOT, "modules/arch-test/build"));

    @Test
    public void scansAllRewrittenModules() throws Exception {
        List<String> modules = Modules.modules();
        assertEquals("Rewritten modules, including arch-test", 30, modules.size());
        assertTrue(modules.contains("arch-test"));
        assertTrue(modules.containsAll(Modules.packageRoots().keySet()));
        int sources = 0;
        int implemented = 0;
        for (String module : modules) {
            if (!module.equals("arch-test")) {
                List<File> files = Modules.javaFiles(module);
                sources += files.size();
                if (!files.isEmpty()) {
                    implemented++;
                }
            }
        }
        assertTrue("No Java sources scanned", sources > 0);
        System.out.println("Architecture scan: cwd=" + new File(".").getCanonicalPath()
                + ", root=" + Modules.ROOT.getCanonicalPath() + ", modules=" + modules.size()
                + ", implemented=" + implemented + ", Java sources=" + sources);
    }

    @Test
    public void implementedCoreSourcesAreNonEmpty() {
        for (String module : new String[]{"core-model", "core-kernel", "core-mvi"}) {
            List<File> files = Modules.javaFiles(module);
            assertFalse("No implemented Java sources scanned in " + module, files.isEmpty());
            for (File file : files) {
                String source = Modules.read(file);
                assertFalse("Empty source: " + file, Modules.codeOnly(source).trim().isEmpty());
                assertTrue("Not a core source: " + file,
                        Modules.packageOf(source).startsWith(Modules.packageRoots().get(module)));
            }
            System.out.println(module + ": " + files.size() + " non-empty Java sources");
        }
    }

    @Test
    public void injectedRootIsCanonical() throws Exception {
        assertEquals(new File(System.getProperty(Modules.ROOT_PROPERTY)).getCanonicalFile(), Modules.ROOT);
        assertEquals(Modules.ROOT, Modules.validateRoot(Modules.ROOT.getPath()));
    }

    @Test
    public void missingBlankAndRelativeRootsFailFast() {
        for (String path : new String[]{null, "", "  ", ".."}) {
            assertThrows(IllegalStateException.class, () -> Modules.validateRoot(path));
        }
    }

    @Test
    public void oldModulesRootAndModuleWorkingDirectoryFailFast() {
        for (String path : new String[]{"modules", "modules/arch-test"}) {
            assertThrows(IllegalStateException.class,
                    () -> Modules.validateRoot(new File(Modules.ROOT, path).getAbsolutePath()));
        }
    }

    @Test
    public void nonexistentAndIncompleteRootsFailFast() throws Exception {
        File missing = new File(temporary.getRoot(), "missing");
        assertThrows(IllegalStateException.class, () -> Modules.validateRoot(missing.getAbsolutePath()));
        File empty = temporary.newFolder("empty");
        assertThrows(IllegalStateException.class, () -> Modules.validateRoot(empty.getAbsolutePath()));
        File incomplete = temporary.newFolder("incomplete");
        assertTrue(new File(incomplete, "settings.gradle.kts").createNewFile());
        assertTrue(new File(incomplete, "modules").mkdir());
        assertThrows(IllegalStateException.class, () -> Modules.validateRoot(incomplete.getAbsolutePath()));
    }

    @Test
    public void missingModuleAndSourceDirectoriesNeverReturnEmptyLists() throws Exception {
        assertThrows(IllegalStateException.class, () -> Modules.javaFiles("missing-module"));
        File missing = new File(temporary.getRoot(), "missing-java");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Modules.javaFilesIn(missing));
        assertTrue(error.getMessage().contains(missing.getAbsolutePath()));
        File regularFile = temporary.newFile("not-a-directory");
        assertThrows(IllegalStateException.class, () -> Modules.javaFilesIn(regularFile));
    }

    @Test
    public void existingEmptySourceDirectoryIsDistinctFromMissingDirectory() throws Exception {
        assertTrue(Modules.javaFilesIn(temporary.newFolder("java")).isEmpty());
    }

    @Test
    public void commentsAreMaskedWithoutChangingOffsetsOrLines() {
        String source = "/* multiline\r\n中文 \"failed\" */\nnew/* gap */Thread(); // inline \"running\"\n";
        String stripped = Modules.stripComments(source);
        assertEquals(source.length(), stripped.length());
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n' || source.charAt(i) == '\r') {
                assertEquals(source.charAt(i), stripped.charAt(i));
            }
        }
        assertFalse(stripped.contains("中文"));
        assertFalse(stripped.contains("failed"));
        assertFalse(stripped.contains("running"));
        Matcher thread = SourceHygieneTest.THREAD_PATTERN.matcher(stripped);
        assertTrue(thread.find());
        assertEquals(source.indexOf("new"), thread.start());
        assertTrue(Modules.location(new File(Modules.ROOT, "Example.java"), stripped, thread.start())
                .endsWith(":3"));
    }

    @Test
    public void literalsRetainUrlsCommentMarkersAndEscapedQuotes() {
        String source = "String url = \"https://host/中文/*literal*/\\\"//still literal\"; char q = '\\\'';";
        assertEquals(source, Modules.stripComments(source));
        Matcher literal = Modules.LITERAL.matcher(Modules.stripComments(source));
        assertTrue(literal.find());
        assertTrue(SourceHygieneTest.looksCjk(literal.group()));
        assertTrue(literal.group().contains("//still literal"));
        assertTrue(literal.find());
        assertEquals("'\\\''", literal.group());
        assertFalse(literal.find());
    }

    @Test
    public void codeChecksIgnoreStringsButFindQualifiedAndSeparatedConstructs() {
        String source = "String example = \"new Thread(); while(true)\"; // Executors.newPool()\n"
                + "new java.lang.Thread(); new android.os.Handler(); Executors /*gap*/ .newPool(); while (true) {}";
        Matcher matcher = SourceHygieneTest.THREAD_PATTERN.matcher(Modules.codeOnly(source));
        int matches = 0;
        while (matcher.find()) {
            matches++;
        }
        assertEquals(4, matches);
    }

    @Test
    public void packageDetectionIgnoresCommentsAndAcceptsWhitespace() {
        assertEquals("cn.lineai.core.model", Modules.packageOf(
                "/*\npackage fake;\n*/ package\tcn.lineai.core.model;"));
    }

    @Test
    public void unrelatedCodecCallsAndStatusClassNamesDoNotHideStateLiterals() {
        assertEquals(2, SourceHygieneTest.statusLiteralOffsets(
                "class Status { String a = name(); Object b = String.valueOf(1);"
                        + "String x = \"failed\"; String y = \"cancelled\"; }").size());
    }

    @Test
    public void onlyActualEnumBodiesMayContainStateLiterals() {
        String source = "class Model { enum Status { FAILED(\"failed\");"
                + "Status(String wire) { if (wire.isEmpty()) { throw new IllegalArgumentException(); } } }"
                + "String illegal = \"running\"; }";
        List<Integer> offsets = SourceHygieneTest.statusLiteralOffsets(source);
        assertEquals(1, offsets.size());
        assertEquals(source.indexOf("\"running\""), (int) offsets.get(0));
        assertTrue(SourceHygieneTest.statusLiteralOffsets(
                "// \"failed\"\n/* \"cancelled\" */ String text = \"escaped \\\"failed\\\"\";").isEmpty());
    }
}

