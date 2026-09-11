#include "application/image_generation_tool_registry.h"

#include <algorithm>
#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>

namespace linecode::application {
namespace {

constexpr std::string_view kToolName = "image_generation";

RegisteredTool Descriptor() {
  return RegisteredTool{
      .name = std::string{kToolName},
      .description =
          "Generate an image using the image generation model selected "
          "in tool settings. Returns an inline Markdown image.",
      .parameters_json =
          R"({"type":"object","properties":{"prompt":{"type":"string","description":"Image generation prompt, including subject, style, composition, text requirements, and constraints"},"size":{"type":"string","description":"Image size, default 1024x1024"},"quality":{"type":"string","description":"Optional quality: auto, low, medium, high, standard, or hd"},"background":{"type":"string","description":"Optional background: auto, transparent, or opaque"}},"required":["prompt"]})",
      .allowed_in_read_only = false,
      .permanent_grant_supported = false,
      .category = "image",
  };
}

bool Enabled(const domain::McpExecutionSettings &settings) {
  const auto found = std::ranges::find(
      settings.groups, kToolName,
      [](const domain::McpToolGroupState &group) {
        return std::string_view{group.id};
      });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

ToolRegistryError RegistryError(ToolRegistryErrorCode code,
                                std::string message) {
  return {.code = code, .message = std::move(message)};
}

ToolRegistryError GenerationError(const ImageGenerationError &error) {
  const auto code =
      error.code == ImageGenerationErrorCode::invalid_arguments
          ? ToolRegistryErrorCode::invalid_arguments
          : error.code == ImageGenerationErrorCode::unsupported_protocol ||
                    error.code ==
                        ImageGenerationErrorCode::invalid_configuration
                ? ToolRegistryErrorCode::unavailable
                : ToolRegistryErrorCode::invocation_failed;
  return RegistryError(code, error.message);
}

} // namespace

ImageGenerationToolRegistry::ImageGenerationToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> execution_settings,
    std::shared_ptr<ToolSettingsService> settings,
    std::shared_ptr<ModelStore> models,
    std::shared_ptr<ImageGenerationToolCodec> codec,
    std::shared_ptr<ImageGenerationGateway> gateway)
    : execution_settings_(std::move(execution_settings)),
      settings_(std::move(settings)), models_(std::move(models)),
      codec_(std::move(codec)), gateway_(std::move(gateway)) {
  if (!execution_settings_ || !settings_ || !models_ || !codec_ ||
      !gateway_) {
    throw std::invalid_argument(
        "ImageGenerationToolRegistry requires all dependencies");
  }
}

huxerui::Task<std::expected<void, ToolRegistryError>>
ImageGenerationToolRegistry::Refresh() {
  auto settings = co_await execution_settings_->Load();
  if (!settings) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  tools_.clear();
  if (Enabled(*settings))
    tools_.push_back(Descriptor());
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool>
ImageGenerationToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ImageGenerationToolRegistry::Invoke(std::string name,
                                    std::string arguments_json) {
  if (name != kToolName) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::unknown_tool,
        "Unknown image tool: " + name));
  }
  if (tools_.empty()) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::unavailable,
        "Image generation is disabled for the current execution mode"));
  }

  auto decoded = codec_->DecodeArguments(std::move(arguments_json));
  if (!decoded) {
    co_return std::unexpected(GenerationError(decoded.error()));
  }
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  if (settings->image_generation_model_id.empty()) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::unavailable,
        "Image generation model is not configured"));
  }
  auto model = co_await models_->Find(settings->image_generation_model_id);
  if (!model) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::load_failed, model.error().message));
  }
  if (!model->has_value()) {
    co_return std::unexpected(RegistryError(
        ToolRegistryErrorCode::unavailable,
        "The selected image generation model no longer exists"));
  }

  auto generated =
      co_await gateway_->Generate(std::move(**model), *decoded);
  if (!generated) {
    co_return std::unexpected(GenerationError(generated.error()));
  }
  co_return ToolInvocationResult{
      .content = codec_->EncodeToolResult(*decoded, *generated),
      .error = false,
  };
}

} // namespace linecode::application
