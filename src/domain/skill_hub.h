#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace linecode::domain {

struct SkillHubSummary {
  std::string slug;
  std::string name;
  std::string description;
  std::string owner;
  std::string category;
  std::string source;
  std::string version;
  std::string icon_url;
  std::int64_t downloads{};
  std::int64_t stars{};
  std::int64_t updated_at{};
  bool verified{};
  bool requires_api_key{};
  std::vector<std::string> subcategories;

  bool operator==(const SkillHubSummary &) const = default;
};

struct SkillHubFileEntry final {
  std::string path;
  std::string sha256;
  std::int64_t size{};

  bool operator==(const SkillHubFileEntry &) const = default;
};

struct SkillHubComment final {
  std::int64_t id{};
  std::int64_t user_id{};
  std::int64_t parent_id{};
  std::string author;
  std::string handle;
  std::string avatar_url;
  std::string content;
  std::int64_t created_at{};
  std::int64_t like_count{};
  std::int64_t reply_count{};
  bool liked{};
  std::string status;
  std::vector<std::string> image_urls;
  std::vector<SkillHubComment> replies;

  bool operator==(const SkillHubComment &) const = default;
};

struct SkillHubVersion final {
  std::string version;
  std::string changelog;
  std::int64_t created_at{};
  std::string security_status;
  std::string security_status_text;

  bool operator==(const SkillHubVersion &) const = default;
};

struct SkillHubEvaluation final {
  std::string status;
  double score{};
  std::string summary;
  std::vector<std::string> highlights;
  std::vector<std::string> suggestions;

  bool operator==(const SkillHubEvaluation &) const = default;
};

struct SkillHubTestCase final {
  std::string title;
  std::string prompt;
  std::string expected;

  bool operator==(const SkillHubTestCase &) const = default;
};

struct SkillHubDetail final : SkillHubSummary {
  std::string canonical_name;
  std::string namespace_handle;
  std::string publisher;
  std::string security_status;
  std::string security_status_text;
  std::string markdown;
  std::vector<std::string> tags;
  std::vector<SkillHubFileEntry> files;
  std::vector<SkillHubComment> comments;
  std::vector<SkillHubVersion> versions;
  SkillHubEvaluation evaluation;
  std::vector<SkillHubTestCase> test_cases;

  [[nodiscard]] bool HasScripts() const noexcept;
  bool operator==(const SkillHubDetail &) const = default;
};

struct SkillHubPage final {
  std::vector<SkillHubSummary> skills;
  std::int64_t total{};

  bool operator==(const SkillHubPage &) const = default;
};

struct SkillHubAccount final {
  std::string display_name;
  std::string handle;
  std::string avatar_url;

  bool operator==(const SkillHubAccount &) const = default;
};

struct SkillHubSession final {
  bool authenticated{};
  SkillHubAccount account;

  bool operator==(const SkillHubSession &) const = default;
};

} // namespace linecode::domain
