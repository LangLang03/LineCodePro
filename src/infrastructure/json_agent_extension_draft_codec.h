#pragma once

#include "application/agent_extension_draft.h"

namespace linecode::infrastructure {

class JsonAgentExtensionDraftCodec final
    : public application::AgentDraftCodec {
public:
  [[nodiscard]] std::string
  EncodeContext(std::string_view description,
                const application::AgentDraftContext &context) const override;
  [[nodiscard]] application::AgentDraftResult<
      application::DecodedAgentDraft>
  Decode(std::string_view response) const override;
};

} // namespace linecode::infrastructure
