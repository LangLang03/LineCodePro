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

    public static SshConfig defaultConfig() {
        return new SshConfig(DEFAULT_HOST, DEFAULT_PORT, "", "", "", "");
    }

    public boolean isConfigured() {
        return !host.isEmpty() && port > 0 && !username.isEmpty()
                && (!password.isEmpty() || !privateKey.trim().isEmpty());
    }
}
