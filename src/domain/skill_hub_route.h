#pragma once

#include <cstdint>
#include <string>
#include <variant>

namespace linecode::domain {

enum class SkillHubDestination : std::uint8_t {
  account,
  settings,
  verify,
  tokens,
  stars,
  following,
  notifications,
  creator,
  publish,
  skillsets,
  mcp,
  skill_hunt,
  contest,
  enterprises,
  enterprise_dashboard,
  enterprise_publish,
  merchant,
  admin,
  admin_reviews,
};

struct SkillStoreDetailRoute final {
  std::string slug;

  bool operator==(const SkillStoreDetailRoute &) const = default;
};

struct SkillHubSiteRoute final {
  struct Destination final {
    SkillHubDestination value{SkillHubDestination::account};
    bool operator==(const Destination &) const = default;
  };

  struct Skill final {
    std::string name_space;
    std::string slug;
    bool operator==(const Skill &) const = default;
  };

  std::variant<Destination, Skill> target;

  bool operator==(const SkillHubSiteRoute &) const = default;
};

} // namespace linecode::domain
