package cn.lineai.model;

import org.json.JSONObject;

public final class SshConfig {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8022;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKey;
    private final String passphrase;

    public SshConfig(String host, int port, String username, String password, String privateKey, String passphrase) {
        this.host = normalizeHost(host);
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.privateKey = privateKey == null ? "" : privateKey;
        this.passphrase = passphrase == null ? "" : passphrase;
    }

    public static SshConfig defaultConfig() {
        return new SshConfig(DEFAULT_HOST, DEFAULT_PORT, "", "", "", "");
    }

    public static SshConfig fromJson(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return defaultConfig();
        }
        try {
            JSONObject object = new JSONObject(raw);
            return new SshConfig(
                    object.optString("host", DEFAULT_HOST),
                    object.optInt("port", DEFAULT_PORT),
                    object.optString("username", ""),
                    object.optString("password", ""),
                    object.optString("privateKey", ""),
                    object.optString("passphrase", "")
            );
        } catch (Exception ignored) {
            return defaultConfig();
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public boolean isConfigured() {
        return host.length() > 0 && port > 0 && username.length() > 0
                && (password.length() > 0 || privateKey.length() > 0);
    }

    public String toJson() {
        try {
            return new JSONObject()
                    .put("host", host)
                    .put("port", port)
                    .put("username", username)
                    .put("password", password)
                    .put("privateKey", privateKey)
                    .put("passphrase", passphrase)
                    .toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String normalizeHost(String value) {
        String host = value == null ? "" : value.trim();
        return host.length() == 0 ? DEFAULT_HOST : host;
    }
}
