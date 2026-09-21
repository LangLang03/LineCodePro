#include "domain/prompt_renderer.h"

#include <algorithm>
#include <cctype>
#include <ranges>

namespace linecode::domain {
namespace {

std::string_view Trim(std::string_view value) {
  const auto visible = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(value, visible);
  if (begin == value.end())
    return {};
  const auto end = std::ranges::find_if(value | std::views::reverse, visible);
  return {begin, end.base()};
}

} // namespace

std::string RenderPromptTemplate(
    std::string_view source, std::span<const PromptVariable> variables) {
  std::string rendered;
  rendered.reserve(source.size());
  std::size_t cursor{};
  while (cursor < source.size()) {
    const auto opening = source.find("{{", cursor);
    if (opening == std::string_view::npos) {
      rendered.append(source.substr(cursor));
      break;
    }
    const auto closing = source.find("}}", opening + 2U);
    if (closing == std::string_view::npos) {
      rendered.append(source.substr(cursor));
      break;
    }
    rendered.append(source.substr(cursor, opening - cursor));
    const auto name = source.substr(opening + 2U, closing - opening - 2U);
    const auto found = std::ranges::find(variables, name,
                                         &PromptVariable::first);
    if (found == variables.end())
      rendered.append(source.substr(opening, closing + 2U - opening));
    else
      rendered.append(found->second);
    cursor = closing + 2U;
  }
  return std::string{Trim(rendered)};
}

} // namespace linecode::domain
