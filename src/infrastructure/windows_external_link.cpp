#include "infrastructure/external_link.h"

#if defined(_WIN32)

#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <shellapi.h>
#include <windows.h>

#pragma comment(lib, "Shell32.lib")

#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include <huxerui/platform_adapter.h>

#include "application/ports/external_link.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/external-link";

bool IsSupportedUrl(std::string_view url) {
  return url.starts_with("https://") || url.starts_with("http://");
}

std::wstring Utf8ToWide(std::string_view value) {
  if (value.empty()) {
    return {};
  }
  const int size =
      MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                          static_cast<int>(value.size()), nullptr, 0);
  if (size <= 0) {
    return {};
  }
  std::wstring wide(static_cast<std::size_t>(size), L'\0');
  if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                          static_cast<int>(value.size()), wide.data(),
                          size) <= 0) {
    return {};
  }
  return wide;
}

class WindowsExternalLinkService final
    : public application::ExternalLinkService {
public:
  void Open(std::string_view url) override {
    if (!IsSupportedUrl(url)) {
      return;
    }
    const std::wstring target = Utf8ToWide(url);
    if (target.empty()) {
      return;
    }
    static_cast<void>(ShellExecuteW(nullptr, L"open", target.c_str(), nullptr,
                                    nullptr, SW_SHOWNORMAL));
  }
};

} // namespace

void InstallExternalLink(huxerui::RootContext &root) {
  root.RegisterPlatformModule<
      std::shared_ptr<application::ExternalLinkService>>(
      kPlatformModuleName,
      [](huxerui::PlatformAdapter &)
          -> std::shared_ptr<application::ExternalLinkService> {
        return std::make_shared<WindowsExternalLinkService>();
      });
  root.Provide<application::ExternalLinkService>(
      root.OpenPlatformModule<
          std::shared_ptr<application::ExternalLinkService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
