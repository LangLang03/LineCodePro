package cn.lineai.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.FileTreeNode;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileTreeRepositoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void buildTreeSortsDirectoriesBeforeFilesAndExpandsSelectedPath() throws Exception {
        File root = temporaryFolder.newFolder("root");
        File src = new File(root, "src");
        File app = new File(root, "app");
        assertTrue(src.mkdirs());
        assertTrue(app.mkdirs());
        write(new File(root, "README.md"));
        write(new File(app, "MainActivity.java"));

        HashSet<String> expanded = new HashSet<>();
        expanded.add(app.getAbsolutePath());

        FileTreeNode tree = new FileTreeRepository().buildTree(root.getAbsolutePath(), expanded);

        assertTrue(tree.isDirectory());
        assertTrue(tree.isExpanded());
        assertEquals("app", tree.getChildren().get(0).getName());
        assertEquals("src", tree.getChildren().get(1).getName());
        assertEquals("README.md", tree.getChildren().get(2).getName());
        assertTrue(tree.getChildren().get(0).isExpanded());
        assertEquals("MainActivity.java", tree.getChildren().get(0).getChildren().get(0).getName());
    }

    @Test
    public void missingRootStillRendersAsEmptyDirectory() {
        File missing = new File(temporaryFolder.getRoot(), "missing");

        FileTreeNode tree = new FileTreeRepository().buildTree(missing.getAbsolutePath(), new HashSet<>());

        assertTrue(tree.isDirectory());
        assertTrue(tree.isExpanded());
        assertFalse(tree.getChildren().iterator().hasNext());
    }

    private static void write(File file) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(new byte[] {1});
        } finally {
            output.close();
        }
    }
}
