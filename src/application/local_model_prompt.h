#pragma once

#include <string>
#include <string_view>
#include <vector>

#include "application/ports/completion_gateway.h"
#include "application/ports/local_model_engine.h"

namespace linecode::application {

// The result of scanning a local model's reply for a tool-call envelope.
struct LocalToolExtraction final {
  // The reply with the recognised envelope removed and trailing whitespace
  // trimmed. Prose the model wrote around a call is kept, because a local model
  // often explains the call it is about to make.
  std::string text;
  std::vector<CompletionToolCall> calls;

  bool operator==(const LocalToolExtraction &) const = default;
};

// Turns a conversation into the turns a local runtime should see.
//
// Local templates only reliably understand system, user and assistant roles, so
// tool output is rendered as a user turn that names the call it answers, and a
// previous assistant turn that requested tools carries the envelope text back
// in its content. Getting that wrong makes a model re-request the tool it just
// ran, which is the failure mode this shape exists to avoid.
[[nodiscard]] std::vector<LocalChatTurn>
BuildLocalModelTurns(const CompletionRequest &request);

// The system instruction that teaches the model the tool-call envelope. Empty
// when the request carries no tools, so a plain chat conversation is not given
// instructions it cannot use.
[[nodiscard]] std::string
BuildLocalToolInstruction(const std::vector<CompletionTool> &tools);

// Extracts tool calls from a local model's reply.
//
// The parser is deliberately conservative: it only accepts a JSON object that
// carries a `tool_calls` array with a recognised `name`, either bare or inside a
// fenced code block. A model that emits prose mentioning curly braces does not
// accidentally trigger a tool call, because the alternative -- guessing -- would
// execute tools the model never asked for.
[[nodiscard]] LocalToolExtraction ExtractLocalToolCalls(std::string_view reply);

// Renders the envelope a local model is asked to emit. Shared by the
// instruction text so the documented shape and the parsed shape cannot drift.
[[nodiscard]] std::string_view LocalToolCallEnvelopeSchema() noexcept;

} // namespace linecode::application
