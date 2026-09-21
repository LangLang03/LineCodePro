#pragma once

#include <string>
#include <string_view>

namespace linecode::application {

// The legacy shell tool exposes terminal streams as human-readable tool
// content. Transport metadata such as exit_code belongs to the result status,
// not to the model-facing text or the expanded command card.
[[nodiscard]] inline std::string
ShellResultContent(std::string_view standard_output,
                   std::string_view standard_error) {
  const auto trim = [](std::string_view value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string_view::npos)
      return std::string_view{};
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1U);
  };

  standard_output = trim(standard_output);
  standard_error = trim(standard_error);
  std::string content{standard_output};
  if (!standard_error.empty()) {
    if (!content.empty())
      content.push_back('\n');
    content.append(standard_error);
  }
  return content;
}

} // namespace linecode::application
