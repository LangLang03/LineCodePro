#include "infrastructure/android_terminal_provider.h"

#if defined(__ANDROID__)

#include <memory>
#include <cstddef>
#include <string>
#include <utility>
#include <variant>
#include <vector>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/terminal_provider.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/terminal-provider";
constexpr auto kJavaFactoryClass =
    "cn.lineai.terminal.LineCodeTerminalProviderModule";

struct ScanPayload final {
  std::vector<domain::ScannedTerminalProvider> values;

  static ScanPayload Decode(const huxerui::PlatformPayload &payload) {
    ScanPayload result;
    const auto &items = payload.AsList();
    result.values.reserve(items.size());
    for (const auto &item : items) {
      const auto &fields = item.AsObject();
      result.values.push_back({
          .package_name = std::string{fields.at("packageName").AsString()},
          .service_class = std::string{fields.at("serviceClass").AsString()},
          .label = std::string{fields.at("label").AsString()},
      });
    }
    return result;
  }
};

struct ProviderPayload final {
  domain::TerminalProviderConfig provider;

  [[nodiscard]] static huxerui::PlatformPayload::Object
  Fields(const domain::TerminalProviderConfig &provider) {
    return {
        {"packageName", provider.package_name},
        {"serviceClass", provider.service_class},
    };
  }
};

struct ProviderOnlyPayload final {
  domain::TerminalProviderConfig provider;

  static huxerui::PlatformPayload Encode(const ProviderOnlyPayload &payload) {
    return ProviderPayload::Fields(payload.provider);
  }
};

struct ShellPayload final {
  domain::TerminalProviderConfig provider;
  application::TerminalShellRequest request;

  static huxerui::PlatformPayload Encode(const ShellPayload &payload) {
    auto fields = ProviderPayload::Fields(payload.provider);
    fields.emplace("command", payload.request.command);
    fields.emplace("cwd", payload.request.working_directory);
    fields.emplace("timeoutMs", payload.request.timeout_milliseconds);
    return fields;
  }
};

struct FilePayload final {
  domain::TerminalProviderConfig provider;
  std::string path;

  static huxerui::PlatformPayload Encode(const FilePayload &payload) {
    auto fields = ProviderPayload::Fields(payload.provider);
    fields.emplace("path", payload.path);
    return fields;
  }
};

struct WriteFilePayload final {
  domain::TerminalProviderConfig provider;
  std::string path;
  std::vector<std::byte> data;

  static huxerui::PlatformPayload Encode(const WriteFilePayload &payload) {
    auto fields = ProviderPayload::Fields(payload.provider);
    fields.emplace("path", payload.path);
    fields.emplace("data", huxerui::Bytes{payload.data});
    return fields;
  }
};

struct ReadFileChunkPayload final {
  domain::TerminalProviderConfig provider;
  std::string path;
  std::int64_t offset{};
  std::int32_t maximum_bytes{};

  static huxerui::PlatformPayload Encode(const ReadFileChunkPayload &payload) {
    auto fields = ProviderPayload::Fields(payload.provider);
    fields.emplace("path", payload.path);
    fields.emplace("offset", payload.offset);
    fields.emplace("size", static_cast<std::int64_t>(payload.maximum_bytes));
    return fields;
  }
};

struct WriteFileChunkPayload final {
  domain::TerminalProviderConfig provider;
  std::string path;
  std::int64_t offset{};
  std::vector<std::byte> data;

  static huxerui::PlatformPayload Encode(const WriteFileChunkPayload &payload) {
    auto fields = ProviderPayload::Fields(payload.provider);
    fields.emplace("path", payload.path);
    fields.emplace("offset", payload.offset);
    fields.emplace("data", huxerui::Bytes{payload.data});
    return fields;
  }
};

struct ShellResultPayload final {
  application::TerminalShellResult value;

  static ShellResultPayload Decode(const huxerui::PlatformPayload &payload) {
    const auto &fields = payload.AsObject();
    return {{
        .exit_code = static_cast<int>(fields.at("exitCode").AsInteger()),
        .standard_output =
            std::string{fields.at("stdout").AsString()},
        .standard_error =
            std::string{fields.at("stderr").AsString()},
    }};
  }
};

struct ProviderInfoPayload final {
  application::TerminalProviderInfo value;

  static ProviderInfoPayload Decode(const huxerui::PlatformPayload &payload) {
    const auto &fields = payload.AsObject();
    return {{
        .provider_type = std::string{fields.at("providerType").AsString()},
        .raw_json = std::string{fields.at("rawJson").AsString()},
        .home_path = std::string{fields.at("home").AsString()},
    }};
  }
};

application::TerminalProviderError
ToError(const huxerui::PlatformError &error) {
  return {.message = error.message.empty() ? error.code : error.message};
}

class AndroidTerminalProviderDiscovery final
    : public application::TerminalProviderGateway {
public:
  explicit AndroidTerminalProviderDiscovery(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  void Scan(Completion completion) override {
    if (!channel_.IsOpen()) {
      completion(std::unexpected(application::TerminalProviderError{
          .message = "Android terminal-provider bridge is closed"}));
      return;
    }
    channel_.Invoke<ScanPayload>(
        "scan", std::monostate{},
        [completion = std::move(completion)](
            huxerui::PlatformResult<ScanPayload> result) mutable {
          if (const auto *error =
                  std::get_if<huxerui::PlatformError>(&result)) {
            completion(std::unexpected(application::TerminalProviderError{
                .message = error->message.empty() ? error->code
                                                  : error->message}));
            return;
          }
          completion(std::move(std::get<ScanPayload>(result).values));
        });
  }

  void ExecuteShell(domain::TerminalProviderConfig provider,
                    application::TerminalShellRequest request,
                    ShellCompletion completion) override {
    Invoke<ShellResultPayload>(
        "executeShell",
        ShellPayload{.provider = std::move(provider),
                     .request = std::move(request)},
        [completion = std::move(completion)](
            application::TerminalProviderResult<ShellResultPayload> result) mutable {
          if (!result) {
            completion(std::unexpected(std::move(result.error())));
            return;
          }
          completion(std::move(result->value));
        });
  }

  void ReadFile(domain::TerminalProviderConfig provider, std::string path,
                BytesCompletion completion) override {
    Invoke<huxerui::Bytes>("readFile",
                           FilePayload{.provider = std::move(provider),
                                       .path = std::move(path)},
                           std::move(completion));
  }

  void WriteFile(domain::TerminalProviderConfig provider, std::string path,
                 std::vector<std::byte> data,
                 VoidCompletion completion) override {
    Invoke<std::monostate>(
        "writeFile",
        WriteFilePayload{.provider = std::move(provider),
                         .path = std::move(path),
                         .data = std::move(data)},
        std::move(completion));
  }

  void DeleteFile(domain::TerminalProviderConfig provider, std::string path,
                  VoidCompletion completion) override {
    Invoke<std::monostate>("deleteFile",
                           FilePayload{.provider = std::move(provider),
                                       .path = std::move(path)},
                           std::move(completion));
  }

  void ListDirectory(domain::TerminalProviderConfig provider,
                     std::string path, TextCompletion completion) override {
    Invoke<std::string>("listDirectory",
                        FilePayload{.provider = std::move(provider),
                                    .path = std::move(path)},
                        std::move(completion));
  }

  void GetProviderInfo(domain::TerminalProviderConfig provider,
                       InfoCompletion completion) override {
    Invoke<ProviderInfoPayload>(
        "providerInfo", ProviderOnlyPayload{.provider = std::move(provider)},
        [completion = std::move(completion)](
            application::TerminalProviderResult<ProviderInfoPayload> result) mutable {
          if (!result) {
            completion(std::unexpected(std::move(result.error())));
            return;
          }
          completion(std::move(result->value));
        });
  }

  void FileExists(domain::TerminalProviderConfig provider, std::string path,
                  BooleanCompletion completion) override {
    Invoke<bool>("fileExists",
                 FilePayload{.provider = std::move(provider),
                             .path = std::move(path)},
                 std::move(completion));
  }

  void FileSize(domain::TerminalProviderConfig provider, std::string path,
                SizeCompletion completion) override {
    Invoke<std::int64_t>("fileSize",
                         FilePayload{.provider = std::move(provider),
                                     .path = std::move(path)},
                         std::move(completion));
  }

  void ReadFileChunk(domain::TerminalProviderConfig provider,
                     std::string path, std::int64_t offset,
                     std::int32_t maximum_bytes,
                     BytesCompletion completion) override {
    Invoke<huxerui::Bytes>(
        "readFileChunk",
        ReadFileChunkPayload{.provider = std::move(provider),
                             .path = std::move(path),
                             .offset = offset,
                             .maximum_bytes = maximum_bytes},
        std::move(completion));
  }

  void WriteFileChunk(domain::TerminalProviderConfig provider,
                      std::string path, std::int64_t offset,
                      std::vector<std::byte> data,
                      VoidCompletion completion) override {
    Invoke<std::monostate>(
        "writeFileChunk",
        WriteFileChunkPayload{.provider = std::move(provider),
                              .path = std::move(path),
                              .offset = offset,
                              .data = std::move(data)},
        std::move(completion));
  }

  void GetFileSize(domain::TerminalProviderConfig provider, std::string path,
                   SizeCompletion completion) override {
    Invoke<std::int64_t>("getFileSize",
                         FilePayload{.provider = std::move(provider),
                                     .path = std::move(path)},
                         std::move(completion));
  }

private:
  template <class Result, class Arguments, class Completion>
  void Invoke(std::string method, Arguments arguments,
              Completion completion) {
    if (!channel_.IsOpen()) {
      completion(std::unexpected(application::TerminalProviderError{
          .message = "Android terminal-provider bridge is closed"}));
      return;
    }
    channel_.Invoke<Result>(
        std::move(method), std::move(arguments),
        [completion = std::move(completion)](
            huxerui::PlatformResult<Result> result) mutable {
          if (const auto *error =
                  std::get_if<huxerui::PlatformError>(&result)) {
            completion(std::unexpected(ToError(*error)));
            return;
          }
          if constexpr (std::same_as<Result, std::monostate>) {
            completion(application::TerminalProviderResult<void>{});
          } else {
            completion(std::get<Result>(std::move(result)));
          }
        });
  }

  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallAndroidTerminalProvider(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::TerminalProviderGateway>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidTerminalProviderDiscovery>(
        std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::TerminalProviderGateway>>(
      kPlatformModuleName, std::move(factory));
  auto gateway = root.OpenPlatformModule<
      std::shared_ptr<application::TerminalProviderGateway>>(
      kPlatformModuleName);
  root.Provide<application::TerminalProviderGateway>(gateway);
  root.Provide<application::TerminalProviderDiscovery>(gateway);
}

} // namespace linecode::infrastructure

#endif
