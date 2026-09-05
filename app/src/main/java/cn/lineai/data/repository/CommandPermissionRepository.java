package cn.lineai.data.repository;

import cn.lineai.model.tool.ToolCall;
import cn.lineai.tool.ToolNames;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact command grants; a grant never changes tool availability or read-only policy. */
public final class CommandPermissionRepository {
    private static final String KEY = "@linecode_command_grants_v1";
    private final SettingsStore settings;
    public CommandPermissionRepository(SettingsStore settings) { this.settings = settings; }

    public synchronized boolean isAllowed(String scope, ToolCall call) {
        String key = grantKey(scope, call);
        if (key.isEmpty()) return false;
        JSONArray grants = read();
        for (int i = 0; i < grants.length(); i++) if (key.equals(grants.optString(i))) return true;
        return false;
    }
    public synchronized void allow(String scope, ToolCall call) {
        String key = grantKey(scope, call);
        if (key.isEmpty() || isAllowed(scope, call)) return;
        JSONArray grants = read(), next = new JSONArray();
        // Keep the newest grants if a workspace has accumulated a large number of commands.
        for (int i = Math.max(0, grants.length() - 511); i < grants.length(); i++) next.put(grants.optString(i));
        next.put(key); settings.setString(KEY, next.toString());
    }
    public synchronized void clear() { settings.remove(KEY); }
    private JSONArray read() {
        try { return new JSONArray(settings.getString(KEY, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    public static String grantKey(String scope, ToolCall call) {
        if (scope == null || scope.isEmpty() || call == null || !ToolNames.SHELL_EXECUTE.equals(call.getName())) return "";
        try {
            JSONObject input = new JSONObject(call.getArguments());
            if (!(input.opt("command") instanceof String) || input.getString("command").trim().isEmpty()) return "";
            String command = input.getString("command");
            String cwd = input.optString("cwd", "").trim();
            // JSON framing prevents delimiter ambiguity; command bytes are not normalized.
            String value = new JSONArray().put(scope).put(call.getName()).put(command).put(cwd).toString();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : hash) key.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return key.toString();
        } catch (Exception ignored) { return ""; }
    }
}
