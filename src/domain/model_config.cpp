#include "domain/model_config.h"

#include <algorithm>
#include <cctype>
#include <utility>

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
  return ModelProtocolInfo(protocol).label;
}

LocalAcceleration ParseLocalAcceleration(std::string_view value) noexcept {
  const auto normalized = LowerTrimmed(value);
  if (normalized.empty() || normalized == "auto" || normalized == "automatic" ||
      normalized == "default") {
    return LocalAcceleration::automatic;
  }
  if (normalized == "cpu" || normalized == "host") {
    return LocalAcceleration::cpu;
  }
  if (normalized == "gpu" || normalized == "vulkan" ||
      normalized == "opencl" || normalized == "adreno") {
    return LocalAcceleration::gpu;
  }
  // NPU acceleration is not supported yet, so an NPU-era spelling from an
  // outside archive is honoured as a CPU request instead of being discarded as
  // an unknown value that would silently become "auto".
  if (normalized == "npu" || normalized == "nnapi" || normalized == "qnn" ||
      normalized == "hexagon" || normalized == "htp") {
    return LocalAcceleration::cpu;
  }
  return LocalAcceleration::automatic;
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
  // Only a GGUF model owns local state. Dropping it for every other protocol
  // keeps a model that was switched back to a remote provider from carrying a
  // stale path or accelerator into storage and the archive.
  if (!IsLocal()) {
    local_model_path.clear();
    local_acceleration = LocalAcceleration::automatic;
  } else {
    const auto path_first = local_model_path.find_first_not_of(" \t\n\r");
    const auto path_last = local_model_path.find_last_not_of(" \t\n\r");
    local_model_path =
        path_first == std::string::npos
            ? std::string{}
            : local_model_path.substr(path_first, path_last - path_first + 1);
    if (local_acceleration == LocalAcceleration::count) {
      local_acceleration = LocalAcceleration::automatic;
    }
  }
}

const std::array<ModelProviderPreset, model_provider_preset_count> &
ModelProviderPresets() noexcept {
  static constexpr std::array presets{
      ModelProviderPreset{ModelProviderPresetKind::deepseek, "deepseek",
                          "DeepSeek", ModelProtocol::openai_compatible,
                          "https://api.deepseek.com/v1",
                          "https://api.deepseek.com/v1"},
      ModelProviderPreset{ModelProviderPresetKind::glm, "glm", "GLM",
                          ModelProtocol::openai_compatible,
                          "https://open.bigmodel.cn/api/paas/v4",
                          "https://open.bigmodel.cn/api/paas/v4"},
      ModelProviderPreset{ModelProviderPresetKind::mimo, "mimo", "Mimo",
                          ModelProtocol::openai_compatible,
                          "https://api.xiaomimimo.com/v1",
                          "https://api.xiaomimimo.com/v1"},
      ModelProviderPreset{ModelProviderPresetKind::mimo_token_plan,
                          "mimo-token-plan", "Mimo Token Plan",
                          ModelProtocol::openai_compatible,
                          "https://token-plan-cn.xiaomimimo.com/v1",
                          "https://token-plan-cn.xiaomimimo.com/v1"},
      ModelProviderPreset{ModelProviderPresetKind::kimi, "kimi", "Kimi",
                          ModelProtocol::openai_compatible,
                          "https://api.moonshot.cn/v1",
                          "https://api.moonshot.cn/v1"},
      ModelProviderPreset{ModelProviderPresetKind::qwen, "qwen", "Qwen",
                          ModelProtocol::openai_compatible,
                          "https://dashscope.aliyuncs.com/compatible-mode/v1",
                          "https://dashscope.aliyuncs.com/compatible-mode/v1"},
      ModelProviderPreset{ModelProviderPresetKind::openai, "openai", "OpenAI",
                          ModelProtocol::openai_compatible,
                          "https://api.openai.com/v1",
                          "https://api.openai.com/v1"},
      ModelProviderPreset{ModelProviderPresetKind::claude, "claude", "Claude",
                          ModelProtocol::anthropic_messages,
                          "https://api.anthropic.com",
                          "https://api.anthropic.com"},
      ModelProviderPreset{
          ModelProviderPresetKind::gemini, "gemini", "Gemini",
          ModelProtocol::openai_compatible,
          "https://generativelanguage.googleapis.com/v1beta/openai",
          "https://generativelanguage.googleapis.com/v1beta/openai"},
      ModelProviderPreset{ModelProviderPresetKind::openrouter, "openrouter",
                          "OpenRouter", ModelProtocol::openai_compatible,
                          "https://openrouter.ai/api/v1",
                          "https://openrouter.ai/api/v1"},
      ModelProviderPreset{ModelProviderPresetKind::groq, "groq", "Groq",
                          ModelProtocol::openai_compatible,
                          "https://api.groq.com/openai/v1",
                          "https://api.groq.com/openai/v1"},
      ModelProviderPreset{ModelProviderPresetKind::together, "together",
                          "Together AI", ModelProtocol::openai_compatible,
                          "https://api.together.xyz/v1",
                          "https://api.together.xyz/v1"},
      ModelProviderPreset{ModelProviderPresetKind::siliconflow, "siliconflow",
                          "SiliconFlow", ModelProtocol::openai_compatible,
                          "https://api.siliconflow.cn/v1",
                          "https://api.siliconflow.cn/v1"},
      ModelProviderPreset{ModelProviderPresetKind::minimax, "minimax",
                          "MiniMax", ModelProtocol::openai_compatible,
                          "https://api.minimax.chat/v1",
                          "https://api.minimax.chat/v1"},
      ModelProviderPreset{ModelProviderPresetKind::ollama, "ollama", "Ollama",
                          ModelProtocol::openai_compatible,
                          "http://127.0.0.1:11434/v1",
                          "http://127.0.0.1:11434/v1"},
      ModelProviderPreset{ModelProviderPresetKind::lmstudio, "lmstudio",
                          "LM Studio", ModelProtocol::openai_compatible,
                          "http://127.0.0.1:1234/v1",
                          "http://127.0.0.1:1234/v1"},
      ModelProviderPreset{ModelProviderPresetKind::codex, "codex", "Codex",
                          ModelProtocol::codex_responses,
                          "https://api.openai.com/v1",
                          "https://api.openai.com/v1"},
  };
  static_assert(presets.size() == model_provider_preset_count);
  static_assert([](const auto &values) consteval {
    for (std::size_t index = 0; index < values.size(); ++index) {
      if (ModelProviderPresetIndex(values[index].kind) != index) {
        return false;
      }
    }
    return true;
  }(presets));
  return presets;
}

const ModelProviderPreset &
ModelProviderPresetFor(ModelProviderPresetKind kind) {
  return ModelProviderPresets().at(ModelProviderPresetIndex(kind));
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
