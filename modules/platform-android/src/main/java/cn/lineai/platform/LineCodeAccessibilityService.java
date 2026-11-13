package cn.lineai.platform;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Accessibility service for phone control tools (click, swipe, screenshot, etc.).
 *
 * <p>This class has no dependency on :app internal classes (only Android SDK).
 * It can be extracted into :feature-phone once the tool framework is extracted
 * into a shared module, since Phone*Tool classes depend on this service.
 * See {@code cn.lineai.tool.builtin.PhoneClickTool} for full module split barrier notes.</p>
 *
 * <p>Ownership contract: the platform module owns the service lifecycle and the static instance
 * registry, while tool classes only consume the instance. The registry is published in
 * {@link #onCreate} and {@link #onServiceConnected}, cleared in {@link #onDestroy} through a
 * compare-and-set so a later service instance is never nulled by an earlier teardown, and consumed
 * through {@link #getInstance} or the bounded wait in {@link #getReadyInstance}. Gesture methods
 * must be called after the service is connected; callers that cannot guarantee that use the
 * ready-instance lookup instead of holding a stale reference.</p>
 */
public final class LineCodeAccessibilityService extends AccessibilityService {

    private static final java.util.concurrent.atomic.AtomicReference<LineCodeAccessibilityService>
            INSTANCE = new java.util.concurrent.atomic.AtomicReference<>();

    private final Executor screenshotExecutor = Executors.newSingleThreadExecutor();

    /** Publishes the instance as early as possible so tools can find it before connection. */
    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE.set(this);
    }

    /** Republishes the instance once the system has connected the service. */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE.set(this);
    }

    /** Intentionally unused; the service dispatches gestures on demand instead of observing events. */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    /** Intentionally unused; interrupted feedback requires no action here. */
    @Override
    public void onInterrupt() {
    }

    /** Clears the registry only when this instance is still the registered one. */
    @Override
    public void onDestroy() {
        super.onDestroy();
        INSTANCE.compareAndSet(this, null);
    }

    /**
     * Returns the current service instance without waiting.
     *
     * @return the connected instance, or null when the service is not running
     */
    public static LineCodeAccessibilityService getInstance() {
        return INSTANCE.get();
    }

    /**
     * Returns the service instance, waiting briefly for the system to connect it after the user has
     * just enabled it.
     *
     * @param context context used to check the enabled-services list; may be null, which disables
     *     the wait
     * @return the connected instance, or null when it is absent after at most 800 ms
     */
    public static LineCodeAccessibilityService getReadyInstance(Context context) {
        LineCodeAccessibilityService service = INSTANCE.get();
        if (service != null) {
            return service;
        }
        if (context != null && isServiceEnabled(context)) {
            long deadline = System.currentTimeMillis() + 800;
            while (System.currentTimeMillis() < deadline) {
                service = INSTANCE.get();
                if (service != null) {
                    return service;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Checks the accessibility manager's enabled list for this service class, independent of the
     * static instance (which may briefly lag the user toggle).
     *
     * @param context context used to reach the accessibility manager; may be null
     * @return true when the system currently reports this service as enabled
     */
    public static boolean isServiceEnabled(Context context) {
        if (context == null) {
            return false;
        }
        AccessibilityManager manager = (AccessibilityManager) context.getApplicationContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (services == null) {
            return false;
        }
        String target = LineCodeAccessibilityService.class.getName();
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null
                    && info.getResolveInfo().serviceInfo != null
                    && target.equals(info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Taps at screen coordinates.
     *
     * @param x horizontal coordinate in screen pixels
     * @param y vertical coordinate in screen pixels
     * @return true when the system accepted the gesture; acceptance does not guarantee completion
     */
    public boolean click(int x, int y) {
        return dispatchGesture(tapGesture(x, y, 1), null, null);
    }

    /**
     * Drags from one point to another.
     *
     * @param x1 start horizontal coordinate in screen pixels
     * @param y1 start vertical coordinate in screen pixels
     * @param x2 end horizontal coordinate in screen pixels
     * @param y2 end vertical coordinate in screen pixels
     * @param durationMs gesture duration in milliseconds; values below 100 are clamped up
     * @return true when the system accepted the gesture
     */
    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        return dispatchGesture(swipeGesture(x1, y1, x2, y2, Math.max(100, durationMs)), null, null);
    }

    /**
     * Long-presses at screen coordinates.
     *
     * @param x horizontal coordinate in screen pixels
     * @param y vertical coordinate in screen pixels
     * @param durationMs press duration in milliseconds; values below 400 are clamped up so the
     *     gesture is always recognised as a long press
     * @return true when the system accepted the gesture
     */
    public boolean longPress(int x, int y, int durationMs) {
        return dispatchGesture(tapGesture(x, y, Math.max(400, durationMs)), null, null);
    }

    /**
     * Dumps the active window's node tree as indented text with class, id, text, description and
     * bounds per node.
     *
     * @return the hierarchy dump, or a diagnostic line when no window is active
     */
    public String viewHierarchy() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "(no active window)";
        }
        StringBuilder builder = new StringBuilder();
        dumpNode(root, builder, 0);
        root.recycle();
        return builder.toString();
    }

    /**
     * Clicks the first clickable node matching a view id.
     *
     * @param resourceId resource id, either bare ({@code my_button}) or fully qualified
     *     ({@code pkg:id/my_button}); bare ids resolve against this app's package
     * @return true when a clickable matching node accepted {@code ACTION_CLICK}
     */
    public boolean clickById(String resourceId) {
        if (resourceId == null || resourceId.length() == 0) {
            return false;
        }
        String fullId = resourceId.contains(":/") ? resourceId : (getPackageName() + ":id/" + resourceId);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(fullId);
        boolean clicked = clickFirstClickableNode(nodes);
        root.recycle();
        return clicked;
    }

    /**
     * Clicks the first clickable node whose visible text matches.
     *
     * @param text text to search for; empty or null is rejected
     * @return true when a clickable matching node accepted {@code ACTION_CLICK}
     */
    public boolean clickByText(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        boolean clicked = clickFirstClickableNode(nodes);
        root.recycle();
        return clicked;
    }

    /**
     * Coordinate-based click alias kept for tool-call symmetry with {@link #clickById}.
     *
     * @param x horizontal coordinate in screen pixels
     * @param y vertical coordinate in screen pixels
     * @return true when the system accepted the gesture
     */
    public boolean clickByCoordinates(int x, int y) {
        return click(x, y);
    }

    /**
     * Performs a named global action.
     *
     * @param action one of {@code back}, {@code home}, {@code exit_app}, {@code recents},
     *     {@code notifications}, {@code quick_settings}, {@code power_dialog} or {@code lock_screen}
     * @return true when the action was recognised and performed; {@code lock_screen} additionally
     *     requires API 28
     */
    public boolean performPhoneAction(String action) {
        if ("back".equals(action)) {
            return performGlobalAction(GLOBAL_ACTION_BACK);
        }
        if ("home".equals(action) || "exit_app".equals(action)) {
            return performGlobalAction(GLOBAL_ACTION_HOME);
        }
        if ("recents".equals(action)) {
            return performGlobalAction(GLOBAL_ACTION_RECENTS);
        }
        if ("notifications".equals(action)) {
            return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
        if ("quick_settings".equals(action)) {
            return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        }
        if ("power_dialog".equals(action)) {
            return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
        if ("lock_screen".equals(action) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        return false;
    }

    /** Clicks the first clickable node in the list, recycling every node either way. */
    private boolean clickFirstClickableNode(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return false;
        }
        boolean clicked = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (!clicked && node.isClickable()
                    && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                clicked = true;
            }
            node.recycle();
        }
        return clicked;
    }

    /**
     * Captures the default display as a software bitmap.
     *
     * @return an ARGB_8888 copy of the screen, or null below API 30, when no display is available,
     *     on timeout (5 s), or on any capture failure
     */
    public Bitmap takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        try {
            DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display display = displayManager != null ? displayManager.getDisplay(Display.DEFAULT_DISPLAY) : null;
            if (display == null) {
                return null;
            }
            final Bitmap[] result = new Bitmap[1];
            final CountDownLatch latch = new CountDownLatch(1);
            takeScreenshot(display.getDisplayId(), screenshotExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        result[0] = bitmapFromScreenshotResult(screenshotResult);
                    } finally {
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    latch.countDown();
                }
            });
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return null;
            }
            return result[0];
        } catch (Exception e) {
            return null;
        }
    }

    /** Converts a hardware screenshot result into a software ARGB bitmap and releases the buffer. */
    @TargetApi(Build.VERSION_CODES.R)
    private Bitmap bitmapFromScreenshotResult(ScreenshotResult screenshotResult) {
        if (screenshotResult == null) {
            return null;
        }
        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
        if (buffer == null) {
            return null;
        }
        try {
            ColorSpace colorSpace = screenshotResult.getColorSpace();
            Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
            if (hardwareBitmap == null) {
                return null;
            }
            try {
                return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
                hardwareBitmap.recycle();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                buffer.close();
            }
        }
    }

    /** Builds a single-stroke tap gesture of the requested duration at one point. */
    private GestureDescription tapGesture(int x, int y, int durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(1, durationMs));
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    /** Builds a single-stroke line gesture between two points. */
    private GestureDescription swipeGesture(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, durationMs);
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    /** Recursively appends one node and its children to the dump, recycling children as consumed. */
    private void dumpNode(AccessibilityNodeInfo node, StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(node.getClassName() == null ? "View" : node.getClassName());
        if (node.getViewIdResourceName() != null) {
            builder.append(" id=").append(node.getViewIdResourceName());
        }
        if (node.getText() != null && node.getText().length() > 0) {
            builder.append(" text=").append(node.getText());
        }
        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
            builder.append(" desc=").append(node.getContentDescription());
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        builder.append(" bounds=").append(bounds.toShortString());
        builder.append("\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNode(child, builder, depth + 1);
                child.recycle();
            }
        }
    }
}
