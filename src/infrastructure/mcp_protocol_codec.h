#pragma once

#include <expected>
#include <string>
#include <string_view>

namespace linecode::infrastructure {

struct DecodedMcpCallResult final {
  std::string content;
  bool error{};

  bool operator==(const DecodedMcpCallResult &) const = default;
};

[[nodiscard]] std::string BuildMcpInitializeRequest(std::string_view id);
[[nodiscard]] std::expected<std::string, std::string>
BuildMcpCallRequest(std::string_view id, std::string_view tool_name,
                    std::string_view arguments_json);
[[nodiscard]] DecodedMcpCallResult
DecodeMcpCallResponse(std::string_view response);

} // namespace linecode::infrastructure
