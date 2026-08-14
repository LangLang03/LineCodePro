package cn.lineai.service;

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
 * See {@code cn.lineai.tool.builtin.PhoneClickTool} for full module split barrier notes.
 */
public final class LineCodeAccessibilityService extends AccessibilityService
        implements cn.lineai.tool.PhoneControlService {

    private static volatile LineCodeAccessibilityService instance;

    private final Executor screenshotExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        cn.lineai.tool.builtin.PhoneControlToolSupport.setService(this);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        cn.lineai.tool.builtin.PhoneControlToolSupport.setService(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
            cn.lineai.tool.builtin.PhoneControlToolSupport.setService(null);
        }
    }

    public static LineCodeAccessibilityService getInstance() {
        return instance;
    }

    public static LineCodeAccessibilityService getReadyInstance(Context context) {
        LineCodeAccessibilityService service = instance;
        if (service != null) {
            return service;
        }
        if (context != null && isServiceEnabled(context)) {
            long deadline = System.currentTimeMillis() + 800;
            while (System.currentTimeMillis() < deadline) {
                service = instance;
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

    public boolean click(int x, int y) {
        return dispatchGesture(tapGesture(x, y, 1), null, null);
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        return dispatchGesture(swipeGesture(x1, y1, x2, y2, Math.max(100, durationMs)), null, null);
    }

    public boolean longPress(int x, int y, int durationMs) {
        return dispatchGesture(tapGesture(x, y, Math.max(400, durationMs)), null, null);
    }

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

    public boolean clickByCoordinates(int x, int y) {
        return click(x, y);
    }

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

    private GestureDescription tapGesture(int x, int y, int durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(1, durationMs));
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    private GestureDescription swipeGesture(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, durationMs);
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

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
