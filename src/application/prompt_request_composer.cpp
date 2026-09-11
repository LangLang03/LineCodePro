#include "application/prompt_request_composer.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <ranges>
#include <stdexcept>
#include <utility>

#include "domain/prompt_renderer.h"

namespace linecode::application {
namespace {

std::string Trimmed(std::string_view value) {
  const auto visible = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(value, visible);
  if (begin == value.end())
    return {};
  const auto end = std::ranges::find_if(value | std::views::reverse, visible);
  return std::string{begin, end.base()};
}

std::string JoinSections(std::initializer_list<std::string_view> sections) {
  std::string output;
  for (const auto section : sections) {
    const auto trimmed = Trimmed(section);
    if (trimmed.empty())
      continue;
    if (!output.empty())
      output += "\n\n";
    output += trimmed;
  }
  return output;
}

std::string_view ChatModeTemplate(PromptChatMode mode) {
  struct Entry final {
    PromptChatMode mode;
    std::string_view template_id;
  };
  static constexpr std::array entries{
      Entry{PromptChatMode::chat, "chatModeChat"},
      Entry{PromptChatMode::plan, "chatModePlan"},
      Entry{PromptChatMode::agent, "chatModeAgent"},
  };
  return std::ranges::find(entries, mode, &Entry::mode)->template_id;
}

std::string PermissionContext(std::string_view mode) {
  struct Entry final {
    std::string_view mode;
    std::string_view prompt;
  };
  static constexpr std::array entries{
      Entry{"auto", "Permission mode: automatic. Enabled tools execute without "
                    "per-call confirmation. Submit tool calls directly instead "
                    "of asking the user for execution approval."},
      Entry{"confirm", "Permission mode: confirmation. Submit tool calls "
                       "directly; the app will request approval for tools that "
                       "require it before execution."},
      Entry{"readonly", "Permission mode: read-only. Only explicitly read-only "
                        "tools are available; do not request state-changing "
                        "operations."},
  };
  const auto found = std::ranges::find(entries, mode, &Entry::mode);
  return std::string{found == entries.end() ? entries.back().prompt
                                            : found->prompt};
}

std::string ToolContext(const CompletionRequest &request,
                        const PromptAssemblyContext &context) {
  if (!Trimmed(context.tools_context).empty())
    return JoinSections(
        {PermissionContext(context.permission_mode), context.tools_context});
  std::string tools = "## Available tools";
  if (request.tools.empty()) {
    tools += "\nNo tools are available for this request.";
  } else {
    tools += "\nOnly the following injected tools are available:";
    for (const auto &tool : request.tools) {
      tools += "\n- ";
      tools += tool.name;
      if (!tool.description.empty()) {
        tools += ": ";
        tools += tool.description;
      }
    }
  }
  return JoinSections({PermissionContext(context.permission_mode), tools});
}

bool CorrespondsTo(CompletionRole role, domain::MessageRole history_role) {
  return (role == CompletionRole::user &&
          history_role == domain::MessageRole::user) ||
         (role == CompletionRole::assistant &&
          history_role == domain::MessageRole::assistant) ||
         (role == CompletionRole::tool &&
          history_role == domain::MessageRole::tool);
}

void InjectAttachmentPrompts(
    CompletionRequest &request,
    const std::vector<domain::ChatMessage> &attachment_history,
    const AttachmentPromptRenderer &renderer) {
  if (attachment_history.empty())
    return;
  std::vector<CompletionMessage> messages;
  messages.reserve(request.messages.size() + attachment_history.size());
  auto history = attachment_history.begin();
  for (auto &message : request.messages) {
    messages.push_back(std::move(message));
    if (message.role == CompletionRole::system)
      continue;
    while (history != attachment_history.end() &&
           !CorrespondsTo(message.role, history->role))
      ++history;
    if (history == attachment_history.end())
      continue;
    if (history->role == domain::MessageRole::user &&
        !history->attachments.empty()) {
      const auto prompt = renderer.Render(std::span{history, 1U});
      if (!prompt.empty())
        messages.push_back(
            CompletionMessage{.role = CompletionRole::user,
                              .content = std::move(prompt)});
    }
    ++history;
  }
  request.messages = std::move(messages);
}

} // namespace

SystemPromptComposer::SystemPromptComposer(
    std::vector<domain::PromptTemplateItem> templates) {
  templates_.reserve(templates.size());
  for (auto &item : templates)
    templates_.insert_or_assign(std::move(item.definition.id),
                                std::move(item.current_text));
}

std::string_view SystemPromptComposer::Template(std::string_view id) const {
  const auto found = templates_.find(std::string{id});
  return found == templates_.end() ? std::string_view{} : found->second;
}

std::string SystemPromptComposer::Render(
    std::string_view id,
    std::initializer_list<domain::PromptVariable> values) const {
  return domain::RenderPromptTemplate(Template(id), values);
}

std::string SystemPromptComposer::Compose(
    const CompletionRequest &request,
    const domain::AiBehaviorSettings &behavior,
    const PromptAssemblyContext &context) const {
  const auto tone = Render(behavior.tone == domain::ToneMode::chat
                               ? "toneChat"
                               : "toneCoding");
  const auto chat_mode = Render(ChatModeTemplate(context.chat_mode));
  const auto work_directory = Trimmed(context.work_directory).empty()
                                  ? std::string{}
                                  : Render(
                                        "workDirectory",
                                        {{"HOME_PATH", context.work_directory},
                                         {"LINECODE_ROOT", context.linecode_root},
                                         {"GLOBAL_SKILLS_ROOT",
                                          context.global_skills_root},
                                         {"WORKSPACE_PRIVATE_ROOT",
                                          context.workspace_private_root},
                                         {"WORKSPACE_SKILLS_ROOT",
                                          context.workspace_skills_root}});
  const auto model_identity = Trimmed(request.model.model_id).empty()
                                  ? std::string{}
                                  : Render(
                                        "modelIdentity",
                                        {{"MODEL_ID", request.model.model_id},
                                         {"MODEL_NAME", request.model.name},
                                         {"MODEL_PROVIDER",
                                          request.model.provider_label},
                                         {"MODEL_PROTOCOL",
                                          domain::ModelProtocolLabel(
                                              request.model.protocol)}});
  const auto todo = Trimmed(context.todo_state).empty()
                        ? Render("todoUsage")
                        : Render("todoState", {{"TODO_LIST", context.todo_state}});
  const auto tools = ToolContext(request, context);
  return Render(
      "systemPrompt",
      {{"TONE_CONTEXT", tone},
       {"CHAT_MODE_CONTEXT", chat_mode},
       {"WORK_DIRECTORY_CONTEXT", work_directory},
       {"LEARNING_CONTEXT", context.learning_context},
       {"MODEL_IDENTITY", model_identity},
       {"TOOLS_CONTEXT", tools},
       {"TODO_STATE", todo}});
}

PromptRequestComposer::PromptRequestComposer(
    std::shared_ptr<PromptTemplateRepository> prompt_templates,
    std::shared_ptr<AiBehaviorSettingsRepository> behavior_settings,
    std::shared_ptr<const AttachmentPromptRenderer> attachment_renderer)
    : prompt_templates_(std::move(prompt_templates)),
      behavior_settings_(std::move(behavior_settings)),
      attachment_renderer_(std::move(attachment_renderer)) {
  if (!prompt_templates_ || !behavior_settings_ || !attachment_renderer_)
    throw std::invalid_argument(
        "PromptRequestComposer requires prompt, behavior and attachment dependencies");
}

huxerui::Task<SettingsResult<CompletionRequest>>
PromptRequestComposer::Compose(CompletionRequest request,
                               PromptAssemblyContext context) {
  auto templates = co_await prompt_templates_->Load();
  if (!templates)
    co_return std::unexpected(std::move(templates.error()));
  auto behavior = co_await behavior_settings_->Load();
  if (!behavior)
    co_return std::unexpected(std::move(behavior.error()));

  const auto prompt = SystemPromptComposer{std::move(*templates)}.Compose(
      request, *behavior, context);
  InjectAttachmentPrompts(request, context.attachment_history,
                          *attachment_renderer_);
  request.messages.insert(
      request.messages.begin(),
      CompletionMessage{.role = CompletionRole::system, .content = prompt});
  request.reasoning_effort = behavior->reasoning;
  request.preserve_reasoning = behavior->preserve_reasoning;
  co_return request;
}

} // namespace linecode::application
