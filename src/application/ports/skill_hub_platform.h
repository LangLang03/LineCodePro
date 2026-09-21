#pragma once

#include <functional>
#include <optional>
#include <string>

namespace linecode::application {

struct SkillHubCookieResult final {
  std::string cookie;
  std::string error;

  [[nodiscard]] bool Succeeded() const noexcept { return error.empty(); }
};

// Narrow host boundary used by the C++ SkillHub feature. Authentication and
// API behavior remain in C++; hosts only expose their cookie store and native
// cookie capability.
class SkillHubPlatformService {
public:
  using CookieCompletion = std::function<void(SkillHubCookieResult)>;
  using ReadingScaleCompletion = std::function<void(std::optional<float>)>;

  virtual ~SkillHubPlatformService() = default;

  virtual void ReadSessionCookie(CookieCompletion completion) = 0;
  virtual void ClearSessionCookies() = 0;
  virtual void
  ReadLegacyMarkdownTextScale(ReadingScaleCompletion completion) = 0;
};

} // namespace linecode::application
