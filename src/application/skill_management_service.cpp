#include "application/skill_management_service.h"

#include <stdexcept>
#include <utility>

#include <huxerui/task.h>

namespace linecode::application {

SkillManagementService::SkillManagementService(
    std::shared_ptr<SkillRepository> repository,
    std::shared_ptr<SkillPackageGateway> packages)
    : repository_(std::move(repository)), packages_(std::move(packages)) {
  if (!repository_ || !packages_)
    throw std::invalid_argument(
        "SkillManagementService requires repository and package gateway");
}

huxerui::Task<SkillResult<domain::SkillRecord>>
SkillManagementService::InstallGitHub(SkillRoots roots,
                                      const domain::SkillLocation location,
                                      std::string url) const {
  auto package =
      co_await packages_->FetchGitHub(std::move(roots), location,
                                     std::move(url));
  if (!package)
    co_return std::unexpected(std::move(package.error()));
  co_return co_await repository_->Install(std::move(*package));
}

huxerui::Task<SkillResult<domain::SkillRecord>>
SkillManagementService::InstallSkillHub(SkillRoots roots,
                                        const domain::SkillLocation location,
                                        std::string slug,
                                        std::string version) const {
  auto package = co_await packages_->FetchSkillHub(
      std::move(roots), location, std::move(slug), std::move(version));
  if (!package)
    co_return std::unexpected(std::move(package.error()));
  co_return co_await repository_->Install(std::move(*package));
}

} // namespace linecode::application
