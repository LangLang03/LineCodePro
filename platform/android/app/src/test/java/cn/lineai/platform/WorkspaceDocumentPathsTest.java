package cn.lineai.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkspaceDocumentPathsTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void mapsNestedDocumentsAndChecksDescendants() throws Exception {
        File root = temporaryFolder.newFolder("workspace");
        File skills = new File(root, "skills/nested");
        assertTrue(skills.mkdirs());
        File file = new File(skills, "SKILL.md");
        assertTrue(file.createNewFile());

        WorkspaceDocumentPaths paths = new WorkspaceDocumentPaths(root);
        assertEquals(root.getCanonicalFile(), paths.resolve("workspace"));
        assertEquals(file.getCanonicalFile(),
                paths.resolve("workspace/skills/nested/SKILL.md"));
        assertEquals("workspace/skills/nested/SKILL.md", paths.documentIdFor(file));
        assertTrue(paths.isChild("workspace/skills", "workspace/skills/nested/SKILL.md"));
        assertFalse(paths.isChild("workspace/skills", "workspace/skills-other/file"));
    }

    @Test
    public void rejectsTraversalMalformedNamesAndSiblingPaths() throws Exception {
        File root = temporaryFolder.newFolder("workspace");
        File sibling = temporaryFolder.newFolder("workspace-other");
        WorkspaceDocumentPaths paths = new WorkspaceDocumentPaths(root);

        assertThrows(FileNotFoundException.class,
                () -> paths.resolve("workspace/../workspace-other"));
        assertThrows(FileNotFoundException.class,
                () -> paths.resolve("workspace/a//b"));
        assertThrows(FileNotFoundException.class,
                () -> paths.resolve("workspace/a\\b"));
        assertThrows(FileNotFoundException.class,
                () -> paths.resolve("other/file"));
        assertThrows(FileNotFoundException.class,
                () -> paths.documentIdFor(sibling));
        assertThrows(FileNotFoundException.class,
                () -> WorkspaceDocumentPaths.validateName("../escape"));
    }

    @Test
    public void rejectsSymlinksInsideAndOutsideWorkspace() throws Exception {
        File root = temporaryFolder.newFolder("workspace");
        File inside = temporaryFolder.newFolder(root.getName(), "inside");
        File outside = temporaryFolder.newFolder("outside");
        File outsideLink = new File(root, "outside-link");
        File insideLink = new File(root, "inside-link");
        Files.createSymbolicLink(outsideLink.toPath(), outside.toPath());
        Files.createSymbolicLink(insideLink.toPath(), inside.toPath());

        WorkspaceDocumentPaths paths = new WorkspaceDocumentPaths(root);
        assertThrows(FileNotFoundException.class,
                () -> paths.resolve("workspace/outside-link/secret.txt"));
        assertThrows(FileNotFoundException.class,
                () -> paths.resolve("workspace/inside-link/file.txt"));
        assertThrows(FileNotFoundException.class,
                () -> paths.documentIdFor(outsideLink));
    }
}
