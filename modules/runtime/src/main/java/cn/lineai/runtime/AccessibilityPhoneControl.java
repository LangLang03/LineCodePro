package cn.lineai.runtime;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;

import cn.lineai.platform.LineCodeAccessibilityService;
import cn.lineai.tool.api.PhoneControl;

/** Bridges the legacy accessibility service to the rewritten phone tools. */
public final class AccessibilityPhoneControl implements PhoneControl {

    private final Context context;

    /**
     * Creates the bridge over the application context.
     *
     * @param context any context; only the application context is retained
     */
    public AccessibilityPhoneControl(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Ready accessibility service, or {@code null} when not enabled; every call re-checks. */
    private LineCodeAccessibilityService service() {
        return LineCodeAccessibilityService.getReadyInstance(context);
    }

    /** Dispatches a tap gesture; {@code false} when the service is unavailable. */
    @Override public boolean click(int x, int y) {
        LineCodeAccessibilityService service = service();
        return service != null && service.click(x, y);
    }

    /** Dispatches a long-press gesture; {@code false} when the service is unavailable. */
    @Override public boolean longPress(int x, int y, int durationMs) {
        LineCodeAccessibilityService service = service();
        return service != null && service.longPress(x, y, durationMs);
    }

    /** Dispatches a swipe gesture; {@code false} when the service is unavailable. */
    @Override public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        LineCodeAccessibilityService service = service();
        return service != null && service.swipe(x1, y1, x2, y2, durationMs);
    }

    /** Serializes the accessibility node tree for the model; empty when unavailable. */
    @Override public String viewHierarchy() {
        LineCodeAccessibilityService service = service();
        return service == null ? "" : service.viewHierarchy();
    }

    /** Performs a named phone action (back, home, recents, notifications). */
    @Override public boolean action(String action) {
        LineCodeAccessibilityService service = service();
        return service != null && service.performPhoneAction(action);
    }

    /** Captures the screen as PNG bytes; {@code null} when capture fails or is unavailable. */
    @Override public byte[] screenshot() {
        LineCodeAccessibilityService service = service();
        if (service == null) return null;
        Bitmap bitmap = service.takeScreenshot();
        if (bitmap == null) return null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return out.toByteArray();
        } catch (Exception error) {
            return null;
        } finally {
            bitmap.recycle();
        }
    }
}
