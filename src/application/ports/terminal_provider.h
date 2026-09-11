#pragma once

#include <expected>
#include <cstddef>
#include <functional>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/terminal_provider.h"

namespace linecode::application {

struct TerminalProviderError final {
  std::string message;

  bool operator==(const TerminalProviderError &) const = default;
};

template <class Value>
using TerminalProviderResult = std::expected<Value, TerminalProviderError>;

class TerminalProviderStore {
public:
  virtual ~TerminalProviderStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      TerminalProviderResult<std::vector<domain::TerminalProviderConfig>>>
  ListTerminalProviders() = 0;
  [[nodiscard]] virtual huxerui::Task<
      TerminalProviderResult<domain::TerminalProviderConfig>>
  SaveTerminalProvider(domain::TerminalProviderConfig value) = 0;
  [[nodiscard]] virtual huxerui::Task<TerminalProviderResult<void>>
  SetTerminalProviderEnabled(std::string id, bool enabled) = 0;
  [[nodiscard]] virtual huxerui::Task<TerminalProviderResult<void>>
  DeleteTerminalProvider(std::string id) = 0;
};

class TerminalProviderDiscovery {
public:
  using Completion = std::function<void(TerminalProviderResult<
      std::vector<domain::ScannedTerminalProvider>>)>;

  virtual ~TerminalProviderDiscovery() = default;
  virtual void Scan(Completion completion) = 0;
};

struct TerminalShellRequest final {
  std::string command;
  std::string working_directory;
  std::int64_t timeout_milliseconds{30'000};

  bool operator==(const TerminalShellRequest &) const = default;
};

struct TerminalShellResult final {
  int exit_code{};
  std::string standard_output;
  std::string standard_error;

  bool operator==(const TerminalShellResult &) const = default;
};

class TerminalProviderGateway : public TerminalProviderDiscovery {
public:
  using VoidCompletion =
      std::function<void(TerminalProviderResult<void>)>;
  using ShellCompletion =
      std::function<void(TerminalProviderResult<TerminalShellResult>)>;
  using BytesCompletion = std::function<
      void(TerminalProviderResult<std::vector<std::byte>>)>;
  using TextCompletion =
      std::function<void(TerminalProviderResult<std::string>)>;

  virtual void ExecuteShell(domain::TerminalProviderConfig provider,
                            TerminalShellRequest request,
                            ShellCompletion completion) = 0;
  virtual void ReadFile(domain::TerminalProviderConfig provider,
                        std::string path, BytesCompletion completion) = 0;
  virtual void WriteFile(domain::TerminalProviderConfig provider,
                         std::string path, std::vector<std::byte> data,
                         VoidCompletion completion) = 0;
  virtual void DeleteFile(domain::TerminalProviderConfig provider,
                          std::string path, VoidCompletion completion) = 0;
  virtual void ListDirectory(domain::TerminalProviderConfig provider,
                             std::string path, TextCompletion completion) = 0;
};

} // namespace linecode::application
