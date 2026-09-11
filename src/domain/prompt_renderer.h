#pragma once

#include <span>
#include <string>
#include <string_view>
#include <utility>

namespace linecode::domain {

using PromptVariable = std::pair<std::string_view, std::string_view>;

// Performs the same single-pass substitution as the legacy StringTemplate:
// placeholders introduced by replacement values remain literal data.
[[nodiscard]] std::string
RenderPromptTemplate(std::string_view source,
                     std::span<const PromptVariable> variables);

} // namespace linecode::domain
