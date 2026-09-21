#pragma once

#include <memory>

#include <huxerui/file.h>

#include "application/ports/extension_store.h"
#include "application/ports/terminal_provider.h"

namespace linecode::infrastructure {

class SqliteExtensionStoreState;

class SqliteExtensionStore final : public application::AgentExtensionStore,
                                   public application::McpExtensionStore,
                                   public application::TerminalProviderStore {
public:
  explicit SqliteExtensionStore(huxerui::File database_file);
  SqliteExtensionStore(
      huxerui::File database_file,
      std::shared_ptr<application::ExtensionClock> clock,
      std::shared_ptr<application::ExtensionIdGenerator> id_generator);

  [[nodiscard]] huxerui::Task<
      application::ExtensionStoreResult<std::vector<domain::AgentExtension>>>
  ListAgents() override;
  [[nodiscard]] huxerui::Task<
      application::ExtensionStoreResult<std::optional<domain::AgentExtension>>>
  FindAgent(std::string id) override;
  [[nodiscard]] huxerui::Task<
      application::ExtensionStoreResult<domain::AgentExtension>>
  SaveAgent(domain::AgentExtension value) override;
  [[nodiscard]] huxerui::Task<application::ExtensionStoreResult<void>>
  SetAgentEnabled(std::string id, bool enabled) override;
  [[nodiscard]] huxerui::Task<application::ExtensionStoreResult<void>>
  DeleteAgents(std::vector<std::string> ids) override;

  [[nodiscard]] huxerui::Task<
      application::ExtensionStoreResult<std::vector<domain::McpExtension>>>
  ListMcps() override;
  [[nodiscard]] huxerui::Task<
      application::ExtensionStoreResult<std::optional<domain::McpExtension>>>
  FindMcp(std::string id) override;
  [[nodiscard]] huxerui::Task<
      application::ExtensionStoreResult<domain::McpExtension>>
  SaveMcp(domain::McpExtension value) override;
  [[nodiscard]] huxerui::Task<application::ExtensionStoreResult<void>>
  SetMcpEnabled(std::string id, bool enabled) override;
  [[nodiscard]] huxerui::Task<application::ExtensionStoreResult<void>>
  DeleteMcps(std::vector<std::string> ids) override;

  [[nodiscard]] huxerui::Task<application::TerminalProviderResult<
      std::vector<domain::TerminalProviderConfig>>>
  ListTerminalProviders() override;
  [[nodiscard]] huxerui::Task<
      application::TerminalProviderResult<domain::TerminalProviderConfig>>
  SaveTerminalProvider(domain::TerminalProviderConfig value) override;
  [[nodiscard]] huxerui::Task<application::TerminalProviderResult<void>>
  SetTerminalProviderEnabled(std::string id, bool enabled) override;
  [[nodiscard]] huxerui::Task<application::TerminalProviderResult<void>>
  DeleteTerminalProvider(std::string id) override;

private:
  std::shared_ptr<SqliteExtensionStoreState> state_;
  std::shared_ptr<application::ExtensionClock> clock_;
  std::shared_ptr<application::ExtensionIdGenerator> id_generator_;
};

} // namespace linecode::infrastructure
