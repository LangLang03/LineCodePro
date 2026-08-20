package cn.lineai.ui.component;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WorkingStatusViewTest {
    @Test
    public void thinkingStateOnlyAppliesBeforeResponseTextStarts() {
        assertTrue(WorkingStatusView.isThinking("**Evaluating approach**", ""));
        assertTrue(WorkingStatusView.isThinking("summary", null));
        assertTrue(!WorkingStatusView.isThinking("summary", "Final answer"));
        assertTrue(!WorkingStatusView.isThinking("", ""));
        assertTrue(!WorkingStatusView.isThinking(null, null));
    }

    @Test
    public void highlightColorRemainsVisibleOnLightThemes() {
        int base = 0xff505050;
        int highlight = WorkingStatusView.highlightColor(base);

        assertTrue(((highlight >> 16) & 0xff) > ((base >> 16) & 0xff));
        assertTrue(((highlight >> 8) & 0xff) > ((base >> 8) & 0xff));
        assertTrue((highlight & 0xff) > (base & 0xff));
    }
}
