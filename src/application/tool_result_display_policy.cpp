#include "application/tool_result_display_policy.h"

#include <algorithm>
#include <stdexcept>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

constexpr std::string_view kImageGenerationTool = "image_generation";
constexpr std::string_view kInlineImagePrefix = "data:image/";
constexpr std::string_view kInlineImageReplacement = "linecode-inline-image";
constexpr std::string_view kImageGeneratedFallback =
    "Image generated and displayed in conversation.";

[[nodiscard]] std::string Trimmed(std::string_view text) {
  const auto first = text.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos)
    return {};
  const auto last = text.find_last_not_of(" \t\r\n");
  return std::string{text.substr(first, last - first + 1U)};
}

[[nodiscard]] std::string StripInlineDataImages(std::string_view text) {
  std::string result;
  result.reserve(text.size());
  std::size_t cursor{};
  while (cursor < text.size()) {
    const auto start = text.find(kInlineImagePrefix, cursor);
    if (start == std::string_view::npos) {
      result.append(text.substr(cursor));
      break;
    }
    result.append(text.substr(cursor, start - cursor));
    result.append(kInlineImageReplacement);
    auto end = start;
    while (end < text.size()) {
      const auto byte = static_cast<unsigned char>(text[end]);
      if (text[end] == ')' || text[end] == '"' || text[end] == '\'' ||
          byte <= 0x20U)
        break;
      ++end;
    }
    cursor = end;
  }
  return result;
}

class ImageGenerationDisplayPolicy final : public ToolResultDisplayPolicy {
public:
  [[nodiscard]] ToolResultDisplayProjection
  Project(std::string_view content, bool error) const override {
    ToolResultDisplayProjection projection{
        .model_content = StripInlineDataImages(content),
        .display_markdown = {},
        .hide_success_card = !error && !Trimmed(content).empty(),
    };
    if (error || Trimmed(content).empty())
      return projection;

    namespace json = infrastructure::archive_json;
    auto parsed = json::Parse(content);
    const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
    if (object == nullptr) {
      projection.model_content = std::string{kImageGeneratedFallback};
      return projection;
    }
    const auto *marker = json::Find(*object, "linecode_image_generation");
    if (marker == nullptr || !std::holds_alternative<bool>(*marker) ||
        !std::get<bool>(*marker)) {
      return projection;
    }
    if (const auto *markdown =
            json::AsString(json::Find(*object, "display_markdown"))) {
      projection.display_markdown = Trimmed(*markdown);
    }
    if (const auto *model =
            json::AsString(json::Find(*object, "model_content"));
        model != nullptr && !Trimmed(*model).empty()) {
      projection.model_content = StripInlineDataImages(Trimmed(*model));
    } else {
      projection.model_content = std::string{kImageGeneratedFallback};
    }
    return projection;
  }
};

} // namespace

ToolResultDisplayPolicyRegistry::ToolResultDisplayPolicyRegistry(
    std::vector<ToolResultDisplayPolicyRegistration> policies)
    : policies_(std::move(policies)) {
  if (std::ranges::any_of(policies_, [](const auto &registration) {
        return registration.tool_name.empty() || !registration.policy;
      })) {
    throw std::invalid_argument(
        "Tool result display policies require a name and strategy");
  }
}

ToolResultDisplayProjection ToolResultDisplayPolicyRegistry::Project(
    std::string_view tool_name, std::string_view content, bool error) const {
  const auto found = std::ranges::find(policies_, tool_name,
                                       &ToolResultDisplayPolicyRegistration::tool_name);
  if (found != policies_.end())
    return found->policy->Project(content, error);
  return {.model_content = StripInlineDataImages(content),
          .display_markdown = {},
          .hide_success_card = false};
}

std::shared_ptr<const ToolResultDisplayProjector>
DefaultToolResultDisplayProjector() {
  static const auto projector =
      std::make_shared<const ToolResultDisplayPolicyRegistry>(
          std::vector<ToolResultDisplayPolicyRegistration>{
              {.tool_name = std::string{kImageGenerationTool},
               .policy = std::make_shared<ImageGenerationDisplayPolicy>()},
          });
  return projector;
}

} // namespace linecode::application
