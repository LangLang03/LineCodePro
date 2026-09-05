package cn.lineai.tool.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiffLinesTest {
    @Test public void creationDoesNotInventAnEmptyRemovedLine() {
        DiffLines diff = DiffLines.calculate("", "#!/bin/sh\necho ready\n");
        assertEquals(2, diff.added); assertEquals(0, diff.removed);
        assertEquals(1, diff.lines.get(0).number); assertEquals(2, diff.lines.get(1).number);
    }
    @Test public void replacementKeepsContextAndAccurateLineNumbers() {
        DiffLines diff = DiffLines.calculate("one\nold\nthree\n", "one\nnew\nextra\nthree\n");
        assertEquals(2, diff.added); assertEquals(1, diff.removed);
        assertEquals(-1, diff.lines.get(1).kind); assertEquals(2, diff.lines.get(1).number);
        assertEquals(1, diff.lines.get(2).kind); assertEquals(2, diff.lines.get(2).number);
        assertEquals(4, diff.lines.get(4).number);
    }
    @Test public void blankLinesAndDeletionRemainRealLines() {
        DiffLines blank = DiffLines.calculate("", "\n"); assertEquals(1, blank.added);
        DiffLines deleted = DiffLines.calculate("a\n\nb\n", ""); assertEquals(3, deleted.removed); assertEquals(0, deleted.added);
    }
    @Test public void unchangedFilesHaveNoChanges() {
        DiffLines diff = DiffLines.calculate("a\r\nb\r\n", "a\nb\n");
        assertEquals(0, diff.added); assertEquals(0, diff.removed);
    }
    @Test(timeout = 3000) public void largeUnrelatedFilesHaveBoundedMemoryAndReconstructCorrectly() {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < 8000; i++) { a.append("old ").append(i).append('\n'); b.append("new ").append(i).append('\n'); }
        DiffLines diff = DiffLines.calculate(a.toString(), b.toString());
        assertEquals(8000, diff.added); assertEquals(8000, diff.removed);
        StringBuilder result = new StringBuilder();
        for (DiffLines.Line line : diff.lines) if (line.kind >= 0) result.append(line.text).append('\n');
        assertEquals(b.toString(), result.toString());
    }
    @Test public void addingATerminatingNewlineIsARealChange() {
        DiffLines diff = DiffLines.calculate("same", "same\n");
        assertEquals(1, diff.added); assertEquals(1, diff.removed);
        assertFalse(diff.lines.get(0).terminated); assertTrue(diff.lines.get(1).terminated);
    }
    @Test public void anEditNearTheEndKeepsItsSourceLineNumber() {
        StringBuilder a = new StringBuilder(); for (int i = 0; i < 1000; i++) a.append("same\n");
        DiffLines diff = DiffLines.calculate(a + "old\n", a + "new\n");
        assertEquals(1, diff.added); assertEquals(1, diff.removed);
        assertEquals(1001, diff.lines.get(diff.lines.size() - 1).number);
    }
}
