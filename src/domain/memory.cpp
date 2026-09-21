#include "domain/memory.h"

#include <algorithm>
#include <cctype>
#include <ranges>

namespace linecode::domain {
namespace {

std::string Trim(std::string_view value) {
  const auto whitespace = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && whitespace(value.front()))
    value.remove_prefix(1);
  while (!value.empty() && whitespace(value.back()))
    value.remove_suffix(1);
  return std::string{value};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(value, std::back_inserter(lowered),
                         [](unsigned char byte) {
                           return static_cast<char>(std::tolower(byte));
                         });
  return lowered;
}

std::size_t Utf8CharacterCount(std::string_view value) noexcept {
  return static_cast<std::size_t>(std::ranges::count_if(
      value, [](unsigned char byte) { return (byte & 0xC0U) != 0x80U; }));
}

std::size_t Utf8PrefixBytes(std::string_view value,
                            std::size_t characters) noexcept {
  std::size_t byte_index{};
  std::size_t count{};
  while (byte_index < value.size() && count < characters) {
    ++byte_index;
    while (byte_index < value.size() &&
           (static_cast<unsigned char>(value[byte_index]) & 0xC0U) == 0x80U) {
      ++byte_index;
    }
    ++count;
  }
  return byte_index;
}

} // namespace

const MemoryScopeSpec &MemoryScopeDefinition(MemoryScope scope) noexcept {
  const auto found = std::ranges::find(memory_scope_catalog, scope,
                                       &MemoryScopeSpec::value);
  return found == memory_scope_catalog.end() ? memory_scope_catalog.front()
                                              : *found;
}

MemoryScope ParseMemoryScope(std::string_view value) noexcept {
  const auto normalized = Lower(Trim(value));
  const auto found = std::ranges::find(memory_scope_catalog, normalized,
                                       &MemoryScopeSpec::storage_name);
  return found == memory_scope_catalog.end() ? MemoryScope::user
                                              : found->value;
}

std::string NormalizeMemoryContent(std::string_view value) {
  return Trim(value);
}

std::string PreviewMemoryText(std::string_view value,
                              std::size_t max_characters) {
  std::string compact;
  compact.reserve(value.size());
  bool spacing = false;
  for (const char character : value) {
    const bool whitespace = character == '\r' || character == '\n' ||
                            character == ' ' || character == '\t';
    if (whitespace) {
      spacing = !compact.empty();
      continue;
    }
    if (spacing) {
      compact.push_back(' ');
      spacing = false;
    }
    compact.push_back(character);
  }
  compact = Trim(compact);
  if (Utf8CharacterCount(compact) <= max_characters)
    return compact;
  if (max_characters <= 3U)
    return std::string(max_characters, '.');
  compact.resize(Utf8PrefixBytes(compact, max_characters - 3U));
  compact += "...";
  return compact;
}

} // namespace linecode::domain
