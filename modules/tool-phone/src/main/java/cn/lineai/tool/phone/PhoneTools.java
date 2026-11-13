package cn.lineai.tool.phone;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Supplier;

import cn.lineai.tool.api.PhoneControl;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolKind;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

/**
 * Accessibility-backed phone control tools.
 *
 * <p>All six tools share one {@link PhoneControl} supplier so the accessibility bridge can be
 * enabled or disabled at runtime; an unavailable control is reported as a failure result
 * rather than thrown. Every tool carries {@link ToolKind#PHONE} so mode policies can filter
 * the family as a unit.</p>
 */
public final class PhoneTools {

    /** Utility class; not instantiable. */
    private PhoneTools() {
    }

    /**
     * Copies the base registry and registers the phone tool family.
     *
     * @param base registry to extend; may be {@code null} to start empty
     * @param control supplier of the accessibility-backed control, evaluated per call
     * @return a new registry containing the base tools plus the phone tools
     */
    public static ToolRegistry augment(ToolRegistry base, Supplier<PhoneControl> control) {
        ToolRegistry registry = new ToolRegistry();
        if (base != null) {
            for (Tool tool : base.all())
                registry.register(tool);
        }
        registry.register(new Hierarchy(control))
                .register(new Click(control))
                .register(new LongPress(control))
                .register(new Swipe(control))
                .register(new GlobalAction(control))
                .register(new Screenshot(control));
        return registry;
    }

    /** Current control from the supplier, or {@code null} when none is available. */
    private static PhoneControl control(Supplier<PhoneControl> supplier) {
        return supplier == null ? null : supplier.get();
    }

    /** Standard failure used by every tool when the accessibility bridge is unavailable. */
    private static ToolResult missing() {
        return ToolResult.failure("", "Accessibility service is not enabled");
    }

    /** Phone-family card with the given title and action text. */
    private static ToolPresentation toolPresentation(String title, String action) {
        return new ToolPresentation(title, ToolIcon.PHONE, action, "");
    }

    /** Shared kind so phone tools can be toggled independently of file tools. */
    private static ToolKind phoneKind() {
        return ToolKind.PHONE;
    }

    /** Dumps the accessibility tree of the active screen. */
    private static final class Hierarchy implements Tool {
        private final Supplier<PhoneControl> supplier;

        /** Binds the tool to the shared control supplier. */
        Hierarchy(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        /** {@inheritDoc} */
        public String name() {
            return "phone_view_hierarchy";
        }

        /** {@inheritDoc} */
        public String description() {
            return "Dump the accessibility tree of the current screen.";
        }

        /** {@inheritDoc} */
        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object().build();
        }

        /** {@inheritDoc} */
        public boolean mutating() {
            return false;
        }

        /** {@inheritDoc} */
        public ToolKind kind() {
            return phoneKind();
        }

        /** {@inheritDoc} */
        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone hierarchy", "");
        }

        /**
         * {@inheritDoc}
         *
         * <p>An empty hierarchy means no active window and is reported as a failure.</p>
         */
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            String text = control.viewHierarchy();
            return text == null || text.isEmpty()
                    ? ToolResult.failure("", "No active window")
                    : ToolResult.success("", text);
        }
    }

    /** Taps at pixel coordinates on the current screen. */
    private static final class Click implements Tool {
        private final Supplier<PhoneControl> supplier;

        /** Binds the tool to the shared control supplier. */
        Click(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        /** {@inheritDoc} */
        public String name() {
            return "phone_click";
        }

        /** {@inheritDoc} */
        public String description() {
            return "Tap screen coordinates (pixels).";
        }

        /** {@inheritDoc} */
        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .number("x", "Screen X coordinate")
                    .number("y", "Screen Y coordinate")
                    .required("x").required("y").build();
        }

        /** {@inheritDoc} */
        public boolean mutating() {
            return true;
        }

        /** {@inheritDoc} */
        public ToolKind kind() {
            return phoneKind();
        }

        /** {@inheritDoc} */
        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone click", "x=" + arguments.optInt("x")
                    + " y=" + arguments.optInt("y"));
        }

        /** {@inheritDoc} */
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            return control.click(arguments.optInt("x"), arguments.optInt("y"))
                    ? ToolResult.success("", "Clicked")
                    : ToolResult.failure("", "Click failed");
        }
    }

    /** Presses and holds at pixel coordinates. */
    private static final class LongPress implements Tool {
        private final Supplier<PhoneControl> supplier;

        /** Binds the tool to the shared control supplier. */
        LongPress(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        /** {@inheritDoc} */
        public String name() {
            return "phone_long_press";
        }

        /** {@inheritDoc} */
        public String description() {
            return "Long-press screen coordinates (pixels).";
        }

        /** {@inheritDoc} */
        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .number("x", "Screen X coordinate")
                    .number("y", "Screen Y coordinate")
                    .number("duration_ms", "Press duration in milliseconds")
                    .required("x").required("y").build();
        }

        /** {@inheritDoc} */
        public boolean mutating() {
            return true;
        }

        /** {@inheritDoc} */
        public ToolKind kind() {
            return phoneKind();
        }

        /** {@inheritDoc} */
        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone long press", "x=" + arguments.optInt("x")
                    + " y=" + arguments.optInt("y"));
        }

        /** {@inheritDoc} */
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            int duration = arguments.optInt("duration_ms", 800);
            return control.longPress(arguments.optInt("x"), arguments.optInt("y"), duration)
                    ? ToolResult.success("", "Long-pressed")
                    : ToolResult.failure("", "Long press failed");
        }
    }

    /** Drags from one screen point to another, e.g. for scrolling. */
    private static final class Swipe implements Tool {
        private final Supplier<PhoneControl> supplier;

        /** Binds the tool to the shared control supplier. */
        Swipe(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        /** {@inheritDoc} */
        public String name() {
            return "phone_swipe";
        }

        /** {@inheritDoc} */
        public String description() {
            return "Swipe between two screen points.";
        }

        /** {@inheritDoc} */
        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .number("x1", "Start X")
                    .number("y1", "Start Y")
                    .number("x2", "End X")
                    .number("y2", "End Y")
                    .number("duration_ms", "Swipe duration in milliseconds")
                    .required("x1").required("y1").required("x2").required("y2").build();
        }

        /** {@inheritDoc} */
        public boolean mutating() {
            return true;
        }

        /** {@inheritDoc} */
        public ToolKind kind() {
            return phoneKind();
        }

        /** {@inheritDoc} */
        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone swipe", arguments.optInt("x1") + ","
                    + arguments.optInt("y1") + " → " + arguments.optInt("x2") + ","
                    + arguments.optInt("y2"));
        }

        /** {@inheritDoc} */
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            int duration = arguments.optInt("duration_ms", 350);
            return control.swipe(arguments.optInt("x1"), arguments.optInt("y1"),
                    arguments.optInt("x2"), arguments.optInt("y2"), duration)
                            ? ToolResult.success("", "Swiped")
                            : ToolResult.failure("", "Swipe failed");
        }
    }

    /** Runs one global accessibility action such as back or home. */
    private static final class GlobalAction implements Tool {
        private final Supplier<PhoneControl> supplier;

        /** Binds the tool to the shared control supplier. */
        GlobalAction(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        /** {@inheritDoc} */
        public String name() {
            return "phone_global_action";
        }

        /** {@inheritDoc} */
        public String description() {
            return "Run a global action: back, home, recents, notifications, "
                    + "quick_settings, power_dialog, lock_screen.";
        }

        /** {@inheritDoc} */
        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .enumString("action", "Global action", "back", "home", "recents",
                            "notifications", "quick_settings", "power_dialog", "lock_screen")
                    .required("action").build();
        }

        /** {@inheritDoc} */
        public boolean mutating() {
            return true;
        }

        /** {@inheritDoc} */
        public ToolKind kind() {
            return phoneKind();
        }

        /** {@inheritDoc} */
        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone action", arguments.optString("action", ""));
        }

        /** {@inheritDoc} */
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            String action = arguments.optString("action", "");
            return control.action(action)
                    ? ToolResult.success("", "Performed " + action)
                    : ToolResult.failure("", "Unknown or failed action: " + action);
        }
    }

    /** Captures the screen and stores the PNG under {@code screenshots/}. */
    private static final class Screenshot implements Tool {
        private final Supplier<PhoneControl> supplier;

        /** Binds the tool to the shared control supplier. */
        Screenshot(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        /** {@inheritDoc} */
        public String name() {
            return "phone_screenshot";
        }

        /** {@inheritDoc} */
        public String description() {
            return "Capture the screen and save a PNG in the workspace.";
        }

        /** {@inheritDoc} */
        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object().build();
        }

        /** {@inheritDoc} */
        public boolean mutating() {
            return true;
        }

        /** {@inheritDoc} */
        public ToolKind kind() {
            return phoneKind();
        }

        /** {@inheritDoc} */
        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone screenshot", "");
        }

        /**
         * {@inheritDoc}
         *
         * <p>Files are timestamped so repeated captures never collide; the result reports the
         * workspace-relative path the model can pass to read_file or image_understanding.</p>
         */
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            byte[] bytes = control.screenshot();
            if (bytes == null || bytes.length == 0) {
                return ToolResult.failure("", "Screenshot unavailable");
            }
            try {
                File dir = new File(context.workspaceRoot(), "screenshots");
                if (!dir.exists() && !dir.mkdirs()) {
                    return ToolResult.failure("", "Cannot create screenshots directory");
                }
                String name = "phone-" + System.currentTimeMillis() + ".png";
                Files.write(new File(dir, name).toPath(), bytes);
                return ToolResult.success("", "Screenshot saved to screenshots/" + name);
            } catch (IOException error) {
                return ToolResult.failure("", error.getMessage() == null
                        ? "Cannot save screenshot"
                        : error.getMessage());
            }
        }
    }
}
