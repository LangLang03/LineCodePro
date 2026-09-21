#include <array>
#include "gtest_support.h"
#include <string>
#include <variant>

#include "domain/app_state.h"
#include "domain/skill_hub_route.h"

TEST(skill_hub_route_tests, LegacySuite) {
  using namespace linecode::domain;

  const AppRoute store = AppRoute::skill_store;
  EXPECT_EXPRESSION(store == AppRoute::skill_store);
  EXPECT_EXPRESSION(store.SkillStoreDetailValue() == nullptr);

  const AppRoute detail = AppRoute::SkillStoreDetail("safe-skill");
  EXPECT_EXPRESSION(detail.PageValue() == nullptr);
  EXPECT_EXPRESSION(detail.SkillStoreDetailValue() != nullptr);
  EXPECT_EXPRESSION(detail.SkillStoreDetailValue()->slug == "safe-skill");

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
    EXPECT_EXPRESSION(route.SkillHubSiteValue() != nullptr);
    const auto *value = std::get_if<SkillHubSiteRoute::Destination>(
        &route.SkillHubSiteValue()->target);
    EXPECT_EXPRESSION(value != nullptr);
    EXPECT_EXPRESSION(value->value == destination);
  }

  const AppRoute official =
      AppRoute::SkillHubSkillSite("official", "safe-skill");
  const auto *site = official.SkillHubSiteValue();
  EXPECT_EXPRESSION(site != nullptr);
  const auto *skill = std::get_if<SkillHubSiteRoute::Skill>(&site->target);
  EXPECT_EXPRESSION(skill != nullptr);
  EXPECT_EXPRESSION(skill->name_space == "official");
  EXPECT_EXPRESSION(skill->slug == "safe-skill");
  EXPECT_EXPRESSION(official == AppRoute::SkillHubSkillSite("official", "safe-skill"));
  EXPECT_EXPRESSION(!(official == AppRoute::SkillHubSkillSite("another", "safe-skill")));
}
