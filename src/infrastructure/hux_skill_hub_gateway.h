#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/skill_services.h"

namespace linecode::infrastructure {

class HuxSkillHubGateway final : public application::SkillHubCatalog,
                                 public application::SkillPackageGateway {
public:
  explicit HuxSkillHubGateway(std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<
      application::SkillResult<domain::SkillHubPage>>
  List(application::SkillHubListQuery query) override;
  [[nodiscard]] huxerui::Task<
      application::SkillResult<domain::SkillHubDetail>>
  Detail(std::string slug) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<std::string>>
  FileContent(std::string slug, std::string version,
              std::string path) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<huxerui::Bytes>>
  Download(std::string slug, std::string version) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<huxerui::Bytes>>
  Icon(std::string url) override;
  [[nodiscard]] huxerui::Task<
      application::SkillResult<std::vector<domain::SkillHubComment>>>
  CommentReplies(std::string slug, std::int64_t comment_id,
                 std::string name_space) override;

  [[nodiscard]] huxerui::Task<
      application::SkillResult<application::SkillInstallRequest>>
  FetchGitHub(application::SkillRoots roots,
              domain::SkillLocation location, std::string url) override;
  [[nodiscard]] huxerui::Task<
      application::SkillResult<application::SkillInstallRequest>>
  FetchSkillHub(application::SkillRoots roots,
                domain::SkillLocation location, std::string slug,
                std::string version) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
