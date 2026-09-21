#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "application/ports/agent_extension_draft.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/extension_store.h"
#include "application/ports/model_store.h"
#include "application/ports/tool_registry.h"
#include "application/tool_text_catalog.h"

namespace linecode::application {

struct DecodedAgentDraft final {
  std::string name;
  std::string slug;
  std::string prompt;
  std::string trigger;
  std::vector<std::string> tool_names;
  std::vector<std::string> mcp_ids;

  bool operator==(const DecodedAgentDraft &) const = default;
};

class AgentDraftCodec {
public:
  virtual ~AgentDraftCodec() = default;

  [[nodiscard]] virtual std::string
  EncodeContext(std::string_view description,
                const AgentDraftContext &context) const = 0;
  [[nodiscard]] virtual AgentDraftResult<DecodedAgentDraft>
  Decode(std::string_view response) const = 0;
};

class CompletionAgentExtensionDraftGenerator final
    : public AgentExtensionDraftGenerator {
public:
  CompletionAgentExtensionDraftGenerator(
      std::shared_ptr<ModelStore> models,
      std::shared_ptr<CompletionGateway> completion,
      std::shared_ptr<ToolRegistry> tools,
      std::shared_ptr<McpExtensionStore> mcps,
      std::shared_ptr<const AgentDraftCodec> codec,
      ToolTextLanguage language = ToolTextLanguage::english);

  [[nodiscard]] huxerui::Task<AgentDraftResult<AgentDraftContext>>
  LoadContext() override;
  [[nodiscard]] huxerui::Task<AgentDraftResult<domain::AgentExtension>>
  Generate(std::string description) override;

private:
  std::shared_ptr<ModelStore> models_;
  std::shared_ptr<CompletionGateway> completion_;
  std::shared_ptr<ToolRegistry> tools_;
  std::shared_ptr<McpExtensionStore> mcps_;
  std::shared_ptr<const AgentDraftCodec> codec_;
  ToolTextLanguage language_;
};

} // namespace linecode::application
