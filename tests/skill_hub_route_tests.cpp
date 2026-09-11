#include <array>
#include <cassert>
#include <string>
#include <variant>

#include "domain/app_state.h"
#include "domain/skill_hub_route.h"

int main() {
  using namespace linecode::domain;

  const AppRoute store = AppRoute::skill_store;
  assert(store == AppRoute::skill_store);
  assert(store.SkillStoreDetailValue() == nullptr);

  const AppRoute detail = AppRoute::SkillStoreDetail("safe-skill");
  assert(detail.PageValue() == nullptr);
  assert(detail.SkillStoreDetailValue() != nullptr);
  assert(detail.SkillStoreDetailValue()->slug == "safe-skill");

  constexpr std::array destinations{
      SkillHubDestination::account,
      SkillHubDestination::settings,
      SkillHubDestination::verify,
      SkillHubDestination::tokens,
      SkillHubDestination::stars,
      SkillHubDestination::following,
      SkillHubDestination::notifications,
      SkillHubDestination::creator,
      SkillHubDestination::publish,
      SkillHubDestination::skillsets,
      SkillHubDestination::mcp,
      SkillHubDestination::skill_hunt,
      SkillHubDestination::contest,
      SkillHubDestination::enterprises,
      SkillHubDestination::enterprise_dashboard,
      SkillHubDestination::enterprise_publish,
      SkillHubDestination::merchant,
      SkillHubDestination::admin,
      SkillHubDestination::admin_reviews,
  };
  for (const auto destination : destinations) {
    const AppRoute route = AppRoute::SkillHubSite(destination);
    assert(route.SkillHubSiteValue() != nullptr);
    const auto *value = std::get_if<SkillHubSiteRoute::Destination>(
        &route.SkillHubSiteValue()->target);
    assert(value != nullptr);
    assert(value->value == destination);
  }

  const AppRoute official =
      AppRoute::SkillHubSkillSite("official", "safe-skill");
  const auto *site = official.SkillHubSiteValue();
  assert(site != nullptr);
  const auto *skill = std::get_if<SkillHubSiteRoute::Skill>(&site->target);
  assert(skill != nullptr);
  assert(skill->name_space == "official");
  assert(skill->slug == "safe-skill");
  assert(official == AppRoute::SkillHubSkillSite("official", "safe-skill"));
  assert(!(official == AppRoute::SkillHubSkillSite("another", "safe-skill")));
}
