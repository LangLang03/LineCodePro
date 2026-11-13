package cn.lineai.core.model;

/** SSH connection settings. */
public final class SshConfig {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8022;

    public final String host;
    public final int port;
    public final String username;
    public final String password;
    public final String privateKey;
    public final String passphrase;

    /**
     * Creates a configuration, applying defaults for blank host or non-positive port.
     * @param host remote host, blank falls back to {@link #DEFAULT_HOST}
     * @param port remote port, non-positive falls back to {@link #DEFAULT_PORT}
     * @param username login user
     * @param password password credential, may be blank
     * @param privateKey PEM private key, may be blank
     * @param passphrase private key passphrase, may be blank
     */
    public SshConfig(String host, int port, String username, String password, String privateKey,
                     String passphrase) {
        String normalizedHost = host == null ? "" : host.trim();
        this.host = normalizedHost.isEmpty() ? DEFAULT_HOST : normalizedHost;
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.privateKey = privateKey == null ? "" : privateKey;
        this.passphrase = passphrase == null ? "" : passphrase;
    }

    /** Returns the loopback configuration used before the user edits any field. */
    public static SshConfig defaultConfig() {
        return new SshConfig(DEFAULT_HOST, DEFAULT_PORT, "", "", "", "");
    }

    /** Returns whether host, port, user and at least one credential are present. */
    public boolean isConfigured() {
        return !host.isEmpty() && port > 0 && !username.isEmpty()
                && (!password.isEmpty() || !privateKey.trim().isEmpty());
    }
}
