#pragma once

#include <array>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::domain {

enum class MemoryScope : std::uint8_t {
  user,
  project,
  environment,
};

struct MemoryScopeSpec final {
  MemoryScope value;
  std::string_view storage_name;
  bool global;
};

inline constexpr std::array memory_scope_catalog{
    MemoryScopeSpec{MemoryScope::user, "user", true},
    MemoryScopeSpec{MemoryScope::project, "project", false},
    MemoryScopeSpec{MemoryScope::environment, "environment", false},
};

[[nodiscard]] const MemoryScopeSpec &
MemoryScopeDefinition(MemoryScope scope) noexcept;
[[nodiscard]] MemoryScope ParseMemoryScope(std::string_view value) noexcept;
[[nodiscard]] std::string NormalizeMemoryContent(std::string_view value);
[[nodiscard]] std::string PreviewMemoryText(std::string_view value,
                                            std::size_t max_characters);

struct MemoryRecord final {
  std::string id;
  MemoryScope scope{MemoryScope::user};
  std::string project_id;
  std::string content;
  std::string source;
  double confidence{1.0};
  std::int64_t created_at{};
  std::int64_t updated_at{};
  std::int64_t last_used_at{};
  std::int64_t use_count{};

  bool operator==(const MemoryRecord &) const = default;
};

struct WorkingMemoryRecord final {
  std::string id;
  std::string project_id;
  std::string content;
  std::string source;
  std::int64_t expires_at{};
  std::int64_t created_at{};
  std::int64_t updated_at{};

  bool operator==(const WorkingMemoryRecord &) const = default;
};

struct ConversationIndexRecord final {
  std::string id;
  std::string project_id;
  std::string conversation_id;
  std::string message_id;
  std::string role;
  std::string text;
  std::string title;
  std::int64_t created_at{};
  std::int64_t updated_at{};

  bool operator==(const ConversationIndexRecord &) const = default;
};

struct MemorySkillRecord final {
  std::string name;
  std::string path;
  std::string description;
  std::int64_t updated_at{};

  bool operator==(const MemorySkillRecord &) const = default;
};

struct MemoryOverview final {
  std::string project_id;
  std::vector<MemoryRecord> long_term;
  std::vector<MemoryRecord> project;
  std::vector<MemoryRecord> environment;
  std::vector<WorkingMemoryRecord> short_term;
  std::vector<ConversationIndexRecord> history;

  bool operator==(const MemoryOverview &) const = default;
};

struct MemoryConversationMessage final {
  std::string id;
  std::string role;
  std::string content;
  std::int64_t timestamp{};

  bool operator==(const MemoryConversationMessage &) const = default;
};

struct MemoryConversationTurn final {
  std::string project_id;
  std::string conversation_id;
  std::string title;
  std::vector<MemoryConversationMessage> messages;
  std::int64_t updated_at{};

  bool operator==(const MemoryConversationTurn &) const = default;
};

} // namespace linecode::domain
