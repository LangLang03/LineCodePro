#include "infrastructure/error_log_platform.h"

#if defined(_WIN32)

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

#include <huxerui/clipboard.h>
#include <huxerui/platform_adapter.h>
#include <huxerui/task.h>

#include "application/error_log_policy.h"
#include "application/ports/error_log_store.h"

namespace linecode::infrastructure {
namespace {

class UniqueHandle final {
public:
  explicit UniqueHandle(HANDLE value = INVALID_HANDLE_VALUE) noexcept
      : value_(value) {}
  ~UniqueHandle() { Reset(); }

  UniqueHandle(const UniqueHandle &) = delete;
  UniqueHandle &operator=(const UniqueHandle &) = delete;
  UniqueHandle(UniqueHandle &&other) noexcept
      : value_(std::exchange(other.value_, INVALID_HANDLE_VALUE)) {}
  UniqueHandle &operator=(UniqueHandle &&other) noexcept {
    if (this != &other) {
      Reset();
      value_ = std::exchange(other.value_, INVALID_HANDLE_VALUE);
    }
    return *this;
  }

  [[nodiscard]] HANDLE Get() const noexcept { return value_; }
  [[nodiscard]] explicit operator bool() const noexcept {
    return value_ != nullptr && value_ != INVALID_HANDLE_VALUE;
  }

private:
  void Reset() noexcept {
    if (*this) {
      CloseHandle(value_);
    }
    value_ = INVALID_HANDLE_VALUE;
  }

  HANDLE value_;
};

std::optional<std::wstring> TemporaryDirectory() {
  std::wstring buffer(32768U, L'\0');
  const DWORD length =
      GetTempPathW(static_cast<DWORD>(buffer.size()), buffer.data());
  if (length == 0 || length >= buffer.size()) {
    return std::nullopt;
  }
  buffer.resize(length);
  std::wstring directory = buffer + L"LineCode";
  if (!CreateDirectoryW(directory.c_str(), nullptr) &&
      GetLastError() != ERROR_ALREADY_EXISTS) {
    return std::nullopt;
  }
  directory.append(L"\\error_log_views");
  if (!CreateDirectoryW(directory.c_str(), nullptr) &&
      GetLastError() != ERROR_ALREADY_EXISTS) {
    return std::nullopt;
  }
  return directory;
}

bool WriteAll(HANDLE file, std::string_view text) {
  std::size_t offset = 0;
  while (offset < text.size()) {
    const std::size_t remaining = text.size() - offset;
    const DWORD count = static_cast<DWORD>(std::min<std::size_t>(
        remaining, std::numeric_limits<DWORD>::max()));
    DWORD written = 0;
    if (!WriteFile(file, text.data() + offset, count, &written, nullptr) ||
        written == 0) {
      return false;
    }
    offset += written;
  }
  return FlushFileBuffers(file) != FALSE;
}

std::optional<std::wstring> WriteTemporaryLog(std::string title,
                                              std::string text) {
  const auto directory = TemporaryDirectory();
  if (!directory) {
    return std::nullopt;
  }
  const std::string safe_name =
      application::SafeTemporaryErrorLogFileName(title);
  const std::wstring wide_name(safe_name.begin(), safe_name.end());
  const std::wstring path = *directory + L"\\" + wide_name;

  // A prior view is made read-only after writing. Temporarily restore write
  // access only to replace this app-owned cache file with newly redacted text.
  SetFileAttributesW(path.c_str(), FILE_ATTRIBUTE_NORMAL);
  UniqueHandle file(CreateFileW(
      path.c_str(), GENERIC_WRITE, FILE_SHARE_READ, nullptr, CREATE_ALWAYS,
      FILE_ATTRIBUTE_TEMPORARY | FILE_ATTRIBUTE_NOT_CONTENT_INDEXED, nullptr));
  if (!file || !WriteAll(file.Get(), text)) {
    return std::nullopt;
  }
  file = UniqueHandle{};
  if (!SetFileAttributesW(path.c_str(),
                          FILE_ATTRIBUTE_READONLY | FILE_ATTRIBUTE_TEMPORARY |
                              FILE_ATTRIBUTE_NOT_CONTENT_INDEXED)) {
    return std::nullopt;
  }
  return path;
}

class WindowsErrorLogPlatformActions final
    : public application::ErrorLogPlatformActions {
public:
  explicit WindowsErrorLogPlatformActions(huxerui::PlatformAdapter &adapter)
      : adapter_(&adapter) {}

  bool CopyText(std::string_view text) override {
    huxerui::PlatformClipboard *clipboard = adapter_->Clipboard();
    return clipboard != nullptr && clipboard->WriteText(text);
  }

  huxerui::Task<bool> OpenText(std::string title, std::string text,
                              std::string) override {
    auto path = co_await huxerui::RunWorker(
        [](std::string worker_title, std::string worker_text) {
          return WriteTemporaryLog(std::move(worker_title),
                                   std::move(worker_text));
        },
        std::move(title), std::move(text));
    if (!path) {
      co_return false;
    }
    const HINSTANCE result =
        ShellExecuteW(nullptr, L"open", path->c_str(), nullptr, nullptr,
                      SW_SHOWNORMAL);
    co_return reinterpret_cast<INT_PTR>(result) > 32;
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
