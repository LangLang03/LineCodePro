package cn.lineai.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Pure-JVM tests for the public static decision methods of {@link BoundedScrollView}.
 * No android.* imports, no View instantiation, no MotionEvent: the drag decisions are
 * modelled as pure functions of (contentScrollable, deltaY, canScrollUp, canScrollDown).
 */
public final class BoundedScrollViewTest {

    @Test
    public void shouldHandleDrag() {
        // Non-scrollable content -> always false regardless of direction.
        assertFalse(BoundedScrollView.shouldHandleDrag(false, 10f, true, true));
        assertFalse(BoundedScrollView.shouldHandleDrag(false, -10f, true, true));
        assertFalse(BoundedScrollView.shouldHandleDrag(false, 0f, true, true));

        // At top + finger dragging DOWN (deltaY > 0, canScrollUp = false) -> false.
        assertFalse(BoundedScrollView.shouldHandleDrag(true, 10f, false, true));

        // At top + finger dragging UP (deltaY < 0, canScrollDown = true) -> true.
        assertTrue(BoundedScrollView.shouldHandleDrag(true, -10f, false, true));

        // At bottom + dragging UP (canScrollDown = false) -> false.
        assertFalse(BoundedScrollView.shouldHandleDrag(true, -10f, true, false));

        // At bottom + dragging DOWN (canScrollUp = true) -> true.
        assertTrue(BoundedScrollView.shouldHandleDrag(true, 10f, true, false));

        // deltaY == 0 with scrollable content -> true (keep ownership while at rest).
        assertTrue(BoundedScrollView.shouldHandleDrag(true, 0f, true, true));
        assertTrue(BoundedScrollView.shouldHandleDrag(true, 0f, false, false));
    }

    @Test
    public void canScrollContent() {
        // viewport 100 / child 200 -> scrollable.
        assertTrue(BoundedScrollView.canScrollContent(100, 200));

        // viewport 100 / child 100 -> not scrollable.
        assertFalse(BoundedScrollView.canScrollContent(100, 100));

        // viewport 0 / child 200 -> not scrollable.
        assertFalse(BoundedScrollView.canScrollContent(0, 200));

        // Negative or zero viewport -> never scrollable.
        assertFalse(BoundedScrollView.canScrollContent(-1, 200));
        assertFalse(BoundedScrollView.canScrollContent(0, 0));
    }

    @Test
    public void shouldDisallowOnDown() {
        // Each boolean parameter independently toggles the result (all 8 combinations).
        assertFalse(BoundedScrollView.shouldDisallowOnDown(false, false, false));
        assertFalse(BoundedScrollView.shouldDisallowOnDown(false, false, true));
        assertFalse(BoundedScrollView.shouldDisallowOnDown(false, true, false));
        assertFalse(BoundedScrollView.shouldDisallowOnDown(false, true, true));
        assertFalse(BoundedScrollView.shouldDisallowOnDown(true, false, false));
        assertFalse(BoundedScrollView.shouldDisallowOnDown(true, false, true));
        assertFalse(BoundedScrollView.shouldDisallowOnDown(true, true, false));
        assertTrue(BoundedScrollView.shouldDisallowOnDown(true, true, true));
    }

    @Test
    public void gestureSequence() {
        // Simulate a tracked-lastTouchY gesture chain:
        // DOWN (disallow true)
        //   -> MOVE down that scrolls inner (deltaY = 10, canScrollUp)  -> stays disallowed
        //   -> reach top (deltaY = 5, canScrollUp false)                -> release
        //   -> reverse direction (deltaY = -12, canScrollDown)          -> re-disallow
        // UP                                                             -> release
        // deltaY is always computed from consecutive lastTouchY values.
        List<Boolean> observed = new ArrayList<>();
        float lastTouchY = 100f;

        // DOWN.
        observed.add(BoundedScrollView.shouldDisallowOnDown(true, true, true));

        // MOVE down that scrolls inner: deltaY = nextY - lastTouchY = 10.
        float nextY = 110f;
        float deltaY = nextY - lastTouchY;
        assertEquals(10f, deltaY, 0f);
        observed.add(BoundedScrollView.shouldHandleDrag(true, deltaY, true, true));
        lastTouchY = nextY;

        // Reach top: still moving down, deltaY = 5, but no upward room left.
        nextY = 115f;
        deltaY = nextY - lastTouchY;
        assertEquals(5f, deltaY, 0f);
        observed.add(BoundedScrollView.shouldHandleDrag(true, deltaY, false, true));
        lastTouchY = nextY;

        // Reverse direction: MOVE up, deltaY = -12, downward room available.
        nextY = 103f;
        deltaY = nextY - lastTouchY;
        assertEquals(-12f, deltaY, 0f);
        observed.add(BoundedScrollView.shouldHandleDrag(true, deltaY, false, true));
        lastTouchY = nextY;

        // UP: release.
        observed.add(false);

        assertEquals(Arrays.asList(true, true, false, true, false), observed);
    }
}
