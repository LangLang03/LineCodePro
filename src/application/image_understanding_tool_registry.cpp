#include "application/image_understanding_tool_registry.h"

#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>

namespace linecode::application {
namespace {

constexpr std::string_view kToolName = "image_understanding";
constexpr std::string_view kPromptTemplateId = "imageUnderstandingToolSystem";
constexpr std::string_view kFallbackSystemPrompt =
    "You are LineCode's image understanding tool. Analyze images based on "
    "the user's prompt, returning only content relevant to the image and the "
    "prompt. Do not mention tool calls, base64, or file paths; state "
    "uncertainty when unsure.";

RegisteredTool Descriptor() {
  return RegisteredTool{
      .name = std::string{kToolName},
      .description =
          "Read an image in the current local, SSH, or terminal-provider "
          "workspace and ask the vision model selected in tool settings to "
          "analyze it.",
      .parameters_json =
          R"({"type":"object","properties":{"path":{"type":"string","description":"Image path relative to the current workspace, or a remote absolute path accepted by the active workspace"},"prompt":{"type":"string","description":"Question or analysis request for the vision model"}},"required":["path","prompt"]})",
      .allowed_in_read_only = true,
      .agent_category = AgentToolCategory::read,
      .category = "image",
      .presentation =
          {.english_name = "Understand image",
           .english_description =
               "Analyze a workspace image with the configured vision model.",
           .chinese_name = "理解图像",
           .chinese_description = "使用已配置的视觉模型分析工作区图像。"},
  };
}

bool Enabled(const domain::McpExecutionSettings &settings) {
  const auto found = std::ranges::find(
      settings.groups, kToolName, [](const domain::McpToolGroupState &group) {
        return std::string_view{group.id};
      });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

ToolRegistryError Adapt(const ImageUnderstandingError &error) {
  using enum ImageUnderstandingErrorCode;
  switch (error.code) {
  case invalid_arguments:
    return Error(ToolRegistryErrorCode::invalid_arguments, error.message);
  case unavailable:
  case unsupported_protocol:
  case invalid_configuration:
  case not_found:
  case too_large:
  case unsupported_format:
    return Error(ToolRegistryErrorCode::unavailable, error.message);
  case transport:
  case http_status:
  case decode:
    return Error(ToolRegistryErrorCode::invocation_failed, error.message);
  }
  std::unreachable();
}

std::string
SystemPrompt(const std::vector<domain::PromptTemplateItem> &templates) {
  const auto found = std::ranges::find(
      templates, kPromptTemplateId, [](const domain::PromptTemplateItem &item) {
        return std::string_view{item.definition.id};
      });
  return found == templates.end() || found->current_text.empty()
             ? std::string{kFallbackSystemPrompt}
             : found->current_text;
}

} // namespace

ImageUnderstandingToolRegistry::ImageUnderstandingToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> execution_settings,
    std::shared_ptr<ToolSettingsService> settings,
    std::shared_ptr<ModelStore> models,
    std::shared_ptr<PromptTemplateRepository> prompt_templates,
    std::shared_ptr<WorkspaceImageReader> images,
    std::shared_ptr<ImageUnderstandingToolCodec> codec,
    std::shared_ptr<ImageUnderstandingGateway> gateway)
    : execution_settings_(std::move(execution_settings)),
      settings_(std::move(settings)), models_(std::move(models)),
      prompt_templates_(std::move(prompt_templates)),
      images_(std::move(images)), codec_(std::move(codec)),
      gateway_(std::move(gateway)) {
  if (!execution_settings_ || !settings_ || !models_ || !prompt_templates_ ||
      !images_ || !codec_ || !gateway_) {
    throw std::invalid_argument(
        "ImageUnderstandingToolRegistry requires all dependencies");
  }
}

huxerui::Task<std::expected<void, ToolRegistryError>>
ImageUnderstandingToolRegistry::Refresh() {
  auto settings = co_await execution_settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  tools_.clear();
  if (Enabled(*settings))
    tools_.push_back(Descriptor());
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool>
ImageUnderstandingToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ImageUnderstandingToolRegistry::Invoke(std::string name,
                                       std::string arguments_json) {
  if (name != kToolName) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown image tool: " + name));
  }
  if (tools_.empty()) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::unavailable,
        "Image understanding is disabled for the current execution mode"));
  }
  auto request = codec_->DecodeArguments(std::move(arguments_json));
  if (!request)
    co_return std::unexpected(Adapt(request.error()));

  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  if (settings->image_understanding_model_id.empty()) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::unavailable,
              "Image understanding model is not configured"));
  }
  auto model = co_await models_->Find(settings->image_understanding_model_id);
  if (!model) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, model.error().message));
  }
  if (!model->has_value()) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::unavailable,
              "The selected image understanding model no longer exists"));
  }

  auto read = co_await images_->Read(request->path);
  if (!read)
    co_return std::unexpected(Adapt(read.error()));
  auto image = codec_->ValidateImage(std::move(read->resolved_path),
                                     std::move(read->bytes));
  if (!image)
    co_return std::unexpected(Adapt(image.error()));
  auto templates = co_await prompt_templates_->Load();
  if (!templates) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, templates.error().message));
  }
  auto analyzed =
      co_await gateway_->Analyze(std::move(**model), SystemPrompt(*templates),
                                 *request, std::move(*image));
  if (!analyzed)
    co_return std::unexpected(Adapt(analyzed.error()));
  if (analyzed->empty())
    *analyzed = "The vision model returned no image content.";
  co_return ToolInvocationResult{.content = std::move(*analyzed),
                                 .error = false};
}

} // namespace linecode::application
