#include "domain/model_config.h"

#include <algorithm>
#include <cctype>

namespace linecode::domain {
namespace {

std::string LowerTrimmed(std::string_view value) {
  const auto first = std::ranges::find_if(value, [](unsigned char character) {
    return std::isspace(character) == 0;
  });
  const auto last =
      std::find_if(value.rbegin(), value.rend(), [](unsigned char character) {
        return std::isspace(character) == 0;
      }).base();
  if (first >= last) {
    return {};
  }
  std::string normalized(first, last);
  std::ranges::transform(normalized, normalized.begin(),
                         [](unsigned char character) {
                           return static_cast<char>(std::tolower(character));
                         });
  return normalized;
}

} // namespace

std::string_view ModelProtocolStorageName(ModelProtocol protocol) noexcept {
  switch (protocol) {
  case ModelProtocol::openai_compatible:
    return "OPENAI_COMPATIBLE";
  case ModelProtocol::codex_responses:
    return "CODEX_RESPONSES";
  case ModelProtocol::anthropic_messages:
    return "ANTHROPIC_MESSAGES";
  case ModelProtocol::local_gguf:
    return "LOCAL_GGUF";
  }
  return "OPENAI_COMPATIBLE";
}

ModelProtocol ParseModelProtocol(std::string_view value) noexcept {
  const auto normalized = LowerTrimmed(value);
  if (normalized == "codex" || normalized == "codex_responses") {
    return ModelProtocol::codex_responses;
  }
  if (normalized == "anthropic" || normalized == "claude" ||
      normalized == "anthropic_messages") {
    return ModelProtocol::anthropic_messages;
  }
  if (normalized == "local" || normalized == "gguf" ||
      normalized == "local_gguf") {
    return ModelProtocol::local_gguf;
  }
  return ModelProtocol::openai_compatible;
}

std::string_view ModelProtocolLabel(ModelProtocol protocol) noexcept {
  switch (protocol) {
  case ModelProtocol::openai_compatible:
    return "OpenAI";
  case ModelProtocol::codex_responses:
    return "Codex";
  case ModelProtocol::anthropic_messages:
    return "Anthropic";
  case ModelProtocol::local_gguf:
    return "Local";
  }
  return "OpenAI";
}

std::string ModelConfig::EffectiveCompressionModelId() const {
  if (!compression_model_enabled || compression_model_auto ||
      compression_model_id.empty()) {
    return model_id;
  }
  return compression_model_id;
}

void ModelConfig::Normalize() {
  if (tool_call_limit < unlimited_tool_calls) {
    tool_call_limit = 0;
  }
  if (context_size < 0) {
    context_size = context_size_unset;
  }
  if (!SupportsDedicatedCompression(protocol)) {
    compression_model_enabled = false;
  }
  const auto first = compression_model_id.find_first_not_of(" \t\n\r");
  const auto last = compression_model_id.find_last_not_of(" \t\n\r");
  compression_model_id =
      first == std::string::npos
          ? std::string{}
          : compression_model_id.substr(first, last - first + 1);
  if (provider_label.empty()) {
    provider_label = ModelProtocolLabel(protocol);
  }
}

const std::array<ModelProviderPreset, 17> &ModelProviderPresets() noexcept {
  static constexpr std::array presets{
      ModelProviderPreset{"deepseek", ModelProtocol::openai_compatible,
                          "https://api.deepseek.com/v1",
                          "https://api.deepseek.com/v1"},
      ModelProviderPreset{"glm", ModelProtocol::openai_compatible,
                          "https://open.bigmodel.cn/api/paas/v4",
                          "https://open.bigmodel.cn/api/paas/v4"},
      ModelProviderPreset{"mimo", ModelProtocol::openai_compatible,
                          "https://api.xiaomimimo.com/v1",
                          "https://api.xiaomimimo.com/v1"},
      ModelProviderPreset{"mimo-token-plan", ModelProtocol::openai_compatible,
                          "https://token-plan-cn.xiaomimimo.com/v1",
                          "https://token-plan-cn.xiaomimimo.com/v1"},
      ModelProviderPreset{"kimi", ModelProtocol::openai_compatible,
                          "https://api.moonshot.cn/v1",
                          "https://api.moonshot.cn/v1"},
      ModelProviderPreset{"qwen", ModelProtocol::openai_compatible,
                          "https://dashscope.aliyuncs.com/compatible-mode/v1",
                          "https://dashscope.aliyuncs.com/compatible-mode/v1"},
      ModelProviderPreset{"openai", ModelProtocol::openai_compatible,
                          "https://api.openai.com/v1",
                          "https://api.openai.com/v1"},
      ModelProviderPreset{"claude", ModelProtocol::anthropic_messages,
                          "https://api.anthropic.com",
                          "https://api.anthropic.com"},
      ModelProviderPreset{
          "gemini", ModelProtocol::openai_compatible,
          "https://generativelanguage.googleapis.com/v1beta/openai",
          "https://generativelanguage.googleapis.com/v1beta/openai"},
      ModelProviderPreset{"openrouter", ModelProtocol::openai_compatible,
                          "https://openrouter.ai/api/v1",
                          "https://openrouter.ai/api/v1"},
      ModelProviderPreset{"groq", ModelProtocol::openai_compatible,
                          "https://api.groq.com/openai/v1",
                          "https://api.groq.com/openai/v1"},
      ModelProviderPreset{"together", ModelProtocol::openai_compatible,
                          "https://api.together.xyz/v1",
                          "https://api.together.xyz/v1"},
      ModelProviderPreset{"siliconflow", ModelProtocol::openai_compatible,
                          "https://api.siliconflow.cn/v1",
                          "https://api.siliconflow.cn/v1"},
      ModelProviderPreset{"minimax", ModelProtocol::openai_compatible,
                          "https://api.minimax.chat/v1",
                          "https://api.minimax.chat/v1"},
      ModelProviderPreset{"ollama", ModelProtocol::openai_compatible,
                          "http://127.0.0.1:11434/v1",
                          "http://127.0.0.1:11434/v1"},
      ModelProviderPreset{"lmstudio", ModelProtocol::openai_compatible,
                          "http://127.0.0.1:1234/v1",
                          "http://127.0.0.1:1234/v1"},
      ModelProviderPreset{"codex", ModelProtocol::codex_responses,
                          "https://api.openai.com/v1",
                          "https://api.openai.com/v1"},
  };
  return presets;
}

std::optional<ModelProviderPreset>
FindModelProviderPreset(std::string_view id) noexcept {
  const auto &presets = ModelProviderPresets();
  const auto found = std::ranges::find(presets, id, &ModelProviderPreset::id);
  if (found == presets.end()) {
    return std::nullopt;
  }
  return *found;
}

} // namespace linecode::domain
