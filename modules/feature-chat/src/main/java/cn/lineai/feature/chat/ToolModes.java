package cn.lineai.feature.chat;

import cn.lineai.core.model.ChatMode;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolKind;
import cn.lineai.tool.api.ToolRegistry;

/** Tool permission modes shared by the controller and the header sheet. */
public final class ToolModes {

    /** Settings key of the permission mode (auto/confirm/readonly). */
    public static final String KEY = "tools.permissionMode";
    /** Permission mode: run every tool without asking. */
    public static final String AUTO = "auto";
    /** Permission mode: ask before mutating tools (the default). */
    public static final String CONFIRM = "confirm";
    /** Permission mode: mutating tools are filtered out entirely. */
    public static final String READONLY = "readonly";
    /** Settings key of the chat mode (agent/plan/control). */
    public static final String CHAT_MODE_KEY = "chat.mode";

    /** Static utility; not instantiable. */
    private ToolModes() {
    }

    /** Current normalized chat mode; agent mode is the default. */
    public static String chatMode(SettingsStore settings) {
        return ChatMode.normalize(settings.get(CHAT_MODE_KEY, ChatMode.AGENT));
    }

    /** Persists a normalized chat mode. */
    public static void setChatMode(SettingsStore settings, String mode) {
        settings.put(CHAT_MODE_KEY, ChatMode.normalize(mode));
    }

    /**
     * Session-mode filter: control tools are injected only by the control mode.
     *
     * <p>Agent mode keeps every other family (including mutating workspace tools), chat/plan keep
     * read-only families; phone tools belong exclusively to control.</p>
     */
    public static ToolRegistry filterChatMode(String chatMode, ToolRegistry source) {
        String mode = ChatMode.normalize(chatMode);
        ToolRegistry result = new ToolRegistry();
        for (Tool tool : source.all()) {
            boolean phone = tool.kind() == ToolKind.PHONE;
            if (ChatMode.CONTROL.equals(mode)) {
                if (phone) result.register(tool);
            } else if (ChatMode.AGENT.equals(mode)) {
                if (!phone) result.register(tool);
            } else if (!tool.mutating() && !phone) {
                result.register(tool);
            }
        }
        return result;
    }

    /** Current normalized permission mode; confirm is the default. */
    public static String mode(SettingsStore settings) {
        return normalize(settings.get(KEY, CONFIRM));
    }

    /** Persists a normalized permission mode. */
    public static void setMode(SettingsStore settings, String mode) {
        settings.put(KEY, normalize(mode));
    }

    /** Maps unknown values to {@link #CONFIRM}; only auto/confirm/readonly are recognized. */
    public static String normalize(String mode) {
        if (AUTO.equals(mode) || READONLY.equals(mode)) return mode;
        return CONFIRM;
    }

    /** Read-only mode never exposes mutating tools, so they cannot even be requested. */
    public static ToolRegistry filter(String mode, ToolRegistry source) {
        if (!READONLY.equals(normalize(mode))) return source;
        ToolRegistry result = new ToolRegistry();
        for (Tool tool : source.all()) {
            if (!tool.mutating()) result.register(tool);
        }
        return result;
    }
}
