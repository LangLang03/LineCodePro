#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/skill_services.h"

namespace linecode::infrastructure {

class HuxSkillHubSessionGateway final
    : public application::SkillHubSessionGateway {
public:
  explicit HuxSkillHubSessionGateway(
      std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<
      application::SkillResult<domain::SkillHubSession>>
  CurrentSession(std::string cookie) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  Publish(std::string cookie,
          application::SkillHubPublishRequest request) override;
  [[nodiscard]] huxerui::Task<
      application::SkillResult<domain::SkillHubComment>>
  PostComment(std::string cookie, std::string slug, std::string name_space,
              std::string content,
              std::optional<std::int64_t> reply_to) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  SetCommentLiked(std::string cookie, std::string slug,
                  std::int64_t comment_id, std::string name_space,
                  bool liked) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  DeleteComment(std::string cookie, std::string slug,
                std::int64_t comment_id, std::string name_space) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<bool>>
  Starred(std::string cookie, std::string slug,
          std::string name_space) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  SetStarred(std::string cookie, std::string slug, std::string name_space,
             bool starred) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  Logout(std::string cookie) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
