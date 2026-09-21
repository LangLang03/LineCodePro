#pragma once

#include <expected>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/extension_config.h"

namespace linecode::application {

struct AgentToolOption final {
  std::string name;
  std::string category;
  std::string display_name;
  std::string display_description;
  bool selected_by_default{};

  bool operator==(const AgentToolOption &) const = default;
};

struct AgentMcpOption final {
  std::string id;
  std::string name;
  std::string description;

  bool operator==(const AgentMcpOption &) const = default;
};

struct AgentDraftContext final {
  std::vector<AgentToolOption> tools;
  std::vector<AgentMcpOption> mcps;

  bool operator==(const AgentDraftContext &) const = default;
};

enum class AgentDraftErrorCode {
  empty_description,
  missing_model,
  catalog_load,
  completion,
  invalid_json,
  missing_fields,
};

struct AgentDraftError final {
  AgentDraftErrorCode code{AgentDraftErrorCode::completion};
  std::string message;

  bool operator==(const AgentDraftError &) const = default;
};

template <class Value>
using AgentDraftResult = std::expected<Value, AgentDraftError>;

// Owns the complete application use case exposed to the editor. UI code does
// not know how models, runtime tools, MCPs, prompts, or JSON are implemented.
class AgentExtensionDraftGenerator {
public:
  virtual ~AgentExtensionDraftGenerator() = default;

  [[nodiscard]] virtual huxerui::Task<AgentDraftResult<AgentDraftContext>>
  LoadContext() = 0;
  [[nodiscard]] virtual huxerui::Task<AgentDraftResult<domain::AgentExtension>>
  Generate(std::string description) = 0;
};

} // namespace linecode::application
