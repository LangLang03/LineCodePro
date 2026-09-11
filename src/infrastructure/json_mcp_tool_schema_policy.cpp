#include "infrastructure/json_mcp_tool_schema_policy.h"

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {

std::string
JsonMcpToolSchemaPolicy::Normalize(std::string_view schema_json) const {
  static constexpr auto fallback =
      R"({"additionalProperties":true,"properties":{},"type":"object"})";
  auto parsed = archive_json::Parse(schema_json);
  const auto *object = parsed ? archive_json::AsObject(&*parsed) : nullptr;
  if (!object)
    return fallback;
  const auto *type =
      archive_json::AsString(archive_json::Find(*object, "type"));
  return type && *type == "object" ? archive_json::Serialize(*parsed)
                                     : std::string{fallback};
}

} // namespace linecode::infrastructure
