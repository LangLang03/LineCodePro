package cn.lineai.runtime;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;

import cn.lineai.platform.LineCodeAccessibilityService;
import cn.lineai.tool.api.PhoneControl;

/** Bridges the legacy accessibility service to the rewritten phone tools. */
public final class AccessibilityPhoneControl implements PhoneControl {

    private final Context context;

    public AccessibilityPhoneControl(Context context) {
        this.context = context.getApplicationContext();
    }

    private LineCodeAccessibilityService service() {
        return LineCodeAccessibilityService.getReadyInstance(context);
    }

    @Override public boolean click(int x, int y) {
        LineCodeAccessibilityService service = service();
        return service != null && service.click(x, y);
    }

    @Override public boolean longPress(int x, int y, int durationMs) {
        LineCodeAccessibilityService service = service();
        return service != null && service.longPress(x, y, durationMs);
    }

    @Override public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        LineCodeAccessibilityService service = service();
        return service != null && service.swipe(x1, y1, x2, y2, durationMs);
    }

    @Override public String viewHierarchy() {
        LineCodeAccessibilityService service = service();
        return service == null ? "" : service.viewHierarchy();
    }

    @Override public boolean action(String action) {
        LineCodeAccessibilityService service = service();
        return service != null && service.performPhoneAction(action);
    }

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
