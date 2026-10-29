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

/** Accessibility-backed phone control tools. */
public final class PhoneTools {

    private PhoneTools() {
    }

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

    private static PhoneControl control(Supplier<PhoneControl> supplier) {
        return supplier == null ? null : supplier.get();
    }

    private static ToolResult missing() {
        return ToolResult.failure("", "Accessibility service is not enabled");
    }

    private static ToolPresentation toolPresentation(String title, String action) {
        return new ToolPresentation(title, ToolIcon.PHONE, action, "");
    }

    private static ToolKind phoneKind() {
        return ToolKind.PHONE;
    }

    private static final class Hierarchy implements Tool {
        private final Supplier<PhoneControl> supplier;

        Hierarchy(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        public String name() {
            return "phone_view_hierarchy";
        }

        public String description() {
            return "Dump the accessibility tree of the current screen.";
        }

        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object().build();
        }

        public boolean mutating() {
            return false;
        }

        public ToolKind kind() {
            return phoneKind();
        }

        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone hierarchy", "");
        }

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

    private static final class Click implements Tool {
        private final Supplier<PhoneControl> supplier;

        Click(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        public String name() {
            return "phone_click";
        }

        public String description() {
            return "Tap screen coordinates (pixels).";
        }

        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .number("x", "Screen X coordinate")
                    .number("y", "Screen Y coordinate")
                    .required("x").required("y").build();
        }

        public boolean mutating() {
            return true;
        }

        public ToolKind kind() {
            return phoneKind();
        }

        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone click", "x=" + arguments.optInt("x")
                    + " y=" + arguments.optInt("y"));
        }

        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PhoneControl control = control(supplier);
            if (control == null)
                return missing();
            return control.click(arguments.optInt("x"), arguments.optInt("y"))
                    ? ToolResult.success("", "Clicked")
                    : ToolResult.failure("", "Click failed");
        }
    }

    private static final class LongPress implements Tool {
        private final Supplier<PhoneControl> supplier;

        LongPress(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        public String name() {
            return "phone_long_press";
        }

        public String description() {
            return "Long-press screen coordinates (pixels).";
        }

        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .number("x", "Screen X coordinate")
                    .number("y", "Screen Y coordinate")
                    .number("duration_ms", "Press duration in milliseconds")
                    .required("x").required("y").build();
        }

        public boolean mutating() {
            return true;
        }

        public ToolKind kind() {
            return phoneKind();
        }

        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone long press", "x=" + arguments.optInt("x")
                    + " y=" + arguments.optInt("y"));
        }

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

    private static final class Swipe implements Tool {
        private final Supplier<PhoneControl> supplier;

        Swipe(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        public String name() {
            return "phone_swipe";
        }

        public String description() {
            return "Swipe between two screen points.";
        }

        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .number("x1", "Start X")
                    .number("y1", "Start Y")
                    .number("x2", "End X")
                    .number("y2", "End Y")
                    .number("duration_ms", "Swipe duration in milliseconds")
                    .required("x1").required("y1").required("x2").required("y2").build();
        }

        public boolean mutating() {
            return true;
        }

        public ToolKind kind() {
            return phoneKind();
        }

        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone swipe", arguments.optInt("x1") + ","
                    + arguments.optInt("y1") + " → " + arguments.optInt("x2") + ","
                    + arguments.optInt("y2"));
        }

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

    private static final class GlobalAction implements Tool {
        private final Supplier<PhoneControl> supplier;

        GlobalAction(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        public String name() {
            return "phone_global_action";
        }

        public String description() {
            return "Run a global action: back, home, recents, notifications, "
                    + "quick_settings, power_dialog, lock_screen.";
        }

        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object()
                    .enumString("action", "Global action", "back", "home", "recents",
                            "notifications", "quick_settings", "power_dialog", "lock_screen")
                    .required("action").build();
        }

        public boolean mutating() {
            return true;
        }

        public ToolKind kind() {
            return phoneKind();
        }

        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone action", arguments.optString("action", ""));
        }

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

    private static final class Screenshot implements Tool {
        private final Supplier<PhoneControl> supplier;

        Screenshot(Supplier<PhoneControl> supplier) {
            this.supplier = supplier;
        }

        public String name() {
            return "phone_screenshot";
        }

        public String description() {
            return "Capture the screen and save a PNG in the workspace.";
        }

        public String parametersJson() {
            return cn.lineai.tool.api.ToolSchemaBuilder.object().build();
        }

        public boolean mutating() {
            return true;
        }

        public ToolKind kind() {
            return phoneKind();
        }

        public ToolPresentation presentation(JSONObject arguments) {
            return toolPresentation("Phone screenshot", "");
        }

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
