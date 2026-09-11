#pragma once

#include <string>
#include <string_view>

namespace linecode::application {

class McpToolSchemaPolicy {
public:
  virtual ~McpToolSchemaPolicy() = default;

  // Returns an object-shaped JSON schema. Invalid/non-object tool schemas are
  // replaced with the permissive legacy fallback.
  [[nodiscard]] virtual std::string
  Normalize(std::string_view schema_json) const = 0;
};

} // namespace linecode::application
