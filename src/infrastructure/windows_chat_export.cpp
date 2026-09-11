#include "infrastructure/chat_export.h"

#if defined(_WIN32)

#include <memory>
#include <string_view>

#include <huxerui/clipboard.h>
#include <huxerui/platform_adapter.h>

#include "application/chat_export.h"
#include "application/ports/chat_export_delivery.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/chat-export";

class WindowsChatExportDelivery final
    : public application::ChatExportDelivery {
public:
  explicit WindowsChatExportDelivery(huxerui::PlatformClipboard *clipboard)
      : clipboard_(clipboard) {}

  [[nodiscard]] bool CopyText(const std::string_view text) override {
    return clipboard_ != nullptr && clipboard_->WriteText(text);
  }

  [[nodiscard]] bool ShareText(std::string_view) override { return false; }
  [[nodiscard]] bool ShareFile(
      const application::ShareFileRequest &) override {
    return false;
  }
  [[nodiscard]] bool RenderAndShare(
      const application::RenderedTranscriptRequest &) override {
    return false;
  }

private:
  huxerui::PlatformClipboard *clipboard_{};
};

} // namespace

void InstallChatExport(huxerui::RootContext &root) {
  root.RegisterPlatformModule<
      std::shared_ptr<application::ChatExportDelivery>>(
      kPlatformModuleName,
      [](huxerui::PlatformAdapter &adapter)
          -> std::shared_ptr<application::ChatExportDelivery> {
        return std::make_shared<WindowsChatExportDelivery>(
            adapter.Clipboard());
      });
  auto delivery = root.OpenPlatformModule<
      std::shared_ptr<application::ChatExportDelivery>>(kPlatformModuleName);
  root.Provide<application::ChatExportService>(
      std::make_shared<application::ChatExportService>(
          std::move(delivery),
          application::CreateDefaultChatExportRegistry()));
}

} // namespace linecode::infrastructure

#endif
