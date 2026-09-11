#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::application {

struct ToolResultDisplayProjection final {
  std::string model_content;
  std::string display_markdown;
  bool hide_success_card{};

  bool operator==(const ToolResultDisplayProjection &) const = default;
};

class ToolResultDisplayPolicy {
public:
  virtual ~ToolResultDisplayPolicy() = default;

  [[nodiscard]] virtual ToolResultDisplayProjection
  Project(std::string_view content, bool error) const = 0;
};

class ToolResultDisplayProjector {
public:
  virtual ~ToolResultDisplayProjector() = default;

  [[nodiscard]] virtual ToolResultDisplayProjection
  Project(std::string_view tool_name, std::string_view content,
          bool error) const = 0;
};

struct ToolResultDisplayPolicyRegistration final {
  std::string tool_name;
  std::shared_ptr<const ToolResultDisplayPolicy> policy;
};

class ToolResultDisplayPolicyRegistry final
    : public ToolResultDisplayProjector {
public:
  explicit ToolResultDisplayPolicyRegistry(
      std::vector<ToolResultDisplayPolicyRegistration> policies);

  [[nodiscard]] ToolResultDisplayProjection
  Project(std::string_view tool_name, std::string_view content,
          bool error) const override;

private:
  std::vector<ToolResultDisplayPolicyRegistration> policies_;
};

[[nodiscard]] std::shared_ptr<const ToolResultDisplayProjector>
DefaultToolResultDisplayProjector();

} // namespace linecode::application
