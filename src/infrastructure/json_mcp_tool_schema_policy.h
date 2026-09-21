#pragma once

#include "application/ports/mcp_tool_schema_policy.h"

namespace linecode::infrastructure {

class JsonMcpToolSchemaPolicy final
    : public application::McpToolSchemaPolicy {
public:
  [[nodiscard]] std::string
  Normalize(std::string_view schema_json) const override;
};

} // namespace linecode::infrastructure
