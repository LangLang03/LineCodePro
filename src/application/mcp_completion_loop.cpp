#include "application/mcp_completion_loop.h"

#include "application/ports/mid_loop_compactor.h"

#include <algorithm>
#include <chrono>
#include <optional>
#include <stdexcept>
#include <utility>

#include "application/tool_permission_service.h"

namespace linecode::application {
namespace {

[[nodiscard]] CompletionError LoopError(std::string message) {
  return {.code = CompletionErrorCode::transport,
          .message = std::move(message),
          .http_status = 0};
}

[[nodiscard]] std::int64_t NowMillis() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

void Emit(CompletionObserver &observer, CompletionToolCallEvent event) {
  if (observer.on_event)
    observer.on_event(CompletionEvent{std::move(event)});
}

[[nodiscard]] CompletionObserver TurnObserver(CompletionObserver &observer,
                                              std::size_t turn_index) {
  CompletionObserver result;
  result.on_tool_review = observer.on_tool_review;
  result.on_event = [&observer, turn_index](const CompletionEvent &event) {
    if (!observer.on_event)
      return;
    std::visit(
        [&observer, turn_index](const auto &source) {
          auto stamped = source;
          stamped.turn_index = turn_index;
          observer.on_event(CompletionEvent{std::move(stamped)});
        },
        event);
  };
  return result;
}

} // namespace

McpCompletionLoop::McpCompletionLoop(
    std::shared_ptr<CompletionGateway> completion,
    std::shared_ptr<ToolRegistry> tools,
    std::shared_ptr<ToolPermissionService> permissions,
    std::shared_ptr<CompletionRequestComposer> request_composer,
    std::shared_ptr<const ToolResultDisplayProjector> result_display,
    std::shared_ptr<MidLoopCompactor> mid_loop_compactor)
    : completion_(std::move(completion)), tools_(std::move(tools)),
      permissions_(std::move(permissions)),
      request_composer_(std::move(request_composer)),
      result_display_(result_display ? std::move(result_display)
                                     : DefaultToolResultDisplayProjector()),
      mid_loop_compactor_(std::move(mid_loop_compactor)) {
  if (!completion_ || !tools_)
    throw std::invalid_argument(
        "McpCompletionLoop requires completion gateway and MCP registry");
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
McpCompletionLoop::Complete(CompletionRequest request,
                            CompletionObserver observer) {
  auto prepared = co_await PrepareTools(request);
  if (!prepared)
    co_return std::unexpected(std::move(prepared.error()));
  co_return co_await RunPrepared(std::move(request), std::move(observer));
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
McpCompletionLoop::Complete(CompletionRequest request,
                            PromptAssemblyContext prompt_context,
                            CompletionObserver observer) {
  auto prepared = co_await PrepareTools(request);
  if (!prepared)
    co_return std::unexpected(std::move(prepared.error()));
  if (!request_composer_) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = "Prompt request composer is unavailable",
    });
  }
  auto composed = co_await request_composer_->Compose(
      std::move(request), std::move(prompt_context));
  if (!composed) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = std::move(composed.error().message),
    });
  }
  co_return co_await RunPrepared(std::move(*composed), std::move(observer));
}

huxerui::Task<std::expected<void, CompletionError>>
McpCompletionLoop::PrepareTools(CompletionRequest &request) {
  auto refreshed = co_await tools_->Refresh();
  if (!refreshed)
    co_return std::unexpected(LoopError(refreshed.error().message));

  request.tools.clear();
  request.tools.reserve(tools_->Tools().size());
  for (const auto &tool : tools_->Tools()) {
    request.tools.push_back({.name = tool.name,
                             .description = tool.description,
                             .parameters_json = tool.parameters_json});
  }
  co_return std::expected<void, CompletionError>{};
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
McpCompletionLoop::RunPrepared(CompletionRequest request,
                               CompletionObserver observer) {
  const bool unlimited = request.model.tool_call_limit ==
                         domain::ModelConfig::unlimited_tool_calls;
  std::size_t invoked_count{};
  std::size_t turn_index{};
  for (;;) {
    auto response = co_await completion_->Complete(
        request, TurnObserver(observer, turn_index));
    if (!response)
      co_return std::unexpected(std::move(response.error()));
    if (response->tool_calls.empty())
      co_return std::move(*response);

    const auto requested_count = response->tool_calls.size();
    if (!unlimited &&
        (request.model.tool_call_limit <= 0 ||
         requested_count >
             static_cast<std::size_t>(request.model.tool_call_limit) -
                 std::min(
                     static_cast<std::size_t>(request.model.tool_call_limit),
                     invoked_count))) {
      co_return std::unexpected(CompletionError{
          .code = CompletionErrorCode::decode,
          .message = "The model exceeded its MCP tool call limit",
          .http_status = 0,
      });
    }

    request.messages.push_back(CompletionMessage::Assistant(
        response->text, std::move(response->tool_calls),
        std::move(response->reasoning_content)));
    const auto calls = request.messages.back().tool_calls;
    for (const auto &call : calls) {
      const auto started_at = NowMillis();
      Emit(observer, CompletionToolCallEvent{
                         .turn_index = turn_index,
                         .call = call,
                         .status = CompletionToolCallStatus::requested,
                         .result = std::nullopt,
                         .display = {},
                         .created_at_millis = started_at,
                     });
      const auto descriptor =
          std::ranges::find(tools_->Tools(), std::string_view{call.name},
                            [](const RegisteredTool &tool) {
                              return std::string_view{tool.name};
                            });
      if (descriptor == tools_->Tools().end()) {
        auto result = CompletionToolResult{.call_id = call.id,
                                           .name = call.name,
                                           .content = "Unknown runtime tool: " +
                                                      call.name,
                                           .error = true};
        const auto display =
            result_display_->Project(call.name, result.content, result.error);
        Emit(observer, CompletionToolCallEvent{
                           .turn_index = turn_index,
                           .call = call,
                           .status = CompletionToolCallStatus::failed,
                           .result = result,
                           .display = display,
                           .created_at_millis = started_at,
                           .duration_millis = NowMillis() - started_at,
                       });
        result.content = display.model_content;
        request.messages.push_back(CompletionMessage::Tool(std::move(result)));
        ++invoked_count;
        continue;
      }

      ToolPermissionDecision permission = ToolPermissionDecision::execute;
      if (permissions_) {
        auto evaluated = co_await permissions_->Evaluate(
            *descriptor, call, request.permission_scope);
        if (!evaluated)
          co_return std::unexpected(LoopError(evaluated.error().message));
        permission = *evaluated;
      }
      if (permission == ToolPermissionDecision::deny) {
        auto result = CompletionToolResult{
            .call_id = call.id,
            .name = call.name,
            .content =
                "This tool is not allowed in read-only mode: " + call.name,
            .error = true};
        const auto display =
            result_display_->Project(call.name, result.content, result.error);
        Emit(observer, CompletionToolCallEvent{
                           .turn_index = turn_index,
                           .call = call,
                           .status = CompletionToolCallStatus::rejected,
                           .result = result,
                           .display = display,
                           .created_at_millis = started_at,
                           .duration_millis = NowMillis() - started_at,
                       });
        result.content = display.model_content;
        request.messages.push_back(CompletionMessage::Tool(std::move(result)));
        ++invoked_count;
        continue;
      }
      if (permission == ToolPermissionDecision::review) {
        Emit(observer, CompletionToolCallEvent{
                           .turn_index = turn_index,
                           .call = call,
                           .status = CompletionToolCallStatus::awaiting_review,
                           .result = std::nullopt,
                           .display = {},
                           .created_at_millis = started_at,
                       });
        auto decision = CompletionObserver::ToolReviewDecision::reject;
        if (observer.on_tool_review) {
          decision = co_await observer.on_tool_review(
              CompletionObserver::ToolReviewRequest{
                  .call = call,
                  .can_allow_always = descriptor->SupportsPermanentGrant() &&
                                      !request.permission_scope.empty(),
              });
        }
        if (decision == CompletionObserver::ToolReviewDecision::reject) {
          auto result = CompletionToolResult{
              .call_id = call.id,
              .name = call.name,
              .content = "The user rejected this tool call.",
              .error = true};
          const auto display =
              result_display_->Project(call.name, result.content, result.error);
          Emit(observer, CompletionToolCallEvent{
                             .turn_index = turn_index,
                             .call = call,
                             .status = CompletionToolCallStatus::rejected,
                             .result = result,
                             .display = display,
                             .created_at_millis = started_at,
                             .duration_millis = NowMillis() - started_at,
                         });
          result.content = display.model_content;
          request.messages.push_back(
              CompletionMessage::Tool(std::move(result)));
          ++invoked_count;
          continue;
        }
        if (decision == CompletionObserver::ToolReviewDecision::allow_always &&
            permissions_) {
          auto remembered = co_await permissions_->RememberPermanentGrant(
              *descriptor, call, request.permission_scope);
          if (!remembered)
            co_return std::unexpected(LoopError(remembered.error().message));
        }
      }
      Emit(observer, CompletionToolCallEvent{
                         .turn_index = turn_index,
                         .call = call,
                         .status = CompletionToolCallStatus::running,
                         .result = std::nullopt,
                         .display = {},
                         .created_at_millis = started_at,
                     });
      auto invoked = co_await tools_->InvokeWithContext(
          call.name, call.arguments_json,
          ToolInvocationContext{
              .call_id = call.id,
              .on_progress_json =
                  [emit_progress = observer.on_event, call, turn_index,
                   started_at](std::string progress_json) {
                    if (!emit_progress)
                      return;
                    emit_progress(CompletionEvent{CompletionToolCallEvent{
                        .turn_index = turn_index,
                        .call = call,
                        .status = CompletionToolCallStatus::running,
                        .result = CompletionToolResult{
                            .call_id = call.id,
                            .name = call.name,
                            .content = std::move(progress_json)},
                        .display = {},
                        .created_at_millis = started_at,
                        .duration_millis = NowMillis() - started_at,
                    }});
                  },
          });
      CompletionToolResult result{
          .call_id = call.id, .name = call.name, .content = {}, .error = false};
      if (invoked) {
        result.content = invoked->content.empty() ? "MCP tool completed"
                                                  : std::move(invoked->content);
        result.error = invoked->error;
        result.diff_id = std::move(invoked->diff_id);
      } else {
        result.content = invoked.error().message;
        result.error = true;
      }
      const auto display =
          result_display_->Project(call.name, result.content, result.error);
      Emit(observer,
           CompletionToolCallEvent{
               .turn_index = turn_index,
               .call = call,
               .status = result.error ? CompletionToolCallStatus::failed
                                      : CompletionToolCallStatus::completed,
               .result = result,
               .display = display,
               .created_at_millis = started_at,
               .duration_millis = NowMillis() - started_at,
           });
      result.content = display.model_content;
      request.messages.push_back(CompletionMessage::Tool(std::move(result)));
      ++invoked_count;
    }
    // Legacy `continueModelAfterTools()`: before the next model turn, give the
    // compactor a chance to shrink the history so a long tool loop cannot
    // outgrow the window.
    if (mid_loop_compactor_) {
      // Hand the provider's own count to the trigger: the local estimate
      // divides characters by four, which undercounts CJK text by a wide
      // margin and would let the context drift past the window.
      request = co_await mid_loop_compactor_->CompactIfNeeded(
          std::move(request), response->input_tokens);
    }
    ++turn_index;
  }
}

} // namespace linecode::application
