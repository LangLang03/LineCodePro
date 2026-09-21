#include <huxerui/huxerui.h>
#include <huxerui/webview.h>

#include "app/app_root.h"
#if defined(__ANDROID__)
#include "infrastructure/android_keep_alive.h"
#include "infrastructure/android_storage_permission.h"
#include "infrastructure/android_terminal_provider.h"
#include "infrastructure/android_termux_integration.h"
#include "infrastructure/android_window_insets.h"
#include "infrastructure/workspace_directory_share.h"
#endif
#if defined(__ANDROID__) || defined(_WIN32)
#include "infrastructure/chat_export.h"
#include "infrastructure/error_log_platform.h"
#include "infrastructure/external_link.h"
#include "infrastructure/skill_hub_platform.h"
#include "infrastructure/share_text.h"
#endif
#if defined(_WIN32)
#include "infrastructure/unavailable_termux_integration.h"
#endif

using namespace huxerui;

const Application application{
    linecode::app::AppRoot,
    {
        .window =
            {
                .title = "LineCode Pro",
                .initial_size = {430.0F, 840.0F},
                .minimum_size = Size{360.0F, 640.0F},
                .content_mode = WindowContentMode::EdgeToEdge,
            },
#if defined(__ANDROID__)
        .root_hooks =
            {linecode::infrastructure::InstallAndroidKeepAlive,
             linecode::infrastructure::InstallAndroidStoragePermission,
             linecode::infrastructure::InstallAndroidTerminalProvider,
             linecode::infrastructure::InstallAndroidTermuxIntegration,
             linecode::infrastructure::InstallAndroidWindowInsets,
             linecode::infrastructure::InstallWorkspaceDirectoryShare,
             linecode::infrastructure::InstallChatExport,
             linecode::infrastructure::InstallExternalLink,
             linecode::infrastructure::InstallSkillHubPlatform,
             linecode::infrastructure::InstallShareText,
             linecode::infrastructure::InstallErrorLogPlatformActions,
             huxerui::InstallWebView},
#elif defined(_WIN32)
        .root_hooks =
            {linecode::infrastructure::InstallUnavailableTermuxIntegration,
             linecode::infrastructure::InstallChatExport,
             linecode::infrastructure::InstallExternalLink,
             linecode::infrastructure::InstallSkillHubPlatform,
             linecode::infrastructure::InstallShareText,
             linecode::infrastructure::InstallErrorLogPlatformActions,
             huxerui::InstallWebView},
#endif
    },
};
