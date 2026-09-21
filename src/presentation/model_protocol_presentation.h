#pragma once

#include <algorithm>
#include <array>
#include <string_view>
#include <utility>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/model_config.h"

namespace linecode::presentation {

struct ModelProtocolPresentation final {
  domain::ModelProtocol protocol;
  huxerui::StringResource name;
  huxerui::StringResource descriptive_name;
  huxerui::Color badge_color;
  std::string_view base_url_placeholder;
  huxerui::StringResource base_url_hint;
  bool selectable_in_remote_form;
};

inline const std::array model_protocol_presentations{
    ModelProtocolPresentation{
        domain::ModelProtocol::openai_compatible,
        app::strings::model_protocol_openai,
        app::strings::model_protocol_openai_compatible,
        huxerui::Color::Rgb(16, 163, 127),
        "https://api.example.com/v1",
        app::strings::model_form_base_url_hint,
        true,
    },
    ModelProtocolPresentation{
        domain::ModelProtocol::codex_responses,
        app::strings::model_protocol_codex,
        app::strings::model_protocol_codex,
        huxerui::Color::Rgb(75, 139, 255),
        "https://api.example.com/codex",
        app::strings::model_form_base_url_hint_codex,
        true,
    },
    ModelProtocolPresentation{
        domain::ModelProtocol::anthropic_messages,
        app::strings::model_protocol_anthropic,
        app::strings::model_protocol_anthropic,
        huxerui::Color::Rgb(184, 111, 80),
        "https://api.example.com/anthropic",
        app::strings::model_form_base_url_hint_anthropic,
        true,
    },
    ModelProtocolPresentation{
        domain::ModelProtocol::local_gguf,
        app::strings::model_protocol_local,
        app::strings::model_protocol_local,
        huxerui::Color::Rgb(46, 125, 98),
        "https://api.example.com/v1",
        app::strings::model_form_base_url_hint,
        false,
    },
};

[[nodiscard]] inline const ModelProtocolPresentation &
ModelProtocolPresentationFor(domain::ModelProtocol protocol) noexcept {
  const auto found = std::ranges::find(model_protocol_presentations, protocol,
                                       &ModelProtocolPresentation::protocol);
  if (found != model_protocol_presentations.end())
    return *found;
  std::unreachable();
}

} // namespace linecode::presentation
