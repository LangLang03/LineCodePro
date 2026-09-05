package cn.lineai.data.repository;

import cn.lineai.model.tool.ToolCall;
import cn.lineai.tool.ToolNames;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandPermissionRepositoryTest {
    private ToolCall call(String command, String cwd) {
        return new ToolCall("id", ToolNames.SHELL_EXECUTE, "{\"command\":\"" + command + "\",\"cwd\":\"" + cwd + "\"}");
    }
    @Test public void persistsAcrossRepositoryInstancesAndCanBeRevoked() {
        MemorySettings settings = new MemorySettings();
        ToolCall call = call("./gradlew assembleDebug", "/project");
        CommandPermissionRepository repository = new CommandPermissionRepository(settings);
        assertFalse(repository.isAllowed("ssh:a:22:u:/project", call));
        repository.allow("ssh:a:22:u:/project", call);
        CommandPermissionRepository reopened = new CommandPermissionRepository(settings);
        assertTrue(reopened.isAllowed("ssh:a:22:u:/project", call));
        reopened.clear(); assertFalse(repository.isAllowed("ssh:a:22:u:/project", call));
    }
    @Test public void scopeAndCommandChangesAlwaysRequireANewGrant() {
        CommandPermissionRepository repository = new CommandPermissionRepository(new MemorySettings());
        ToolCall granted = call("pwd", "/project"); repository.allow("ssh:host:22:alice:/project", granted);
        for (String scope : new String[]{"ssh:other:22:alice:/project", "ssh:host:23:alice:/project", "ssh:host:22:bob:/project",
                "ssh:host:22:alice:/other", "provider:host:22:alice:/project", ""}) assertFalse(repository.isAllowed(scope, granted));
        assertFalse(repository.isAllowed("ssh:host:22:alice:/project", call("pwd; whoami", "/project")));
        assertFalse(repository.isAllowed("ssh:host:22:alice:/project", call("pwd", "/other")));
        assertFalse(repository.isAllowed("ssh:host:22:alice:/project", call("pwd ", "/project")));
    }
    @Test public void newCallIdsAndTimeoutsDoNotChangeTheApprovedOperation() {
        CommandPermissionRepository repository = new CommandPermissionRepository(new MemorySettings());
        repository.allow("target/workspace", call("pwd", ""));
        assertTrue(repository.isAllowed("target/workspace", new ToolCall("new-id", ToolNames.SHELL_EXECUTE,
                "{\"timeoutMs\":10000,\"command\":\"pwd\"}")));
    }
    @Test public void invalidOrNonShellRequestsCannotCreateAGrant() {
        CommandPermissionRepository repository = new CommandPermissionRepository(new MemorySettings());
        for (ToolCall call : new ToolCall[]{new ToolCall("a", ToolNames.FILE_DELETE, "{\"command\":\"pwd\"}"),
                new ToolCall("b", ToolNames.SHELL_EXECUTE, "not json"), call("", ""), null}) {
            repository.allow("scope", call); assertFalse(repository.isAllowed("scope", call));
        }
    }
    @Test public void keyFramingCannotConfuseScopeAndCommand() {
        assertNotEquals(CommandPermissionRepository.grantKey("a|b", call("c", "")),
                CommandPermissionRepository.grantKey("a", call("b|c", "")));
    }
    static final class MemorySettings implements SettingsStore {
        private final Map<String, String> values = new HashMap<>();
        public String getString(String key, String fallback) { return values.getOrDefault(key, fallback); }
        public boolean getBoolean(String key, boolean fallback) { return Boolean.parseBoolean(getString(key, String.valueOf(fallback))); }
        public long getLong(String key, long fallback) { return Long.parseLong(getString(key, String.valueOf(fallback))); }
        public void setString(String key, String value) { values.put(key, value); }
        public void setBoolean(String key, boolean value) { setString(key, String.valueOf(value)); }
        public void setLong(String key, long value) { setString(key, String.valueOf(value)); }
        public void remove(String key) { values.remove(key); }
        public void clearLineCodeSettings() { values.clear(); }
        public Map<String, String> getLineCodeSettings() { return new HashMap<>(values); }
    }
}
