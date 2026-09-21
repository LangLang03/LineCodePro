#pragma once

#include <cstdint>
#include <functional>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include <huxerui/huxerui.h>

#include "application/output_settings.h"
#include "application/ports/diff_store.h"
#include "domain/app_state.h"
#include "domain/diff_lines.h"
#include "presentation/components/tutorial_markdown.h"

namespace linecode::application {
class AgentResultReader;
} // namespace linecode::application

namespace linecode::presentation::chat_timeline {

struct Settings final {
  bool code_wrap_enabled{};
  bool process_auto_expand{};
  bool thinking_auto_expand{};
  bool thinking_scroll{true};
  bool preserve_reasoning{};
  application::BrowserMode browser_mode{application::BrowserMode::builtin};
  bool browser_javascript_enabled{};
  bool allow_any_http{};
};

struct DiffEntry final {
  domain::DiffLines lines;
  std::string review_state;
  std::string review_message;
  bool created{};
  bool available{true};
};

using DiffCache = std::map<std::string, DiffEntry>;

struct ToolRendererContext final {
  std::shared_ptr<const DiffCache> diff_cache;
  std::function<void(std::string diff_id)> on_request_diff;
  std::function<void(std::string tool_call_id, std::string diff_id,
                     std::string state)>
      on_review;
  std::shared_ptr<const application::AgentResultReader> agent_results;
  huxerui::State<std::vector<std::string>> toggled_timeline;
};

[[nodiscard]] huxerui::Task<void>
LoadDiffs(std::shared_ptr<application::DiffStore> store,
          huxerui::State<std::shared_ptr<DiffCache>> cache,
          std::shared_ptr<std::set<std::string>> pending,
          std::vector<std::string> wanted);

[[nodiscard]] huxerui::View
AssistantMarkdown(std::string_view markdown, bool code_wrap,
                  const TutorialMarkdownLinkHandler &on_link = {},
                  const TutorialMarkdownCopyHandler &on_copy = {});

[[nodiscard]] huxerui::View
CompactProgressBlock(const domain::ChatMessage &message,
                     const std::string &label);

[[nodiscard]] huxerui::View
AssistantTimeline(const domain::ChatMessage &message, bool live,
                  const Settings &settings,
                  huxerui::State<std::vector<std::string>> toggled,
                  const TutorialMarkdownLinkHandler &on_link,
                  const TutorialMarkdownCopyHandler &on_copy,
                  const ToolRendererContext &context,
                  std::string_view compact_label);

[[nodiscard]] huxerui::View
ChangedFilesBlock(const domain::ChatMessage &message,
                  std::int64_t stable_turn_id,
                  huxerui::State<std::vector<std::string>> toggled,
                  const TutorialMarkdownLinkHandler &on_link,
                  const TutorialMarkdownCopyHandler &on_copy,
                  const ToolRendererContext &context);

[[nodiscard]] bool HasAssistantTurnProcess(const domain::ChatMessage &message);

} // namespace linecode::presentation::chat_timeline
