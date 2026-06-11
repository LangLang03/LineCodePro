package cn.lineai.mvp;

import cn.lineai.model.FileTreeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Assert;
import org.junit.Test;

public final class SshFileTreeControllerTest {
    @Test
    public void getFileTreeReturnsPlaceholderBeforeLoad() {
        Fixture fixture = new Fixture();

        FileTreeNode tree = fixture.controller.getFileTree();

        Assert.assertEquals("SSH", tree.getName());
        Assert.assertEquals(".", tree.getPath());
        Assert.assertTrue(tree.isExpanded());
    }

    @Test
    public void requestFileTreeLoadBuildsRootTreeAndCapturesRemoteRoot() {
        Fixture fixture = new Fixture();
        fixture.store.listings.put(".", directory("project", "/home/me/project",
                file("README.md", "/home/me/project/README.md")));

        fixture.controller.requestFileTreeLoad(false);
        FileTreeNode tree = fixture.controller.getFileTree();

        Assert.assertEquals("/home/me/project", fixture.host.projectPath);
        Assert.assertTrue(fixture.host.expandedPaths.contains("/home/me/project"));
        Assert.assertEquals("project", tree.getName());
        Assert.assertEquals("/home/me/project", tree.getPath());
        Assert.assertEquals(1, tree.getChildren().size());
        Assert.assertEquals("README.md", tree.getChildren().get(0).getName());
        Assert.assertEquals(1, fixture.host.renderCount);
    }

    @Test
    public void expandedPrefetchedDirectoryShowsCachedChildren() {
        Fixture fixture = new Fixture();
        fixture.host.projectPath = "/repo";
        fixture.host.expandedPaths.add("/repo");
        fixture.store.listings.put("/repo", directory("repo", "/repo",
                directory("src", "/repo/src")));
        fixture.store.listings.put("/repo/src", directory("src", "/repo/src",
                file("Main.java", "/repo/src/Main.java")));

        fixture.controller.requestFileTreeLoad(false);
        fixture.host.expandedPaths.add("/repo/src");
        fixture.controller.rebuildCachedTree();
        FileTreeNode tree = fixture.controller.getFileTree();
        FileTreeNode src = tree.getChildren().get(0);

        Assert.assertEquals("src", src.getName());
        Assert.assertTrue(src.isExpanded());
        Assert.assertEquals("Main.java", src.getChildren().get(0).getName());
    }

    @Test
    public void loadFailureAppearsUnderRoot() {
        Fixture fixture = new Fixture();
        fixture.host.projectPath = "/missing";
        fixture.store.failures.put("/missing", new IllegalStateException("not found"));

        fixture.controller.requestFileTreeLoad(false);
        FileTreeNode tree = fixture.controller.getFileTree();

        Assert.assertEquals("missing", tree.getName());
        Assert.assertEquals("not found", tree.getChildren().get(0).getName());
    }

    @Test
    public void refreshDirectoryAfterFileOperationReloadsExpandedDirectory() {
        Fixture fixture = new Fixture();
        fixture.host.projectPath = "/repo";
        fixture.host.expandedPaths.add("/repo");
        fixture.store.listings.put("/repo", directory("repo", "/repo",
                file("old.txt", "/repo/old.txt")));

        fixture.controller.requestFileTreeLoad(false);
        fixture.store.listings.put("/repo", directory("repo", "/repo",
                file("new.txt", "/repo/new.txt")));
        fixture.controller.refreshDirectoryAfterFileOperation("/repo");

        Assert.assertEquals("new.txt", fixture.controller.getFileTree().getChildren().get(0).getName());
    }

    private static final class Fixture {
        private final FakeDirectoryStore store = new FakeDirectoryStore();
        private final FakeHost host = new FakeHost();
        private final SshFileTreeController controller = new SshFileTreeController(
                store,
                host,
                (name, runnable) -> runnable.run(),
                Runnable::run
        );
    }

    private static final class FakeDirectoryStore implements SshFileTreeController.DirectoryStore {
        private final HashMap<String, FileTreeNode> listings = new HashMap<>();
        private final HashMap<String, Exception> failures = new HashMap<>();

        @Override
        public FileTreeNode listDirectory(String path) throws Exception {
            Exception failure = failures.get(path);
            if (failure != null) {
                throw failure;
            }
            FileTreeNode listing = listings.get(path);
            if (listing == null) {
                throw new IllegalStateException("missing listing: " + path);
            }
            return listing;
        }
    }

    private static final class FakeHost implements SshFileTreeController.Host {
        private boolean sshExecutionMode = true;
        private String projectPath = "";
        private String projectLabel = "LineCode";
        private int renderCount;
        private final HashSet<String> expandedPaths = new HashSet<>();

        @Override
        public boolean isSshExecutionMode() {
            return sshExecutionMode;
        }

        @Override
        public String projectPath() {
            return projectPath;
        }

        @Override
        public String projectLabel() {
            return projectLabel;
        }

        @Override
        public boolean isExpanded(String path) {
            return expandedPaths.contains(path);
        }

        @Override
        public void addExpandedPath(String path) {
            expandedPaths.add(path);
        }

        @Override
        public void setProjectPathFromSshRoot(String path) {
            projectPath = path;
        }

        @Override
        public String basename(String path) {
            String value = path == null ? "" : path;
            int slash = value.lastIndexOf('/');
            return slash >= 0 ? value.substring(slash + 1) : value;
        }

        @Override
        public void render() {
            renderCount++;
        }
    }

    private static FileTreeNode directory(String name, String path, FileTreeNode... children) {
        return new FileTreeNode(name, path, true, true,
                children == null ? Collections.emptyList() : new ArrayList<>(Arrays.asList(children)));
    }

    private static FileTreeNode file(String name, String path) {
        return new FileTreeNode(name, path, false, false, Collections.emptyList());
    }
}
