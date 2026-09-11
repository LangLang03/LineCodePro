#include "infrastructure/share_text.h"

#if defined(_WIN32)

#include <memory>
#include <string_view>

#include <huxerui/platform_adapter.h>

#include "application/ports/share_text.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/share-text";

class WindowsShareTextService final : public application::ShareTextService {
public:
  [[nodiscard]] bool Share(std::string_view) override { return false; }
};

} // namespace

void InstallShareText(huxerui::RootContext &root) {
  root.RegisterPlatformModule<std::shared_ptr<application::ShareTextService>>(
      kPlatformModuleName,
      [](huxerui::PlatformAdapter &)
          -> std::shared_ptr<application::ShareTextService> {
        return std::make_shared<WindowsShareTextService>();
      });
  root.Provide<application::ShareTextService>(
      root.OpenPlatformModule<std::shared_ptr<application::ShareTextService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
