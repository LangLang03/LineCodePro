#pragma once

#include <cstdint>
#include <expected>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "domain/extension_config.h"

namespace linecode::application {

struct ExtensionStoreError final {
  std::string message;

  bool operator==(const ExtensionStoreError &) const = default;
};

template <class Value>
using ExtensionStoreResult = std::expected<Value, ExtensionStoreError>;

class AgentExtensionStore {
public:
  virtual ~AgentExtensionStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      ExtensionStoreResult<std::vector<domain::AgentExtension>>>
  ListAgents() = 0;
  [[nodiscard]] virtual huxerui::Task<
      ExtensionStoreResult<std::optional<domain::AgentExtension>>>
  FindAgent(std::string id) = 0;
  [[nodiscard]] virtual huxerui::Task<
      ExtensionStoreResult<domain::AgentExtension>>
  SaveAgent(domain::AgentExtension value) = 0;
  [[nodiscard]] virtual huxerui::Task<ExtensionStoreResult<void>>
  SetAgentEnabled(std::string id, bool enabled) = 0;
  [[nodiscard]] virtual huxerui::Task<ExtensionStoreResult<void>>
  DeleteAgents(std::vector<std::string> ids) = 0;
};

class McpExtensionStore {
public:
  virtual ~McpExtensionStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      ExtensionStoreResult<std::vector<domain::McpExtension>>>
  ListMcps() = 0;
  [[nodiscard]] virtual huxerui::Task<
      ExtensionStoreResult<std::optional<domain::McpExtension>>>
  FindMcp(std::string id) = 0;
  [[nodiscard]] virtual huxerui::Task<
      ExtensionStoreResult<domain::McpExtension>>
  SaveMcp(domain::McpExtension value) = 0;
  [[nodiscard]] virtual huxerui::Task<ExtensionStoreResult<void>>
  SetMcpEnabled(std::string id, bool enabled) = 0;
  [[nodiscard]] virtual huxerui::Task<ExtensionStoreResult<void>>
  DeleteMcps(std::vector<std::string> ids) = 0;
};

class ExtensionClock {
public:
  virtual ~ExtensionClock() = default;
  [[nodiscard]] virtual std::int64_t NowMilliseconds() const noexcept = 0;
};

class ExtensionIdGenerator {
public:
  virtual ~ExtensionIdGenerator() = default;
  [[nodiscard]] virtual std::string NewId(std::string_view prefix) = 0;
};

} // namespace linecode::application
