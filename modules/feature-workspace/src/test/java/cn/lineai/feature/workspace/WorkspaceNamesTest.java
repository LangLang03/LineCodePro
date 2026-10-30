package cn.lineai.feature.workspace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkspaceNamesTest {

    @Test public void keepsNonLatinNamesReadable() {
        assertEquals("我的项目", WorkspaceNames.folder("我的项目"));
        assertEquals("проект", WorkspaceNames.folder("проект"));
    }

    @Test public void replacesOnlyIllegalPathCharacters() {
        assertEquals("A-B-C", WorkspaceNames.folder("A/B:C"));
        assertEquals("my-project", WorkspaceNames.folder("my project"));
        assertEquals("a-b", WorkspaceNames.folder("a\\b"));
    }

    @Test public void rejectsNamesThatCannotBecomeDirectories() {
        assertEquals("", WorkspaceNames.folder("   "));
        assertEquals("", WorkspaceNames.folder(".."));
        assertEquals("", WorkspaceNames.folder("."));
    }

    @Test public void truncatesLongNames() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 80; index++) name.append('a');
        assertEquals(60, WorkspaceNames.folder(name.toString()).length());
    }
}
