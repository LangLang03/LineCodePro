#pragma once

#include <array>
#include <string_view>

#include <app_resources.h>
#include <huxerui/resource.h>

#include "domain/model_config.h"

namespace linecode::presentation {

// The preset screen renders this data without knowing any provider id. Adding
// provider-specific copy or geometry belongs here rather than in the View.
struct ModelProviderPresetRowGeometry final {
  float minimum_height;
  float vertical_padding;
  float text_offset_y;
};

struct ModelProviderPresetPresentation final {
  domain::ModelProviderPresetKind kind;
  huxerui::StringResource label;
  huxerui::StringResource description;
  std::string_view initial;
  ModelProviderPresetRowGeometry row;
};

inline constexpr ModelProviderPresetRowGeometry standard_preset_row{
    .minimum_height = 61.35F,
    .vertical_padding = 11.0F,
    .text_offset_y = -0.76F,
};

inline constexpr ModelProviderPresetRowGeometry tall_preset_row{
    .minimum_height = 65.7F,
    .vertical_padding = 12.0F,
    .text_offset_y = 1.14F,
};

inline const std::array model_provider_preset_presentations{
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::deepseek,
        app::strings::model_preset_deepseek_label,
        app::strings::model_preset_deepseek_desc, "D", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::glm,
        app::strings::model_preset_glm_label,
        app::strings::model_preset_glm_desc, "G", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::mimo,
        app::strings::model_preset_mimo_label,
        app::strings::model_preset_mimo_desc, "M", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::mimo_token_plan,
        app::strings::model_preset_mimo_token_plan_label,
        app::strings::model_preset_mimo_token_plan_desc, "M", tall_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::kimi,
        app::strings::model_preset_kimi_label,
        app::strings::model_preset_kimi_desc, "K", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::qwen,
        app::strings::model_preset_qwen_label,
        app::strings::model_preset_qwen_desc, "Q", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::openai,
        app::strings::model_preset_openai_label,
        app::strings::model_preset_openai_desc, "O", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::claude,
        app::strings::model_preset_claude_label,
        app::strings::model_preset_claude_desc, "C", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::gemini,
        app::strings::model_preset_gemini_label,
        app::strings::model_preset_gemini_desc, "G", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::openrouter,
        app::strings::model_preset_openrouter_label,
        app::strings::model_preset_openrouter_desc, "O", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::groq,
        app::strings::model_preset_groq_label,
        app::strings::model_preset_groq_desc, "G", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::together,
        app::strings::model_preset_together_label,
        app::strings::model_preset_together_desc, "T", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::siliconflow,
        app::strings::model_preset_siliconflow_label,
        app::strings::model_preset_siliconflow_desc, "S", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::minimax,
        app::strings::model_preset_minimax_label,
        app::strings::model_preset_minimax_desc, "M", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::ollama,
        app::strings::model_preset_ollama_label,
        app::strings::model_preset_ollama_desc, "O", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::lmstudio,
        app::strings::model_preset_lmstudio_label,
        app::strings::model_preset_lmstudio_desc, "L", standard_preset_row},
    ModelProviderPresetPresentation{
        domain::ModelProviderPresetKind::codex,
        app::strings::model_preset_codex_label,
        app::strings::model_preset_codex_desc, "C", standard_preset_row},
};

static_assert(model_provider_preset_presentations.size() ==
              domain::model_provider_preset_count);

} // namespace linecode::presentation
