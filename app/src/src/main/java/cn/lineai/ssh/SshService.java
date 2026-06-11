package cn.lineai.ssh;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import cn.lineai.data.repository.SshConfigRepository;
import cn.lineai.model.SshConfig;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class SshService {
    public interface OutputListener {
        void onOutput(String output);
    }

    public interface SftpOperation<T> {
        T run(ChannelSftp sftp) throws Exception;
    }

    public static final String TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    public static final String TERMUX_ALLOW_EXTERNAL_APPS_COMMAND = "mkdir -p ~/.termux\n"
            + "properties_path=\"$HOME/.termux/termux.properties\"\n"
            + "touch \"$properties_path\"\n"
            + "grep -qxF 'allow-external-apps=true' \"$properties_path\" || printf '\\nallow-external-apps=true\\n' >> \"$properties_path\"\n"
            + "termux-reload-settings >/dev/null 2>&1 || true";

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String TERMUX_ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String TERMUX_EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String TERMUX_EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String TERMUX_EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN";
    private static final String TERMUX_EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    private static final String TERMUX_EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String TERMUX_EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER";
    private static final String TERMUX_EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    private static final String TERMUX_EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION";
    private static final String TERMUX_EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";
    private static final String TERMUX_RESULT_BUNDLE = "result";
    private static final String TERMUX_RESULT_STDOUT = "stdout";
    private static final String TERMUX_RESULT_STDERR = "stderr";
    private static final String TERMUX_RESULT_EXIT_CODE = "exitCode";
    private static final String TERMUX_RESULT_ERR = "err";
    private static final String TERMUX_RESULT_ERRMSG = "errmsg";
    @SuppressLint("SdCardPath")
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    @SuppressLint("SdCardPath")
    private static final String TERMUX_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "LINEAI_PRIVATE_KEY_BEGIN\\n([\\s\\S]*?)\\nLINEAI_PRIVATE_KEY_END"
    );
    private static final String TERMUX_SETUP_SCRIPT = joinLines(
            "set -eu",
            "export DEBIAN_FRONTEND=noninteractive",
            "mkdir -p \"$HOME/.termux\"",
            "properties_path=\"$HOME/.termux/termux.properties\"",
            "touch \"$properties_path\"",
            "grep -qxF 'allow-external-apps=true' \"$properties_path\" || printf '\\nallow-external-apps=true\\n' >> \"$properties_path\"",
            "termux-reload-settings >/dev/null 2>&1 || true",
            "",
            "if ! command -v pkg >/dev/null 2>&1; then",
            "  echo \"Termux pkg command not found\" >&2",
            "  exit 127",
            "fi",
            "pkg update -y",
            "pkg install -y openssh",
            "",
            "user_name=\"$(whoami 2>/dev/null || id -un 2>/dev/null || echo \"\")\"",
            "shell_path=\"${SHELL:-}\"",
            "if [ -z \"$shell_path\" ] && command -v getent >/dev/null 2>&1; then",
            "  shell_path=\"$(getent passwd \"$user_name\" | awk -F: '{print $7}' || true)\"",
            "fi",
            "if [ -z \"$shell_path\" ]; then",
            "  shell_path=\"${PREFIX:-/data/data/com.termux/files/usr}/bin/sh\"",
            "fi",
            "shell_name=\"$(basename \"$shell_path\")\"",
            "start_line='command -v sshd >/dev/null 2>&1 && { command -v pgrep >/dev/null 2>&1 && pgrep -x sshd >/dev/null 2>&1 || sshd >/dev/null 2>&1 || true; }'",
            "case \"$shell_name\" in",
            "  fish)",
            "    rc_path=\"$HOME/.config/fish/config.fish\"",
            "    mkdir -p \"$(dirname \"$rc_path\")\"",
            "    start_line='command -v sshd >/dev/null 2>&1; and begin; command -v pgrep >/dev/null 2>&1; and pgrep -x sshd >/dev/null 2>&1; or sshd >/dev/null 2>&1; or true; end'",
            "    ;;",
            "  zsh) rc_path=\"$HOME/.zshrc\" ;;",
            "  bash) rc_path=\"$HOME/.bashrc\" ;;",
            "  *) rc_path=\"$HOME/.profile\" ;;",
            "esac",
            "touch \"$rc_path\"",
            "if ! grep -Fq 'LineAI:sshd-autostart' \"$rc_path\"; then",
            "  printf '\\n# LineAI:sshd-autostart\\n%s\\n' \"$start_line\" >> \"$rc_path\"",
            "fi",
            "",
            "chmod 700 \"$HOME\" 2>/dev/null || true",
            "mkdir -p \"$HOME/.ssh\"",
            "chmod 700 \"$HOME/.ssh\"",
            "key_path=\"$HOME/.ssh/lineai_rsa\"",
            "if [ ! -f \"$key_path\" ] || ! ssh-keygen -y -f \"$key_path\" >/dev/null 2>&1; then",
            "  rm -f \"$key_path.pub\"",
            "  ssh-keygen -t rsa -b 4096 -m PEM -f \"$key_path\" -N \"\" -C \"lineai@termux\" >/dev/null",
            "fi",
            "chmod 600 \"$key_path\"",
            "if [ ! -f \"$key_path.pub\" ]; then",
            "  ssh-keygen -y -f \"$key_path\" > \"$key_path.pub\"",
            "fi",
            "auth_path=\"$HOME/.ssh/authorized_keys\"",
            "touch \"$auth_path\"",
            "pub_key=\"$(cat \"$key_path.pub\")\"",
            "grep -qxF \"$pub_key\" \"$auth_path\" || printf '%s\\n' \"$pub_key\" >> \"$auth_path\"",
            "chmod 600 \"$auth_path\"",
            "if command -v pgrep >/dev/null 2>&1; then",
            "  pgrep -x sshd >/dev/null 2>&1 || sshd >/dev/null 2>&1 || true",
            "else",
            "  sshd >/dev/null 2>&1 || true",
            "fi",
            "sleep 1",
            "",
            "printf 'LINEAI_TERMUX_USERNAME=%s\\n' \"$user_name\"",
            "printf 'LINEAI_TERMUX_SHELL=%s\\n' \"$shell_name\"",
            "printf 'LINEAI_TERMUX_RC=%s\\n' \"$rc_path\"",
            "printf 'LINEAI_TERMUX_HOST=127.0.0.1\\n'",
            "printf 'LINEAI_TERMUX_PORT=8022\\n'",
            "printf 'LINEAI_PRIVATE_KEY_BEGIN\\n'",
            "cat \"$key_path\"",
            "printf '\\nLINEAI_PRIVATE_KEY_END\\n'"
    );

    private final Context context;
    private final SshConfigRepository repository;

    public SshService(Context context) {
        this.context = context.getApplicationContext();
        repository = new SshConfigRepository(this.context);
    }

    public SshConfig getConfig() {
        return repository.get();
    }

    public void saveConfig(SshConfig config) {
        repository.save(config);
    }

    public String testConnection(SshConfig config) throws Exception {
        return executeCommand("echo LineAI SSH OK && whoami && pwd", 10000, config);
    }

    public String executeCommand(String command, int timeoutMs) throws Exception {
        return executeCommand(command, timeoutMs, repository.get());
    }

    public String executeCommand(String command, int timeoutMs, SshConfig config) throws Exception {
        return executeCommand(command, timeoutMs, config, null);
    }

    public String executeCommand(String command, int timeoutMs, SshConfig config, OutputListener listener) throws Exception {
        String safeCommand = command == null ? "" : command.trim();
        if (safeCommand.length() == 0) {
            throw new IllegalArgumentException("命令不能为空");
        }
        SshConfig safeConfig = config == null ? repository.get() : config;
        if (!safeConfig.isConfigured()) {
            throw new IllegalStateException("SSH 未配置完整，请填写 host、port、username，并填写 password 或 private key。");
        }
        int boundedTimeout = Math.max(1000, Math.min(timeoutMs <= 0 ? 30000 : timeoutMs, 300000));
        return executeInternal(safeCommand, boundedTimeout, safeConfig, listener);
    }

    public <T> T withSftp(SftpOperation<T> operation, int timeoutMs) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("SFTP 操作不能为空");
        }
        SshConfig config = repository.get();
        if (!config.isConfigured()) {
            throw new IllegalStateException("SSH 未配置完整，请填写 host、port、username，并填写 password 或 private key。");
        }
        int boundedTimeout = Math.max(1000, Math.min(timeoutMs <= 0 ? 30000 : timeoutMs, 300000));
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = createSession(config, boundedTimeout);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(boundedTimeout);
            return operation.run(channel);
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void openTermux() {
        ensureTermuxInstalled();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launchIntent == null) {
            launchIntent = new Intent().setClassName(TERMUX_PACKAGE, "com.termux.app.TermuxActivity");
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);
    }

    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public TermuxSetupResult setupTermuxOpenSsh(int timeoutMs) throws Exception {
        ensureTermuxInstalled();
        if (context.checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("LineCode 未获得 Termux RUN_COMMAND 权限，请在应用权限中允许“Run commands in Termux environment”。");
        }
        int boundedTimeout = Math.max(60000, Math.min(timeoutMs <= 0 ? 900000 : timeoutMs, 900000));
        String action = context.getPackageName() + ".TERMUX_SETUP_RESULT." + System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> outputRef = new AtomicReference<>("");
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receivedContext, Intent intent) {
                try {
                    android.os.Bundle result = intent == null ? null : intent.getBundleExtra(TERMUX_RESULT_BUNDLE);
                    if (result == null) {
                        errorRef.set(new IllegalStateException("Termux 未返回执行结果，请确认 Termux 版本 >= 0.109。"));
                        return;
                    }
                    String stdout = result.getString(TERMUX_RESULT_STDOUT, "");
                    String stderr = result.getString(TERMUX_RESULT_STDERR, "");
                    int exitCode = result.getInt(TERMUX_RESULT_EXIT_CODE, 0);
                    int err = result.getInt(TERMUX_RESULT_ERR, Activity.RESULT_OK);
                    String errmsg = result.getString(TERMUX_RESULT_ERRMSG, "");
                    String combined = combine(stdout, stderr, "");
                    if (err != Activity.RESULT_OK) {
                        errorRef.set(new IllegalStateException(errmsg.length() == 0 ? "Termux 启动命令失败: " + err : errmsg));
                    } else if (exitCode != 0) {
                        errorRef.set(new IllegalStateException("exit status " + exitCode + "\n" + combined));
                    } else {
                        outputRef.set(combined);
                    }
                } finally {
                    latch.countDown();
                }
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, new IntentFilter(action));
            }
            int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) (System.currentTimeMillis() & 0x7fffffff),
                    new Intent(action).setPackage(context.getPackageName()),
                    flags
            );
            Intent intent = new Intent()
                    .setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                    .setAction(TERMUX_ACTION_RUN_COMMAND)
                    .putExtra(TERMUX_EXTRA_COMMAND_PATH, TERMUX_SH)
                    .putExtra(TERMUX_EXTRA_ARGUMENTS, new String[] {"-s"})
                    .putExtra(TERMUX_EXTRA_STDIN, TERMUX_SETUP_SCRIPT)
                    .putExtra(TERMUX_EXTRA_WORKDIR, TERMUX_HOME)
                    .putExtra(TERMUX_EXTRA_BACKGROUND, true)
                    .putExtra(TERMUX_EXTRA_RUNNER, "app-shell")
                    .putExtra(TERMUX_EXTRA_COMMAND_LABEL, "LineAI OpenSSH setup")
                    .putExtra(TERMUX_EXTRA_COMMAND_DESCRIPTION, "Install openssh, create a LineAI key, enable authorized_keys, add sshd autostart to shell rc, and start sshd.")
                    .putExtra(TERMUX_EXTRA_PENDING_INTENT, pendingIntent);
            context.startService(intent);
            boolean completed = latch.await(boundedTimeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new IllegalStateException("等待 Termux 配置 OpenSSH 超时，请确认 Termux 已执行授权指令并开启 allow-external-apps=true。");
            }
            if (errorRef.get() != null) {
                throw errorRef.get();
            }
            TermuxSetupResult result = parseTermuxSetupOutput(outputRef.get());
            repository.save(result.getConfig());
            return result;
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }
    }

    private String executeInternal(String command, int timeoutMs, SshConfig config, OutputListener listener) throws Exception {
        ChannelExec channel = null;
        Session session = null;
        try {
            session = createSession(config, timeoutMs);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(timeoutMs);
            long startedAt = System.currentTimeMillis();
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - startedAt > timeoutMs) {
                    throw new IllegalStateException("命令执行超时");
                }
                boolean changed = drainAvailable(stdout, output) > 0;
                changed = drainAvailable(stderr, error) > 0 || changed;
                if (changed && listener != null) {
                    listener.onOutput(combineRaw(output.toString(), error.toString()));
                }
                Thread.sleep(100);
            }
            boolean changed = drainAvailable(stdout, output) > 0;
            changed = drainAvailable(stderr, error) > 0 || changed;
            if (changed && listener != null) {
                listener.onOutput(combineRaw(output.toString(), error.toString()));
            }
            int exitStatus = channel.getExitStatus();
            String combined = combine(output.toString(), error.toString(), "exit status: " + exitStatus);
            if (exitStatus == 0) {
                return combined;
            }
            throw new IllegalStateException("exit status " + exitStatus + "\n" + combined);
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Session createSession(SshConfig config, int timeoutMs) throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHostsFile().getAbsolutePath());
        if (config.getPrivateKey().trim().length() > 0) {
            byte[] passphrase = config.getPassphrase().length() > 0
                    ? config.getPassphrase().getBytes(StandardCharsets.UTF_8)
                    : null;
            jsch.addIdentity(
                    "linecode-key",
                    config.getPrivateKey().getBytes(StandardCharsets.UTF_8),
                    null,
                    passphrase
            );
        }
        Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        if (config.getPassword().length() > 0) {
            session.setPassword(config.getPassword());
        }
        Properties properties = new Properties();
        properties.put("StrictHostKeyChecking", "ask");
        properties.put("IdentitiesOnly", "yes");
        properties.put("PreferredAuthentications", config.getPrivateKey().trim().length() > 0
                ? "publickey,password,keyboard-interactive"
                : "password,keyboard-interactive,publickey");
        session.setConfig(properties);
        session.setUserInfo(new TrustOnFirstUseUserInfo());
        session.connect(timeoutMs);
        return session;
    }

    private File knownHostsFile() throws Exception {
        File dir = new File(context.getFilesDir(), "ssh");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建 SSH 配置目录: " + dir.getPath());
        }
        File file = new File(dir, "known_hosts");
        if (!file.exists() && !file.createNewFile()) {
            throw new IllegalStateException("无法创建 known_hosts 文件: " + file.getPath());
        }
        return file;
    }

    private static final class TrustOnFirstUseUserInfo implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
            return !text.contains("has changed") && !text.contains("offending key");
        }

        @Override
        public void showMessage(String message) {
        }
    }

    private TermuxSetupResult parseTermuxSetupOutput(String output) {
        String username = readValue(output, "LINEAI_TERMUX_USERNAME");
        String host = readValue(output, "LINEAI_TERMUX_HOST");
        int port = parsePort(readValue(output, "LINEAI_TERMUX_PORT"));
        String shell = readValue(output, "LINEAI_TERMUX_SHELL");
        String rcPath = readValue(output, "LINEAI_TERMUX_RC");
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(output == null ? "" : output);
        String privateKey = matcher.find() ? matcher.group(1).trim() : "";
        if (username.length() == 0 || privateKey.length() == 0) {
            throw new IllegalStateException("Termux 已返回结果，但未解析到 username 或 private key。\n" + redactPrivateKey(output));
        }
        SshConfig config = new SshConfig(
                host.length() == 0 ? SshConfig.DEFAULT_HOST : host,
                port,
                username,
                "",
                privateKey,
                ""
        );
        return new TermuxSetupResult(config, output == null ? "" : output, shell, rcPath);
    }

    private void ensureTermuxInstalled() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("未检测到 Termux，请先安装 Termux。");
        }
    }

    private static int drainAvailable(InputStream stream, StringBuilder target) throws Exception {
        int total = 0;
        byte[] buffer = new byte[4096];
        while (stream.available() > 0) {
            int read = stream.read(buffer, 0, Math.min(buffer.length, stream.available()));
            if (read <= 0) {
                break;
            }
            target.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            total += read;
        }
        return total;
    }

    private static String combine(String output, String error, String fallback) {
        StringBuilder builder = new StringBuilder();
        if (output != null && output.trim().length() > 0) {
            builder.append(output.trim());
        }
        if (error != null && error.trim().length() > 0) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(error.trim());
        }
        return builder.length() == 0 ? fallback : builder.toString();
    }

    private static String combineRaw(String output, String error) {
        StringBuilder builder = new StringBuilder();
        if (output != null && output.length() > 0) {
            builder.append(output);
        }
        if (error != null && error.length() > 0) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(error);
        }
        return builder.toString();
    }

    private static String readValue(String output, String key) {
        if (output == null || key == null) {
            return "";
        }
        String prefix = key + "=";
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 ? port : SshConfig.DEFAULT_PORT;
        } catch (Exception ignored) {
            return SshConfig.DEFAULT_PORT;
        }
    }

    private static String redactPrivateKey(String output) {
        return output == null ? "" : PRIVATE_KEY_PATTERN.matcher(output)
                .replaceAll("LINEAI_PRIVATE_KEY=[已保存到 SSH Private key]");
    }

    private static String joinLines(String... lines) {
        StringBuilder builder = new StringBuilder();
        if (lines != null) {
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(lines[i]);
            }
        }
        return builder.toString();
    }

    public static final class TermuxSetupResult {
        private final SshConfig config;
        private final String output;
        private final String shell;
        private final String rcPath;

        TermuxSetupResult(SshConfig config, String output, String shell, String rcPath) {
            this.config = config == null ? SshConfig.defaultConfig() : config;
            this.output = output == null ? "" : output;
            this.shell = shell == null ? "" : shell;
            this.rcPath = rcPath == null ? "" : rcPath;
        }

        public SshConfig getConfig() {
            return config;
        }

        public String getOutput() {
            return output;
        }

        public String getShell() {
            return shell;
        }

        public String getRcPath() {
            return rcPath;
        }
    }
}
