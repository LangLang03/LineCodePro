#include "domain/extension_config.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <ranges>

namespace linecode::domain {
namespace {

[[nodiscard]] std::string Trim(std::string_view value) {
  const auto first = std::ranges::find_if_not(
      value, [](unsigned char byte) { return std::isspace(byte) != 0; });
  const auto last = std::ranges::find_if_not(value | std::views::reverse,
                                             [](unsigned char byte) {
                                               return std::isspace(byte) != 0;
                                             })
                        .base();
  return first < last ? std::string(first, last) : std::string{};
}

void NormalizeStringList(std::vector<std::string> &values) {
  for (auto &value : values)
    value = Trim(value);
  std::erase_if(values, [](const auto &value) { return value.empty(); });
}

[[nodiscard]] std::string SafeToolNamePart(std::string_view value,
                                           std::string_view fallback,
                                           std::size_t maximum_length) {
  const auto raw = Trim(value);
  std::string replaced;
  replaced.reserve(std::min(raw.size(), maximum_length));
  for (const unsigned char character : raw) {
    if (replaced.size() >= maximum_length)
      break;
    const bool allowed =
        (character >= 'a' && character <= 'z') ||
        (character >= 'A' && character <= 'Z') ||
        (character >= '0' && character <= '9') || character == '_' ||
        character == '-';
    replaced.push_back(allowed ? static_cast<char>(character) : '_');
  }
  std::string clean;
  clean.reserve(replaced.size());
  for (const char character : replaced) {
    if (character != '_' || clean.empty() || clean.back() != '_')
      clean.push_back(character);
  }
  while (!clean.empty() && clean.front() == '_')
    clean.erase(clean.begin());
  while (!clean.empty() && clean.back() == '_')
    clean.pop_back();
  if (clean.empty())
    clean = fallback;
  const unsigned char first = static_cast<unsigned char>(clean.front());
  if (!((first >= 'a' && first <= 'z') ||
        (first >= 'A' && first <= 'Z'))) {
    clean = std::string{fallback} + '_' + clean;
  }
  return clean;
}

[[nodiscard]] std::string Base36(std::uint64_t value) {
  constexpr std::string_view digits = "0123456789abcdefghijklmnopqrstuvwxyz";
  std::string result;
  do {
    result.push_back(digits[value % digits.size()]);
    value /= digits.size();
  } while (value != 0);
  std::ranges::reverse(result);
  return result;
}

} // namespace

std::string NormalizeAgentSlug(std::string_view raw,
                               std::string_view fallback) {
  std::string source = Trim(raw);
  if (source.empty())
    source = std::string{fallback};

  std::string normalized;
  normalized.reserve(source.size());
  for (const unsigned char byte : source) {
    const char character = static_cast<char>(std::tolower(byte));
    if ((character >= 'a' && character <= 'z') ||
        (character >= '0' && character <= '9') || character == '-' ||
        character == '_') {
      normalized.push_back(character);
    } else if (std::isspace(byte) != 0) {
      normalized.push_back('-');
    }
  }
  while (normalized.contains("--"))
    normalized.replace(normalized.find("--"), 2, "-");
  while (!normalized.empty() && normalized.front() == '-')
    normalized.erase(normalized.begin());
  while (!normalized.empty() && normalized.back() == '-')
    normalized.pop_back();
  return normalized;
}

std::string NormalizeAgentEditorSlug(std::string_view value) {
  const std::string raw = Trim(value);
  std::string normalized;
  normalized.reserve(std::min<std::size_t>(raw.size(), 48));
  bool last_dash = false;
  for (const unsigned char byte : raw) {
    if (normalized.size() >= 48)
      break;
    const char character = static_cast<char>(std::tolower(byte));
    if ((character >= 'a' && character <= 'z') ||
        (character >= '0' && character <= '9') || character == '_') {
      normalized.push_back(character);
      last_dash = false;
    } else if (!last_dash && !normalized.empty()) {
      normalized.push_back('-');
      last_dash = true;
    }
  }
  while (!normalized.empty() &&
         (normalized.back() == '-' || normalized.back() == '_'))
    normalized.pop_back();
  if (normalized.empty())
    return {};
  if (normalized.front() < 'a' || normalized.front() > 'z')
    return "agent-" + normalized;
  return normalized;
}

AgentExtension NormalizeAgentExtension(AgentExtension value) {
  value.slug = NormalizeAgentSlug(value.slug, value.name);
  NormalizeStringList(value.tool_names);
  NormalizeStringList(value.mcp_ids);
  return value;
}

bool IsHttpMcpUrl(std::string_view value) {
  const auto trimmed = Trim(value);
  if (trimmed.size() < 7)
    return false;
  std::string prefix;
  prefix.reserve(std::min<std::size_t>(8, trimmed.size()));
  std::ranges::transform(
      trimmed.substr(0, std::min<std::size_t>(8, trimmed.size())),
      std::back_inserter(prefix),
      [](unsigned char byte) { return static_cast<char>(std::tolower(byte)); });
  return prefix.starts_with("http://") || prefix.starts_with("https://");
}

McpExtension NormalizeMcpExtension(McpExtension value) {
  value.url = Trim(value.url);
  while (value.url.ends_with('/') && value.url.size() > 8)
    value.url.pop_back();
  for (auto &header : value.request_headers) {
    header.name = Trim(header.name);
    header.value = Trim(header.value);
  }
  std::erase_if(value.request_headers,
                [](const McpRequestHeader &header) {
                  return header.name.empty();
                });
  return value;
}

std::string McpExtensionToolName(std::string_view extension_id,
                                 std::string_view tool_name) {
  constexpr std::uint64_t modulus = 2147483647ULL;
  std::uint64_t hash = 5381ULL;
  for (const unsigned char character : extension_id)
    hash = (hash * 33ULL + character) % modulus;
  auto value = std::string{"mcpx_"} + Base36(hash) + '_' +
               SafeToolNamePart(tool_name, "tool", 42);
  if (value.size() > 64)
    value.resize(64);
  return value;
}

} // namespace linecode::domain
