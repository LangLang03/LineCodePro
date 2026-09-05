#include "application/model_form_service.h"

#include <charconv>
#include <cmath>
#include <cstdlib>
#include <limits>
#include <string>
#include <utility>

namespace linecode::application {
namespace {

std::string Trim(std::string_view text) {
  const auto first = text.find_first_not_of(" \t\n\r");
  if (first == std::string_view::npos) {
    return {};
  }
  const auto last = text.find_last_not_of(" \t\n\r");
  return std::string{text.substr(first, last - first + 1)};
}

std::optional<int> ParseToolLimit(std::string_view text) noexcept {
  const auto trimmed = Trim(text);
  if (trimmed.empty()) {
    return std::nullopt;
  }
  int result{};
  const auto [end, error] =
      std::from_chars(trimmed.data(), trimmed.data() + trimmed.size(), result);
  if (error != std::errc{} || end != trimmed.data() + trimmed.size() ||
      result < domain::ModelConfig::unlimited_tool_calls) {
    return std::nullopt;
  }
  return result;
}

std::string PresetLabel(std::string_view id) {
  if (id == "deepseek")
    return "DeepSeek";
  if (id == "glm")
    return "GLM";
  if (id == "mimo")
    return "Mimo";
  if (id == "mimo-token-plan")
    return "Mimo Token Plan";
  if (id == "kimi")
    return "Kimi";
  if (id == "qwen")
    return "Qwen";
  if (id == "openai")
    return "OpenAI";
  if (id == "claude")
    return "Claude";
  if (id == "gemini")
    return "Gemini";
  if (id == "openrouter")
    return "OpenRouter";
  if (id == "groq")
    return "Groq";
  if (id == "together")
    return "Together AI";
  if (id == "siliconflow")
    return "SiliconFlow";
  if (id == "minimax")
    return "MiniMax";
  if (id == "ollama")
    return "Ollama";
  if (id == "lmstudio")
    return "LM Studio";
  if (id == "codex")
    return "Codex";
  return std::string{id};
}

} // namespace

ModelDraft
ModelFormService::New(std::optional<domain::ModelProviderPreset> preset,
                      bool local) {
  const auto protocol = local    ? domain::ModelProtocol::local_gguf
                        : preset ? preset->protocol
                                 : domain::ModelProtocol::openai_compatible;
  ModelDraft draft;
  draft.protocol = protocol;
  draft.provider_label =
      preset ? PresetLabel(preset->id)
             : std::string{domain::ModelProtocolLabel(protocol)};
  draft.base_url = preset ? std::string{preset->base_url} : std::string{};
  draft.tool_call_limit =
      std::to_string(domain::ModelConfig::default_tool_call_limit);
  draft.context_size = local ? "4096" : "";
  draft.local = local;
  return draft;
}

ModelDraft ModelFormService::Edit(const domain::ModelConfig &model) {
  return ModelDraft{
      .id = model.id,
      .name = model.name,
      .protocol = model.protocol,
      .provider_label = model.provider_label,
      .base_url = model.base_url,
      .api_key = model.api_key,
      .model_id = model.model_id,
      .tool_call_limit = std::to_string(model.tool_call_limit),
      .compression_enabled = model.compression_model_enabled,
      .compression_auto = model.compression_model_auto,
      .compression_model_id = model.compression_model_id,
      .context_size = FormatContextSize(model.context_size),
      .local = model.protocol == domain::ModelProtocol::local_gguf,
  };
}

std::expected<domain::ModelConfig, ModelValidationError>
ModelFormService::Build(const ModelDraft &draft) {
  if (draft.local) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::local_backend_unavailable});
  }
  const auto model_id = Trim(draft.model_id);
  auto name = Trim(draft.name);
  if (name.empty()) {
    name = model_id;
  }
  if (name.empty() || model_id.empty()) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::missing_name_or_model_id});
  }
  const auto api_key = Trim(draft.api_key);
  if (api_key.empty()) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::missing_api_key});
  }
  const auto tool_limit = ParseToolLimit(draft.tool_call_limit);
  if (!tool_limit) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::invalid_tool_call_limit});
  }
  const auto compression_id = Trim(draft.compression_model_id);
  if (draft.compression_enabled && !draft.compression_auto &&
      compression_id.empty()) {
    return std::unexpected(ModelValidationError{
        ModelValidationCode::missing_compression_model_id});
  }

  domain::ModelConfig model{
      .id = draft.id,
      .name = std::move(name),
      .protocol = draft.protocol,
      .provider_label = Trim(draft.provider_label),
      .base_url = EffectiveBaseUrl(draft),
      .api_key = api_key,
      .model_id = model_id,
      .tool_call_limit = *tool_limit,
      .compression_model_enabled = draft.compression_enabled,
      .compression_model_auto = draft.compression_auto,
      .compression_model_id = compression_id,
      .context_size = ParseContextSize(draft.context_size),
  };
  model.Normalize();
  return model;
}

std::expected<domain::ModelConfig, ModelValidationError>
ModelFormService::BuildForProbe(const ModelDraft &draft) {
  if (draft.local) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::local_backend_unavailable});
  }
  const auto model_id = Trim(draft.model_id);
  if (model_id.empty()) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::missing_name_or_model_id});
  }
  const auto api_key = Trim(draft.api_key);
  if (api_key.empty()) {
    return std::unexpected(
        ModelValidationError{ModelValidationCode::missing_api_key});
  }
  auto name = Trim(draft.name);
  if (name.empty())
    name = model_id;
  const auto parsed_limit = ParseToolLimit(draft.tool_call_limit);
  domain::ModelConfig model{
      .id = draft.id,
      .name = std::move(name),
      .protocol = draft.protocol,
      .provider_label = Trim(draft.provider_label),
      .base_url = EffectiveBaseUrl(draft),
      .api_key = api_key,
      .model_id = model_id,
      .tool_call_limit =
          parsed_limit.value_or(domain::ModelConfig::default_tool_call_limit),
      .compression_model_enabled = draft.compression_enabled,
      .compression_model_auto = draft.compression_auto,
      .compression_model_id = Trim(draft.compression_model_id),
      .context_size = ParseContextSize(draft.context_size),
  };
  model.Normalize();
  return model;
}

bool ModelFormService::CanSave(const ModelDraft &draft) {
  return Build(draft).has_value();
}

bool ModelFormService::CanQuery(const ModelDraft &draft) {
  return !draft.local && !EffectiveBaseUrl(draft).empty() &&
         !Trim(draft.api_key).empty();
}

int ModelFormService::ParseContextSize(std::string_view text) noexcept {
  auto trimmed = Trim(text);
  if (trimmed.empty()) {
    return domain::ModelConfig::context_size_unset;
  }
  double multiplier = 1.0;
  const char suffix = trimmed.back();
  if (suffix == 'k' || suffix == 'K' || suffix == 'm' || suffix == 'M') {
    multiplier = suffix == 'm' || suffix == 'M' ? 1'000'000.0 : 1'000.0;
    trimmed.pop_back();
  }
  char *end{};
  const double parsed = std::strtod(trimmed.c_str(), &end);
  const double tokens = parsed * multiplier;
  if (end != trimmed.c_str() + trimmed.size() || !std::isfinite(tokens) ||
      tokens <= 0.0 ||
      tokens > static_cast<double>(std::numeric_limits<int>::max())) {
    return domain::ModelConfig::context_size_unset;
  }
  return static_cast<int>(std::llround(tokens));
}

std::string ModelFormService::FormatContextSize(int tokens) {
  if (tokens <= 0) {
    return {};
  }
  if (tokens >= 1'000'000 && tokens % 1'000'000 == 0) {
    return std::to_string(tokens / 1'000'000) + "M";
  }
  if (tokens >= 1'000 && tokens % 1'000 == 0) {
    return std::to_string(tokens / 1'000) + "K";
  }
  return std::to_string(tokens);
}

std::string ModelFormService::EffectiveBaseUrl(const ModelDraft &draft) {
  const auto explicit_url = Trim(draft.base_url);
  if (!explicit_url.empty()) {
    return explicit_url;
  }
  return draft.protocol == domain::ModelProtocol::anthropic_messages
             ? "https://api.anthropic.com"
             : "https://api.openai.com/v1";
}

} // namespace linecode::application
