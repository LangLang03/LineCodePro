package cn.lineai.feature.chat.toolcall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure checks for the line diff used by the write/edit cards. */
public final class DiffLinesTest {

    @Test
    public void singleReplacementKeepsPrefixAndSuffix() {
        DiffLines diff = DiffLines.calculate("a\nb\nc\n", "a\nB\nc\n");
        assertEquals(1, diff.added);
        assertEquals(1, diff.removed);
        assertEquals(4, diff.lines.size());
        assertEquals(DiffLines.Line.UNCHANGED, diff.lines.get(0).kind);
        assertEquals(DiffLines.Line.REMOVED, diff.lines.get(1).kind);
        assertEquals("b", diff.lines.get(1).text);
        assertEquals(DiffLines.Line.ADDED, diff.lines.get(2).kind);
        assertEquals("B", diff.lines.get(2).text);
    }

    @Test
    public void newFileIsAllAdded() {
        DiffLines diff = DiffLines.calculate("", "one\ntwo\n");
        assertEquals(2, diff.added);
        assertEquals(0, diff.removed);
    }

    @Test
    public void identicalTextHasNoChanges() {
        DiffLines diff = DiffLines.calculate("same\n", "same\n");
        assertEquals(0, diff.added);
        assertEquals(0, diff.removed);
        assertEquals(1, diff.lines.size());
    }

    @Test
    public void missingTrailingNewlineIsReported() {
        DiffLines diff = DiffLines.calculate("", "no newline");
        assertTrue(!diff.lines.get(0).terminated);
        assertEquals("no newline", diff.lines.get(0).text);
    }
}
