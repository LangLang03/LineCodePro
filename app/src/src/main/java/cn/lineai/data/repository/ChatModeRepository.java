package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.ChatMode;

public final class ChatModeRepository {
    private static final String KEY_CHAT_MODE = "@linecode_chat_mode";
    private static final String KEY_RESTORE_PERMISSION_MODE = "@linecode_chat_mode_restore_permission";

    private final SettingsRepository settingsRepository;

    public ChatModeRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
    }

    public synchronized String getMode() {
        return ChatMode.normalize(settingsRepository.getString(KEY_CHAT_MODE, ChatMode.DEFAULT));
    }

    public synchronized void initialize(ToolSettingsRepository toolSettingsRepository) {
        String storedMode = settingsRepository.getString(KEY_CHAT_MODE, "");
        if (storedMode.length() > 0) {
            applyMode(storedMode, toolSettingsRepository);
            return;
        }
        String permissionMode = toolSettingsRepository == null
                ? ToolSettingsRepository.PERMISSION_AUTO
                : toolSettingsRepository.getPermissionMode();
        String initialMode = ToolSettingsRepository.PERMISSION_READONLY.equals(permissionMode)
                ? ChatMode.CHAT
                : ChatMode.DEFAULT;
        settingsRepository.setString(KEY_CHAT_MODE, initialMode);
        rememberRestorablePermission(permissionMode);
    }

    public synchronized void applyMode(String mode, ToolSettingsRepository toolSettingsRepository) {
        String normalized = ChatMode.normalize(mode);
        if (!normalized.equals(getMode())) {
            settingsRepository.setString(KEY_CHAT_MODE, normalized);
        }
        if (toolSettingsRepository == null) {
            return;
        }
        String currentPermission = toolSettingsRepository.getPermissionMode();
        if (ChatMode.CHAT.equals(normalized)) {
            rememberRestorablePermission(currentPermission);
            if (!ToolSettingsRepository.PERMISSION_READONLY.equals(currentPermission)) {
                toolSettingsRepository.setPermissionMode(ToolSettingsRepository.PERMISSION_READONLY);
            }
            return;
        }
        if (ToolSettingsRepository.PERMISSION_READONLY.equals(currentPermission)) {
            toolSettingsRepository.setPermissionMode(getRestorablePermissionMode());
        } else {
            rememberRestorablePermission(currentPermission);
        }
    }

    public synchronized void setModeOnly(String mode) {
        String normalized = ChatMode.normalize(mode);
        if (!normalized.equals(getMode())) {
            settingsRepository.setString(KEY_CHAT_MODE, normalized);
        }
    }

    public synchronized void rememberRestorablePermission(String mode) {
        String normalized = ToolSettingsRepository.normalizePermissionMode(mode);
        if (ToolSettingsRepository.PERMISSION_READONLY.equals(normalized)) {
            return;
        }
        String existing = ToolSettingsRepository.normalizePermissionMode(
                settingsRepository.getString(KEY_RESTORE_PERMISSION_MODE, ToolSettingsRepository.PERMISSION_AUTO));
        if (!normalized.equals(existing)) {
            settingsRepository.setString(KEY_RESTORE_PERMISSION_MODE, normalized);
        }
    }

    private String getRestorablePermissionMode() {
        String mode = ToolSettingsRepository.normalizePermissionMode(
                settingsRepository.getString(KEY_RESTORE_PERMISSION_MODE, ToolSettingsRepository.PERMISSION_AUTO));
        return ToolSettingsRepository.PERMISSION_READONLY.equals(mode)
                ? ToolSettingsRepository.PERMISSION_AUTO
                : mode;
    }
}
