package cn.lineai.runtime;

import java.util.Locale;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.Project;
import cn.lineai.core.model.SshConfig;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.feature.terminal.SshPreferences;
import cn.lineai.feature.terminal.SshService;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

/**
 * SSH workspace helpers.
 *
 * <p>The rewritten app keeps SSH projects imported from the legacy database (ids such as
 * {@code ssh:default}); their {@code path} is a directory on the remote host. Local file tools
 * must not run there, and {@code shell_execute} is routed through {@link SshService}.</p>
 */
final class SshWorkspace {

    private SshWorkspace() {
    }

    static boolean isSsh(Project project) {
        if (project == null) {
            return false;
        }
        String id = project.id.toLowerCase(Locale.ROOT);
        return id.startsWith("ssh:") || id.equals("ssh") || project.path.startsWith("ssh://");
    }

    /** Remote working directory; empty means the SSH login directory. */
    static String directory(Project project) {
        if (project == null) {
            return "";
        }
        String path = project.path;
        if (path.startsWith("ssh://")) {
            int slash = path.indexOf('/', "ssh://".length());
            return slash < 0 ? "" : path.substring(slash);
        }
        return path.startsWith("/") ? path : "";
    }

    static String execute(android.content.Context context, SettingsStore settings,
                          String command, int timeoutMs) {
        SshConfig config = new SshPreferences(settings).config();
        if (!config.isConfigured()) {
            throw new DomainException(ErrorCode.INVALID_INPUT,
                    "SSH is not configured; finish the Termux/SSH setup first");
        }
        return new SshService(context.getFilesDir()).execute(config, command, timeoutMs);
    }

    /** Keeps only tools that can work against a remote host. */
    static ToolRegistry remoteOnly(ToolRegistry source) {
        ToolRegistry filtered = new ToolRegistry();
        for (Tool tool : source.all()) {
            if (!tool.needsLocalWorkspace()) {
                filtered.register(tool);
            }
        }
        return filtered;
    }
}
