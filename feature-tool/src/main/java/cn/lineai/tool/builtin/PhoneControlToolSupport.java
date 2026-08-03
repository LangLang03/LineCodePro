package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.tool.R;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PhoneControlService;

/**
 * Shared helper for Phone control tools. Provides accessibility service lookup
 * and standardised "unavailable" result messages.
 *
 * <p>Decoupled from :app via {@link PhoneControlService} interface. The app module
 * calls {@link #inject} at startup to supply the real service instance and
 * accessibility-enabled check.
 */
public final class PhoneControlToolSupport {
    private static volatile PhoneControlService serviceInstance;
    private static volatile AccessibilityCheck accessibilityCheck;

    /** Functional interface for checking whether accessibility is enabled. */
    public interface AccessibilityCheck {
        boolean isAccessibilityEnabled(Context context);
    }

    private PhoneControlToolSupport() {
    }

    /**
     * Called by the :app module at startup to inject the real
     * {@link PhoneControlService} and accessibility state check.
     */
    public static void inject(PhoneControlService service, AccessibilityCheck check) {
        serviceInstance = service;
        accessibilityCheck = check;
    }

    /**
     * Update only the service instance (called when the accessibility service
     * connects or disconnects).
     */
    public static void setService(PhoneControlService service) {
        serviceInstance = service;
    }

    static PhoneControlService service(Context context) {
        PhoneControlService service = serviceInstance;
        if (service != null) {
            return service;
        }
        // Service not connected yet, but accessibility may be enabled — wait briefly.
        AccessibilityCheck check = accessibilityCheck;
        if (context != null && check != null && check.isAccessibilityEnabled(context)) {
            long deadline = System.currentTimeMillis() + 800;
            while (System.currentTimeMillis() < deadline) {
                service = serviceInstance;
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

    static ToolResult unavailable(BaseTool tool, Context context) {
        AccessibilityCheck check = accessibilityCheck;
        if (context != null && check != null && check.isAccessibilityEnabled(context)) {
            return toolError(tool, context.getString(R.string.phone_tool_accessibility_not_ready));
        }
        if (context != null) {
            return toolError(tool, context.getString(R.string.phone_tool_accessibility_disabled));
        }
        return toolError(tool, "Accessibility service is not enabled.");
    }

    private static ToolResult toolError(BaseTool tool, String message) {
        return ToolResult.withReview("", tool.getName(), message, true, "", "", "");
    }
}
