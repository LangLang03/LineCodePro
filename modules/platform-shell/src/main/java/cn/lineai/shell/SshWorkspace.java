package cn.lineai.shell;

import java.util.Locale;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.Project;
import cn.lineai.core.model.SshConfig;
import cn.lineai.data.api.SettingsStore;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

/**
 * SSH workspace helpers.
 *
 * <p>The rewritten app keeps SSH projects imported from the legacy database (ids such as
 * {@code ssh:default}); their {@code path} is a directory on the remote host. Local file tools
 * must not run there, and {@code shell} is routed through {@link SshService}.</p>
 */
public final class SshWorkspace {

    private SshWorkspace() {
    }

    /**
     * Decides whether a project targets a remote SSH host, from its {@code ssh:} id prefix
     * or its {@code ssh://} path scheme.
     *
     * @param project the project to inspect; may be {@code null}
     * @return {@code true} when local file tools must not run against the project
     */
    public static boolean isSsh(Project project) {
        if (project == null) {
            return false;
        }
        String id = project.id.toLowerCase(Locale.ROOT);
        return id.startsWith("ssh:") || id.equals("ssh") || project.path.startsWith("ssh://");
    }

    /**
     * Extracts the remote working directory encoded in the project path; an empty result
     * means the SSH login directory.
     *
     * @param project the project to inspect; may be {@code null}
     * @return an absolute remote directory, or the empty string for the login directory
     */
    public static String directory(Project project) {
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

    /**
     * Executes one command on the remote host configured in the settings, on the calling
     * thread; callers must not invoke it from the main thread.
     *
     * @param context context used to locate the private files directory for known hosts
     * @param settings settings store holding the {@code ssh.*} credentials
     * @param command the command line to run remotely
     * @param timeoutMs timeout for the connection and the command execution
     * @return the captured standard output of the command
     * @throws cn.lineai.core.kernel.error.DomainException when SSH is not configured or the
     *         command fails (transport, remote error stream or non-zero exit)
     */
    public static String execute(android.content.Context context, SettingsStore settings,
                          String command, int timeoutMs) {
        SshConfig config = new SshPreferences(settings).config();
        if (!config.isConfigured()) {
            throw new DomainException(ErrorCode.INVALID_INPUT,
                    "SSH is not configured; finish the Termux/SSH setup first");
        }
        return new SshService(context.getFilesDir()).execute(config, command, timeoutMs);
    }

    /**
     * Builds a registry that keeps only tools usable against a remote host, so SSH projects
     * never see tools that require a local workspace.
     *
     * @param source the full tool registry
     * @return a new registry containing only tools that do not need a local workspace
     */
    public static ToolRegistry remoteOnly(ToolRegistry source) {
        ToolRegistry filtered = new ToolRegistry();
        for (Tool tool : source.all()) {
            if (!tool.needsLocalWorkspace()) {
                filtered.register(tool);
            }
        }
        return filtered;
    }
}
