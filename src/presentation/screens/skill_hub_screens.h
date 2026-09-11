#pragma once

#include <memory>
#include <string>

#include <huxerui/view.h>

#include "application/ports/skill_services.h"
#include "application/ports/skill_hub_platform.h"
#include "application/ports/share_text.h"
#include "application/skill_management_service.h"
#include "application/skill_hub_reading_settings.h"
#include "application/skill_repository.h"
#include "domain/skill_hub_route.h"

namespace linecode::presentation {

struct SkillHubScreenServices final {
  std::shared_ptr<application::SkillHubCatalog> catalog;
  std::shared_ptr<application::SkillHubSessionGateway> session;
  std::shared_ptr<application::SkillRepository> repository;
  std::shared_ptr<application::SkillManagementService> management;
  std::shared_ptr<application::SkillHubPlatformService> platform;
  std::shared_ptr<application::ShareTextService> share;
  std::shared_ptr<application::SkillHubReadingSettings> reading;
  application::SkillRoots roots;
};

[[huxerui::composable]] huxerui::View
SkillStoreScreen(const SkillHubScreenServices &services);

[[huxerui::composable]] huxerui::View
SkillStoreDetailScreen(const SkillHubScreenServices &services,
                       const domain::SkillStoreDetailRoute &route);

[[huxerui::composable]] huxerui::View SkillHubLoginScreen(
    const SkillHubScreenServices &services);

[[huxerui::composable]] huxerui::View SkillHubCenterScreen();

[[huxerui::composable]] huxerui::View
SkillHubWebScreen(const domain::SkillHubSiteRoute &route);

[[huxerui::composable]] huxerui::View
SkillHubPublishScreen(const SkillHubScreenServices &services);

} // namespace linecode::presentation
