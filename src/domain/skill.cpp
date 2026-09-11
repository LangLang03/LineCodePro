#include "domain/skill.h"

#include <algorithm>
#include <cctype>
#include <ranges>
#include <string>

namespace linecode::domain {
namespace {

std::string Trim(std::string_view value) {
  const auto first = std::ranges::find_if_not(value, [](const unsigned char c) {
    return std::isspace(c) != 0;
  });
  const auto last = std::ranges::find_if_not(value | std::views::reverse,
                                              [](const unsigned char c) {
                                                return std::isspace(c) != 0;
                                              })
                        .base();
  return first < last ? std::string(first, last) : std::string{};
}

bool EqualsAsciiInsensitive(std::string_view left,
                            std::string_view right) noexcept {
  return left.size() == right.size() &&
         std::ranges::equal(left, right, [](const unsigned char a,
                                            const unsigned char b) {
           return std::tolower(a) == std::tolower(b);
         });
}

std::string Unquote(std::string value) {
  if (value.size() >= 2 &&
      ((value.front() == '\'' && value.back() == '\'') ||
       (value.front() == '"' && value.back() == '"'))) {
    value = value.substr(1, value.size() - 2);
  }
  return Trim(value);
}

std::string FrontmatterValue(std::string_view markdown, std::string_view key) {
  std::size_t start{};
  bool first_line{true};
  bool in_frontmatter{};
  while (start <= markdown.size()) {
    const auto newline = markdown.find('\n', start);
    auto line = markdown.substr(start, newline == std::string_view::npos
                                           ? markdown.size() - start
                                           : newline - start);
    if (!line.empty() && line.back() == '\r')
      line.remove_suffix(1);
    const auto trimmed = Trim(line);
    if (first_line) {
      first_line = false;
      in_frontmatter = trimmed == "---";
      if (!in_frontmatter)
        return {};
    } else if (trimmed == "---") {
      return {};
    } else {
      const auto colon = trimmed.find(':');
      if (colon != std::string::npos && colon > 0 &&
          EqualsAsciiInsensitive(Trim(std::string_view{trimmed}.substr(0, colon)),
                                 key)) {
        return Unquote(Trim(std::string_view{trimmed}.substr(colon + 1)));
      }
    }
    if (newline == std::string_view::npos)
      break;
    start = newline + 1;
  }
  return {};
}

std::string MarkdownTitle(std::string_view markdown) {
  std::size_t start{};
  while (start <= markdown.size()) {
    const auto newline = markdown.find('\n', start);
    auto line = Trim(markdown.substr(start, newline == std::string_view::npos
                                               ? markdown.size() - start
                                               : newline - start));
    if (line.starts_with("# "))
      return Trim(std::string_view{line}.substr(2));
    if (newline == std::string_view::npos)
      break;
    start = newline + 1;
  }
  return {};
}

std::string DescriptionLine(std::string_view markdown) {
  std::size_t start{};
  bool first_line{true};
  bool in_frontmatter{};
  while (start <= markdown.size()) {
    const auto newline = markdown.find('\n', start);
    auto line = Trim(markdown.substr(start, newline == std::string_view::npos
                                               ? markdown.size() - start
                                               : newline - start));
    if (first_line) {
      first_line = false;
      in_frontmatter = line == "---";
      if (in_frontmatter) {
        if (newline == std::string_view::npos)
          break;
        start = newline + 1;
        continue;
      }
    } else if (in_frontmatter) {
      if (line == "---")
        in_frontmatter = false;
      if (newline == std::string_view::npos)
        break;
      start = newline + 1;
      continue;
    }
    if (!line.empty() && line != "---" && !line.starts_with('#')) {
      if (line.size() >= 12 && EqualsAsciiInsensitive(
                                   std::string_view{line}.substr(0, 12),
                                   "description:"))
        return Trim(std::string_view{line}.substr(12));
      return line;
    }
    if (newline == std::string_view::npos)
      break;
    start = newline + 1;
  }
  return {};
}

std::string EscapeYamlScalar(std::string_view value) {
  std::string escaped{"\""};
  escaped.reserve(value.size() + 2);
  for (const char c : value) {
    switch (c) {
    case '\\':
      escaped += "\\\\";
      break;
    case '"':
      escaped += "\\\"";
      break;
    case '\n':
      escaped += "\\n";
      break;
    case '\r':
      escaped += "\\r";
      break;
    default:
      escaped.push_back(c);
      break;
    }
  }
  escaped.push_back('"');
  return escaped;
}

} // namespace

SkillLocation ParseSkillLocation(const std::string_view value) noexcept {
  if (value == "project")
    return SkillLocation::project;
  if (value == "ssh")
    return SkillLocation::ssh;
  return SkillLocation::app;
}

std::string_view SerializeSkillLocation(const SkillLocation value) noexcept {
  switch (value) {
  case SkillLocation::project:
    return "project";
  case SkillLocation::ssh:
    return "ssh";
  case SkillLocation::app:
    return "app";
  }
  return "app";
}

std::string_view SkillLocationLabel(const SkillLocation value) noexcept {
  switch (value) {
  case SkillLocation::project:
    return "Current workspace .linecode/skills";
  case SkillLocation::ssh:
    return "SSH ~/.linecode/skills";
  case SkillLocation::app:
    return "App .linecode/skills";
  }
  return "App .linecode/skills";
}

SkillMetadata ParseSkillMetadata(const std::string_view markdown,
                                 const std::string_view fallback_name) {
  auto name = FrontmatterValue(markdown, "name");
  if (name.empty())
    name = MarkdownTitle(markdown);
  if (name.empty())
    name = Trim(fallback_name);
  if (name.empty())
    name = "Skill";
  auto description = FrontmatterValue(markdown, "description");
  if (description.empty())
    description = DescriptionLine(markdown);
  return {.name = std::move(name), .description = std::move(description)};
}

std::expected<std::string, SkillValidationError>
BuildSkillMarkdown(const std::string_view raw_name,
                   const std::string_view raw_description,
                   const std::string_view raw_body) {
  const auto name = Trim(raw_name);
  if (name.empty())
    return std::unexpected(SkillValidationError{"Skill name is required"});
  auto body = Trim(raw_body);
  if (body.empty()) {
    body = "# " + name + "\n\n## 触发条件\n- 当任务与 " + name +
           " 相关时使用。\n\n## 步骤\n- 阅读当前任务和项目上下文。\n"
           "- 按项目既有规范执行。\n- 完成后给出验证结果。";
  }
  return "---\nname: " + EscapeYamlScalar(name) +
         "\ndescription: " + EscapeYamlScalar(Trim(raw_description)) +
         "\n---\n\n" + body + "\n";
}

std::string SanitizeSkillDirectoryName(const std::string_view value,
                                       const std::int64_t fallback_id) {
  std::string result;
  result.reserve(std::min<std::size_t>(value.size(), 96));
  for (const unsigned char c : value) {
    if (result.size() >= 96)
      break;
    if (std::isalnum(c) != 0 || c == '.' || c == '_' || c == '-')
      result.push_back(static_cast<char>(c));
    else if (std::isspace(c) != 0)
      result.push_back('-');
  }
  while (!result.empty() && result.front() == '-')
    result.erase(result.begin());
  while (!result.empty() && result.back() == '-')
    result.pop_back();
  return result.empty() ? "skill_" + std::to_string(fallback_id) : result;
}

bool IsSkillMarkdownName(const std::string_view name) noexcept {
  return EqualsAsciiInsensitive(name, "SKILL.md");
}

} // namespace linecode::domain
