#pragma once

#include <memory>

#include "application/ports/completion_gateway.h"
#include "application/ports/tool_registry.h"
#include "application/prompt_request_composer.h"

namespace linecode::application {

class ToolPermissionService;

class McpCompletionLoop final {
public:
  McpCompletionLoop(std::shared_ptr<CompletionGateway> completion,
                    std::shared_ptr<ToolRegistry> tools,
                    std::shared_ptr<ToolPermissionService> permissions = {},
                    std::shared_ptr<CompletionRequestComposer> request_composer = {});

  // Executes model turns until the assistant returns no tool calls. HuxerUI's
  // owning TaskScope provides cancellation for the complete loop.
  [[nodiscard]] huxerui::Task<std::expected<CompletionResponse, CompletionError>>
  Complete(CompletionRequest request, CompletionObserver observer);
  [[nodiscard]] huxerui::Task<std::expected<CompletionResponse, CompletionError>>
  Complete(CompletionRequest request, PromptAssemblyContext prompt_context,
           CompletionObserver observer);

private:
  [[nodiscard]] huxerui::Task<std::expected<void, CompletionError>>
  PrepareTools(CompletionRequest &request);
  [[nodiscard]] huxerui::Task<std::expected<CompletionResponse, CompletionError>>
  RunPrepared(CompletionRequest request, CompletionObserver observer);

  std::shared_ptr<CompletionGateway> completion_;
  std::shared_ptr<ToolRegistry> tools_;
  std::shared_ptr<ToolPermissionService> permissions_;
  std::shared_ptr<CompletionRequestComposer> request_composer_;
};

} // namespace linecode::application
