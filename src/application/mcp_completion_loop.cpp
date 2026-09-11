#include "application/mcp_completion_loop.h"

#include <algorithm>
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

} // namespace

McpCompletionLoop::McpCompletionLoop(
    std::shared_ptr<CompletionGateway> completion,
    std::shared_ptr<ToolRegistry> tools,
    std::shared_ptr<ToolPermissionService> permissions,
    std::shared_ptr<CompletionRequestComposer> request_composer)
    : completion_(std::move(completion)), tools_(std::move(tools)),
      permissions_(std::move(permissions)),
      request_composer_(std::move(request_composer)) {
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
  const bool unlimited =
      request.model.tool_call_limit == domain::ModelConfig::unlimited_tool_calls;
  std::size_t invoked_count{};
  for (;;) {
    auto response = co_await completion_->Complete(request, observer);
    if (!response)
      co_return std::unexpected(std::move(response.error()));
    if (response->tool_calls.empty())
      co_return std::move(*response);

    const auto requested_count = response->tool_calls.size();
    if (!unlimited &&
        (request.model.tool_call_limit <= 0 ||
         requested_count >
             static_cast<std::size_t>(request.model.tool_call_limit) -
                 std::min(static_cast<std::size_t>(request.model.tool_call_limit),
                          invoked_count))) {
      co_return std::unexpected(CompletionError{
          .code = CompletionErrorCode::decode,
          .message = "The model exceeded its MCP tool call limit",
          .http_status = 0,
      });
    }

    request.messages.push_back(CompletionMessage::Assistant(
        response->text, std::move(response->tool_calls)));
    const auto calls = request.messages.back().tool_calls;
    for (const auto &call : calls) {
      const auto descriptor = std::ranges::find(
          tools_->Tools(), std::string_view{call.name},
          [](const RegisteredTool &tool) {
            return std::string_view{tool.name};
          });
      if (descriptor == tools_->Tools().end()) {
        request.messages.push_back(CompletionMessage::Tool(
            CompletionToolResult{.call_id = call.id,
                                 .name = call.name,
                                 .content = "Unknown runtime tool: " + call.name,
                                 .error = true}));
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
        request.messages.push_back(CompletionMessage::Tool(
            CompletionToolResult{
                .call_id = call.id,
                .name = call.name,
                .content = "This tool is not allowed in read-only mode: " +
                           call.name,
                .error = true}));
        ++invoked_count;
        continue;
      }
      if (permission == ToolPermissionDecision::review) {
        auto decision = CompletionObserver::ToolReviewDecision::reject;
        if (observer.on_tool_review) {
          decision = co_await observer.on_tool_review(
              CompletionObserver::ToolReviewRequest{
                  .call = call,
                  .can_allow_always =
                      descriptor->permanent_grant_supported &&
                      !request.permission_scope.empty(),
              });
        }
        if (decision == CompletionObserver::ToolReviewDecision::reject) {
          request.messages.push_back(CompletionMessage::Tool(
              CompletionToolResult{.call_id = call.id,
                                   .name = call.name,
                                   .content = "The user rejected this tool call.",
                                   .error = true}));
          ++invoked_count;
          continue;
        }
        if (decision ==
                CompletionObserver::ToolReviewDecision::allow_always &&
            permissions_) {
          auto remembered = co_await permissions_->RememberPermanentGrant(
              *descriptor, call, request.permission_scope);
          if (!remembered)
            co_return std::unexpected(LoopError(remembered.error().message));
        }
      }
      auto invoked = co_await tools_->Invoke(call.name, call.arguments_json);
      CompletionToolResult result{.call_id = call.id,
                                  .name = call.name,
                                  .content = {},
                                  .error = false};
      if (invoked) {
        result.content = invoked->content.empty() ? "MCP tool completed"
                                                  : std::move(invoked->content);
        result.error = invoked->error;
      } else {
        result.content = invoked.error().message;
        result.error = true;
      }
      request.messages.push_back(CompletionMessage::Tool(std::move(result)));
      ++invoked_count;
    }
  }
}

} // namespace linecode::application
