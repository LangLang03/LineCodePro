#pragma once

#include <expected>
#include <string>
#include <string_view>
#include <vector>

#include "domain/extension_config.h"

namespace linecode::infrastructure {

[[nodiscard]] std::string
EncodeExtensionStringList(const std::vector<std::string> &values);
[[nodiscard]] std::vector<std::string>
DecodeExtensionStringList(std::string_view json);

[[nodiscard]] std::string
EncodeMcpRequestHeaders(const std::vector<domain::McpRequestHeader> &headers);
[[nodiscard]] std::vector<domain::McpRequestHeader>
DecodeMcpRequestHeaders(std::string_view json);

[[nodiscard]] std::string
EncodeMcpTools(const std::vector<domain::McpToolSummary> &tools);
[[nodiscard]] std::vector<domain::McpToolSummary>
DecodeMcpTools(std::string_view json);

[[nodiscard]] std::expected<std::vector<domain::McpToolSummary>, std::string>
DecodeMcpToolResponse(std::string_view response);

} // namespace linecode::infrastructure
