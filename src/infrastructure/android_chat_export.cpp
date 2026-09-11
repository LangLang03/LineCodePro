#include "infrastructure/chat_export.h"

#if defined(__ANDROID__)

#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/chat_export.h"
#include "application/ports/chat_export_delivery.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/chat-export";
constexpr auto kJavaFactoryClass =
    "cn.lineai.platform.LineCodeChatExportModule";

huxerui::PlatformPayload EncodeFile(
    const application::ShareFileRequest &file) {
  return huxerui::PlatformPayload::Object{
      {"fileName", file.file_name},
      {"mimeType", file.mime_type},
      {"content", huxerui::Bytes{file.content.begin(), file.content.end()}},
  };
}

huxerui::PlatformPayload EncodeTranscript(
    const application::RenderedTranscriptRequest &request) {
  huxerui::PlatformPayload::List blocks;
  blocks.reserve(request.blocks.size());
  for (const auto &block : request.blocks) {
    blocks.emplace_back(huxerui::PlatformPayload::Object{
        {"speaker", block.speaker},
        {"content", block.content},
        {"user", block.user},
    });
  }
  return huxerui::PlatformPayload::Object{
      {"rendererId", request.renderer_id},
      {"fileName", request.file_name},
      {"mimeType", request.mime_type},
      {"blocks", std::move(blocks)},
  };
}

class AndroidChatExportDelivery final
    : public application::ChatExportDelivery {
public:
  explicit AndroidChatExportDelivery(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  [[nodiscard]] bool CopyText(const std::string_view text) override {
    return Invoke("copyText", std::string{text});
  }

  [[nodiscard]] bool ShareText(const std::string_view text) override {
    return Invoke("shareText", std::string{text});
  }

  [[nodiscard]] bool
  ShareFile(const application::ShareFileRequest &file) override {
    return Invoke("shareFile", EncodeFile(file));
  }

  [[nodiscard]] bool RenderAndShare(
      const application::RenderedTranscriptRequest &request) override {
    return Invoke("renderAndShare", EncodeTranscript(request));
  }

private:
  template <class Payload>
  [[nodiscard]] bool Invoke(const std::string_view method, Payload payload) {
    if (!channel_.IsOpen())
      return false;
    channel_.Invoke<std::monostate>(
        std::string{method}, std::move(payload),
        [](huxerui::PlatformResult<std::monostate>) {});
    return true;
  }

  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallChatExport(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::ChatExportDelivery>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidChatExportDelivery>(std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::ChatExportDelivery>>(
      kPlatformModuleName, std::move(factory));
  auto delivery = root.OpenPlatformModule<
      std::shared_ptr<application::ChatExportDelivery>>(kPlatformModuleName);
  root.Provide<application::ChatExportService>(
      std::make_shared<application::ChatExportService>(
          std::move(delivery),
          application::CreateDefaultChatExportRegistry()));
}

} // namespace linecode::infrastructure

#endif
