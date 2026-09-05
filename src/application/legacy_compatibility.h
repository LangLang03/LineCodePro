#pragma once

#include <string_view>

#include "domain/app_state.h"

namespace linecode::application {

enum class HistoricalToolDisposition {
  normal,
  inert_generic,
};

constexpr domain::ChatMode NormalizeLegacyChatMode(std::string_view stored_mode) noexcept {
  if (stored_mode == "plan") {
    return domain::ChatMode::plan;
  }
  if (stored_mode == "agent" || stored_mode == "control") {
    return domain::ChatMode::agent;
  }
  return domain::ChatMode::chat;
}

constexpr HistoricalToolDisposition ClassifyHistoricalTool(std::string_view tool_name) noexcept {
  return tool_name.starts_with("phone_") ? HistoricalToolDisposition::inert_generic
                                         : HistoricalToolDisposition::normal;
}

} // namespace linecode::application
