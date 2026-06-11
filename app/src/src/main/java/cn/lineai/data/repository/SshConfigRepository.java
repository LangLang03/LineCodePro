package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.SshConfig;

public final class SshConfigRepository {
    public static final String KEY_SSH_CONFIG = "@lineai_ssh_config";

    private final SettingsRepository settingsRepository;

    public SshConfigRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
    }

    public synchronized SshConfig get() {
        return SshConfig.fromJson(settingsRepository.getString(KEY_SSH_CONFIG, ""));
    }

    public synchronized void save(SshConfig config) {
        settingsRepository.setString(KEY_SSH_CONFIG, (config == null ? SshConfig.defaultConfig() : config).toJson());
    }
}
