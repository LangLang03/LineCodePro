package cn.lineai.tool.api;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PathPolicyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void resolvesNestedPaths() throws Exception {
        File root = temporary.newFolder("root");
        assertEquals(new File(root, "a/b.txt").getCanonicalFile(),
                PathPolicy.resolve(root, "a/b.txt"));
        assertEquals(root.getCanonicalFile(), PathPolicy.resolve(root, "."));
    }

    @Test public void rejectsParentTraversalAndAbsolutePaths() {
        File root = temporary.getRoot();
        try {
            PathPolicy.resolve(root, "../outside.txt");
            fail("expected rejection");
        } catch (SecurityException expected) {
            assertTrue(expected.getMessage().contains("escapes"));
        }
        try {
            PathPolicy.resolve(root, "/etc/passwd");
            fail("expected rejection");
        } catch (SecurityException expected) {
            assertTrue(expected.getMessage().contains("Absolute"));
        }
    }

    @Test public void bypassAllowsAbsoluteAndParentPaths() throws Exception {
        File root = temporary.newFolder("root");
        assertEquals(new File("/etc/passwd"), PathPolicy.resolve(root, "/etc/passwd", true));
        assertEquals(new File(root, "../outside.txt"),
                PathPolicy.resolve(root, "../outside.txt", true));
    }

    @Test public void containsChecksCanonicalPrefix() throws Exception {
        File root = temporary.newFolder("ws");
        File inside = new File(root, "x.txt");
        assertTrue(PathPolicy.contains(root, inside));
        assertFalse(PathPolicy.contains(root, new File(temporary.getRoot(), "elsewhere.txt")));
    }
}
