#pragma once

#include <chrono>
#include <cstdint>
#include <expected>
#include <string>
#include <string_view>

namespace linecode::domain {

enum class SkillLocation {
  app,
  project,
  ssh,
};

struct SkillRecord final {
  std::string id;
  std::string name;
  std::string description;
  std::string root_path;
  std::string skill_markdown_path;
  SkillLocation location{SkillLocation::app};
  bool enabled{true};
  std::int64_t discovered_at{};
  std::int64_t updated_at{};

  bool operator==(const SkillRecord &) const = default;
};

struct SkillMetadata final {
  std::string name;
  std::string description;

  bool operator==(const SkillMetadata &) const = default;
};

struct SkillValidationError final {
  std::string message;

  bool operator==(const SkillValidationError &) const = default;
};

[[nodiscard]] SkillLocation ParseSkillLocation(std::string_view value) noexcept;
[[nodiscard]] std::string_view SerializeSkillLocation(
    SkillLocation value) noexcept;
[[nodiscard]] std::string_view SkillLocationLabel(
    SkillLocation value) noexcept;
[[nodiscard]] SkillMetadata ParseSkillMetadata(std::string_view markdown,
                                               std::string_view fallback_name);
[[nodiscard]] std::expected<std::string, SkillValidationError>
BuildSkillMarkdown(std::string_view name, std::string_view description,
                   std::string_view body);
[[nodiscard]] std::string SanitizeSkillDirectoryName(std::string_view value,
                                                     std::int64_t fallback_id);
[[nodiscard]] bool IsSkillMarkdownName(std::string_view name) noexcept;

} // namespace linecode::domain
