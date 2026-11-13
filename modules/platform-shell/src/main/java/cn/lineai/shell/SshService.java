package cn.lineai.shell;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.SshConfig;

/**
 * Minimal JSch client: every command runs over a fresh session that is disconnected
 * afterwards. Host keys are pinned on first use in a private {@code known_hosts} file; a
 * regenerated remote host key is re-pinned once so the Termux setup flow can recover.
 */
public final class SshService {

    private final File knownHostsDirectory;

    /**
     * Creates a client whose pinned host keys live under the given directory.
     *
     * @param filesDirectory the application private files directory; the {@code ssh}
     *                       subdirectory holds {@code known_hosts}
     */
    public SshService(File filesDirectory) {
        this.knownHostsDirectory = new File(filesDirectory, "ssh");
    }

    /**
     * Runs a canned diagnostic command and returns its output, so callers can verify the
     * connection and show the remote user and directory.
     *
     * @param config the credentials to test
     * @return the captured standard output of the diagnostic command
     * @throws cn.lineai.core.kernel.error.DomainException on transport or authentication failure
     */
    public String testConnection(SshConfig config) {
        return execute(config, "echo LineAI SSH OK && whoami && pwd", 10_000);
    }

    /**
     * Executes one command over a fresh SSH session and returns its standard output.
     *
     * @param config the credentials to use; must be configured
     * @param command the command line to run on the remote host
     * @param timeoutMs timeout applied to the session, the channel and the execution
     * @return the captured standard output of the command
     * @throws cn.lineai.core.kernel.error.DomainException with {@code INVALID_INPUT} when the
     *         configuration is missing, {@code TOOL_FAILED} when the command wrote to stderr,
     *         or {@code NETWORK} for transport and authentication failures
     */
    public String execute(SshConfig config, String command, int timeoutMs) {
        if (config == null || !config.isConfigured()) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "ssh not configured");
        }
        Session session = null;
        ChannelExec channel = null;
        try {
            session = connect(config, timeoutMs);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(timeoutMs);
            String output = read(stdout);
            String error = read(stderr);
            if (!error.trim().isEmpty()) {
                throw new DomainException(ErrorCode.TOOL_FAILED, error.trim());
            }
            return output;
        } catch (DomainException error) {
            throw error;
        } catch (Exception error) {
            throw new DomainException(ErrorCode.NETWORK,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                    error);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Drops the remembered host keys.
     *
     * <p>The one-tap Termux setup may regenerate the sshd host key (reinstall, data restore),
     * and a stale pinned key then fails every connection with "host key has changed". The setup
     * flow clears this before its verification so the fresh key is trusted again.</p>
     */
    public void clearKnownHosts() {
        File file = new File(knownHostsDirectory, "known_hosts");
        if (file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Opens a session, re-pinning the host key once when the failure indicates the remote
     * key was regenerated; other failures propagate unchanged.
     */
    private Session connect(SshConfig config, int timeoutMs) throws Exception {
        try {
            return openSession(config, timeoutMs);
        } catch (Exception stale) {
            String detail = stale.getMessage() == null ? ""
                    : stale.getMessage().toLowerCase(java.util.Locale.ROOT);
            boolean hostKeyChanged = detail.contains("hostkey") || detail.contains("host key")
                    || detail.contains("has changed") || detail.contains("identification");
            if (!hostKeyChanged) {
                throw stale;
            }
            // Termux regenerated its sshd key: drop the pinned entry and trust it once more.
            clearKnownHosts();
            return openSession(config, timeoutMs);
        }
    }

    /** Configures and opens one JSch session with key or password authentication. */
    private Session openSession(SshConfig config, int timeoutMs) throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHosts().getAbsolutePath());
        if (!config.privateKey.trim().isEmpty()) {
            byte[] passphrase = config.passphrase.isEmpty() ? null
                    : config.passphrase.getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("linecode-key",
                    config.privateKey.getBytes(StandardCharsets.UTF_8), null, passphrase);
        }
        Session session = jsch.getSession(config.username, config.host, config.port);
        if (!config.password.isEmpty()) session.setPassword(config.password);
        Properties properties = new Properties();
        properties.put("StrictHostKeyChecking", "ask");
        properties.put("IdentitiesOnly", "yes");
        properties.put("PreferredAuthentications", !config.privateKey.trim().isEmpty()
                ? "publickey,password,keyboard-interactive"
                : "password,keyboard-interactive,publickey");
        session.setConfig(properties);
        session.setUserInfo(new TrustOnFirstUse());
        session.connect(timeoutMs);
        return session;
    }

    /** Ensures the ssh directory and the known_hosts file exist and returns the latter. */
    private File knownHosts() throws Exception {
        if (!knownHostsDirectory.exists() && !knownHostsDirectory.mkdirs()) {
            throw new DomainException(ErrorCode.STORAGE, "cannot create ssh directory");
        }
        File file = new File(knownHostsDirectory, "known_hosts");
        if (!file.exists() && !file.createNewFile()) {
            throw new DomainException(ErrorCode.STORAGE, "cannot create known_hosts");
        }
        return file;
    }

    /** Drains a stream fully into a UTF-8 string; an empty stream yields the empty string. */
    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Accepts unknown host keys (trust on first use) and rejects changed keys, so a
     * regenerated remote key surfaces as a connect failure instead of being silently
     * re-pinned from the interactive prompt path.
     */
    private static final class TrustOnFirstUse implements UserInfo {
        @Override public String getPassphrase() { return null; }
        @Override public String getPassword() { return null; }
        @Override public boolean promptPassword(String message) { return false; }
        @Override public boolean promptPassphrase(String message) { return false; }
        @Override public boolean promptYesNo(String message) {
            String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
            return !text.contains("has changed") && !text.contains("offending key");
        }
        @Override public void showMessage(String message) { }
    }
}
