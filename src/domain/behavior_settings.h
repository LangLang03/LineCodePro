#pragma once

#include <cstdint>
#include <string_view>

namespace linecode::domain {

enum class ToneMode : std::uint8_t { coding, chat };
enum class ReasoningEffort : std::uint8_t { off, automatic, low, medium, high, maximum };
enum class EnterKeyBehavior : std::uint8_t { send, newline };

struct AiBehaviorSettings final {
  ToneMode tone{ToneMode::coding};
  ReasoningEffort reasoning{ReasoningEffort::medium};
  bool thinking_scroll{true};
  bool thinking_auto_expand{};
  bool preserve_reasoning{};
  bool learning_mode{};
  bool soft_compaction{true};

  bool operator==(const AiBehaviorSettings &) const = default;
};

struct InputSettings final {
  EnterKeyBehavior enter_key{EnterKeyBehavior::send};

  bool operator==(const InputSettings &) const = default;
};

[[nodiscard]] constexpr ToneMode ParseToneMode(std::string_view value) noexcept {
  return value == "chat" ? ToneMode::chat : ToneMode::coding;
}

[[nodiscard]] constexpr std::string_view SerializeToneMode(ToneMode value) noexcept {
  return value == ToneMode::chat ? "chat" : "coding";
}

[[nodiscard]] constexpr ReasoningEffort
ParseReasoningEffort(std::string_view value) noexcept {
  if (value == "off") return ReasoningEffort::off;
  if (value == "auto") return ReasoningEffort::automatic;
  if (value == "low") return ReasoningEffort::low;
  if (value == "high") return ReasoningEffort::high;
  if (value == "max") return ReasoningEffort::maximum;
  return ReasoningEffort::medium;
}

[[nodiscard]] constexpr std::string_view
SerializeReasoningEffort(ReasoningEffort value) noexcept {
  switch (value) {
  case ReasoningEffort::off: return "off";
  case ReasoningEffort::automatic: return "auto";
  case ReasoningEffort::low: return "low";
  case ReasoningEffort::medium: return "medium";
  case ReasoningEffort::high: return "high";
  case ReasoningEffort::maximum: return "max";
  }
  return "medium";
}

[[nodiscard]] constexpr EnterKeyBehavior
ParseEnterKeyBehavior(std::string_view value) noexcept {
  return value == "newline" ? EnterKeyBehavior::newline
                            : EnterKeyBehavior::send;
}

[[nodiscard]] constexpr std::string_view
SerializeEnterKeyBehavior(EnterKeyBehavior value) noexcept {
  return value == EnterKeyBehavior::newline ? "newline" : "send";
}

} // namespace linecode::domain
