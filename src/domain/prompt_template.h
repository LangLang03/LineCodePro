#pragma once

#include <cstdint>
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

// A built-in default this app has shipped before, identified by the SHA-256 of
// its text. An upgrade may replace a stored override that still matches one of
// these: the user never actually edited it, so keeping it would pin the old
// wording forever. Genuinely edited text is never touched.
struct SupersededPromptDefault final {
  std::string id;
  std::string sha256_hex;
};

[[nodiscard]] std::vector<SupersededPromptDefault> SupersededPromptDefaults();

// Bumped whenever the built-in prompt text changes in a way that should reach
// existing installs. Stored under `@linecode_prompt_template_revision` so the
// migration runs once per revision.
inline constexpr std::int64_t kPromptTemplateRevision = 1;

} // namespace linecode::domain
