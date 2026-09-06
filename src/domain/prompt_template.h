#pragma once

#include <string>
#include <vector>

namespace linecode::domain {

struct PromptTemplateDefinition final {
  std::string id;
  std::string source;
  std::vector<std::string> variables;
  std::string default_text;

  bool operator==(const PromptTemplateDefinition &) const = default;
};

struct PromptTemplateItem final {
  PromptTemplateDefinition definition;
  std::string current_text;
  bool customized{};

  bool operator==(const PromptTemplateItem &) const = default;
};

[[nodiscard]] std::vector<PromptTemplateDefinition> BuiltInPromptTemplates();

} // namespace linecode::domain
