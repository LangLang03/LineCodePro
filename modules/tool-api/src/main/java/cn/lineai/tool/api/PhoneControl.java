package cn.lineai.tool.api;

/**
 * Accessibility-backed phone actions used by the phone tools.
 *
 * <p>Implementations bridge to the on-device accessibility service; every action returns a
 * success flag instead of throwing, because a dismissed dialog or a lost window is an expected
 * outcome, not an exceptional one.</p>
 */
public interface PhoneControl {

    /**
     * Taps once at the given screen coordinates.
     *
     * @param x horizontal coordinate in screen pixels
     * @param y vertical coordinate in screen pixels
     * @return {@code true} when the gesture was dispatched successfully
     */
    boolean click(int x, int y);

    /**
     * Presses and holds at the given screen coordinates.
     *
     * @param x horizontal coordinate in screen pixels
     * @param y vertical coordinate in screen pixels
     * @param durationMs how long the touch is held before release
     * @return {@code true} when the gesture was dispatched successfully
     */
    boolean longPress(int x, int y, int durationMs);

    /**
     * Drags from one point to another, e.g. to scroll or to swipe a list.
     *
     * @param x1 start horizontal coordinate in screen pixels
     * @param y1 start vertical coordinate in screen pixels
     * @param x2 end horizontal coordinate in screen pixels
     * @param y2 end vertical coordinate in screen pixels
     * @param durationMs how long the gesture takes from start to end
     * @return {@code true} when the gesture was dispatched successfully
     */
    boolean swipe(int x1, int y1, int x2, int y2, int durationMs);

    /**
     * Reads the accessibility view hierarchy of the current screen.
     *
     * @return a textual rendering of the active window hierarchy
     */
    String viewHierarchy();

    /**
     * Performs a global accessibility action such as back, home or recents.
     *
     * @param action canonical action name understood by the accessibility bridge
     * @return {@code true} when the action was dispatched successfully
     */
    boolean action(String action);

    /**
     * Captures the current screen.
     *
     * @return encoded image bytes, or {@code null} when capture is not available
     */
    byte[] screenshot();
}
