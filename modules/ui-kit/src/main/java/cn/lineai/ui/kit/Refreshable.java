package cn.lineai.ui.kit;

/**
 * Screens that reload their data whenever they become the visible top of the stack.
 *
 * <p>The navigation host calls {@link #onShown} on every back-stack return as well as on first
 * display, so implementations must treat it as a full refresh hint rather than a one-time
 * creation callback. Long-running work triggered here belongs on a background scheduler; the
 * method itself is always invoked on the main thread.</p>
 */
public interface Refreshable {
    /**
     * Invoked by the navigation host each time this screen becomes the visible top of the stack.
     */
    void onShown();
}
