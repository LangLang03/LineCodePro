#include "infrastructure/error_log_platform.h"

#if defined(_WIN32)

#include <memory>
#include <string_view>

#include <huxerui/clipboard.h>
#include <huxerui/platform_adapter.h>

#include "application/ports/error_log_store.h"

namespace linecode::infrastructure {
namespace {

class WindowsErrorLogPlatformActions final
    : public application::ErrorLogPlatformActions {
public:
  explicit WindowsErrorLogPlatformActions(huxerui::PlatformAdapter &adapter)
      : adapter_(&adapter) {}

  bool CopyText(std::string_view text) override {
    huxerui::PlatformClipboard *clipboard = adapter_->Clipboard();
    return clipboard != nullptr && clipboard->WriteText(text);
  }

  bool OpenText(std::string_view, std::string_view,
                std::string_view) override {
    // Windows has no legacy Android ACTION_VIEW chooser. Keep the failure
    // explicit; the page reports its localized open failure without exposing
    // a temporary file or silently copying content.
    return false;
  }

private:
  huxerui::PlatformAdapter *adapter_;
};

} // namespace

void InstallErrorLogPlatformActions(huxerui::RootContext &root) {
  constexpr auto name = "linecode/error-log-platform-actions";
  root.RegisterPlatformModule<
      std::shared_ptr<application::ErrorLogPlatformActions>>(
      name, [](huxerui::PlatformAdapter &adapter) {
        return std::make_shared<WindowsErrorLogPlatformActions>(adapter);
      });
  root.Provide<application::ErrorLogPlatformActions>(
      root.OpenPlatformModule<
          std::shared_ptr<application::ErrorLogPlatformActions>>(name));
}

} // namespace linecode::infrastructure

#endif
