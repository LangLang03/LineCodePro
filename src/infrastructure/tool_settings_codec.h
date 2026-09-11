#pragma once

#include <expected>
#include <string>
#include <string_view>

#include "domain/tool_settings.h"

namespace linecode::infrastructure {

struct ToolSettingsCodecError final {
  std::string message;

  bool operator==(const ToolSettingsCodecError &) const = default;
};

[[nodiscard]] std::string
EncodeWebSearchConfig(const domain::WebSearchConfig &config);

[[nodiscard]] std::expected<domain::WebSearchConfig, ToolSettingsCodecError>
DecodeWebSearchConfig(std::string_view json);

} // namespace linecode::infrastructure
