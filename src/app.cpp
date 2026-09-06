#include <huxerui/huxerui.h>
#include <huxerui/webview.h>

#include "app/app_root.h"
#if defined(__ANDROID__)
#include "infrastructure/android_keep_alive.h"
#endif
#if defined(__ANDROID__) || defined(_WIN32)
#include "infrastructure/error_log_platform.h"
#include "infrastructure/external_link.h"
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
        .root_hooks = {linecode::infrastructure::InstallAndroidKeepAlive,
                       linecode::infrastructure::InstallExternalLink,
                       linecode::infrastructure::InstallErrorLogPlatformActions,
                       huxerui::InstallWebView},
#elif defined(_WIN32)
        .root_hooks = {linecode::infrastructure::InstallExternalLink,
                       linecode::infrastructure::InstallErrorLogPlatformActions,
                       huxerui::InstallWebView},
#endif
    },
};
