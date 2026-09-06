#include <cassert>
#include <memory>
#include <string>
#include <string_view>

#include "application/chat_session.h"
#include "application/legacy_compatibility.h"
#include "application/send_message.h"
#include "infrastructure/in_memory_conversation_store.h"
#include "infrastructure/legacy_conversation_schema.h"
#include "presentation/platform_features.h"

namespace {

class RecordingConversationStore final
    : public linecode::application::ConversationStore {
public:
  [[nodiscard]] std::span<const linecode::domain::ChatMessage>
  Messages() const noexcept override {
    return messages;
  }

  [[nodiscard]] std::uint64_t AllocateMessageId() noexcept override {
    return 1;
  }

  void Append(linecode::domain::ChatMessage message) override {
    messages.push_back(std::move(message));
  }

  void Clear() override { messages.clear(); }

  [[nodiscard]] std::span<const linecode::application::ConversationSummary>
  Conversations() const noexcept override {
    return conversations;
  }

  [[nodiscard]] std::string_view
  CurrentConversationId() const noexcept override {
    return current_id;
  }

  void StartNewConversation() override { started_new = true; }
  void SelectConversation(std::string_view id) override {
    selected_id = id;
  }
  void DeleteConversation(std::string_view id) override { deleted_id = id; }

  std::vector<linecode::domain::ChatMessage> messages;
  std::vector<linecode::application::ConversationSummary> conversations{
      {.id = "conversation-1", .title = "First", .updated_at_millis = 12}};
  std::string current_id{"conversation-1"};
  std::string selected_id;
  std::string deleted_id;
  bool started_new{};
};

} // namespace

int main() {
  using linecode::application::ClassifyHistoricalTool;
  using linecode::application::HistoricalToolDisposition;
  using linecode::application::NormalizeLegacyChatMode;
  using linecode::application::SendMessage;
  using linecode::application::SendMessageError;
  using linecode::domain::ChatMode;
  using linecode::infrastructure::InMemoryConversationStore;
  namespace legacy_schema = linecode::infrastructure::legacy_schema;
  using linecode::presentation::FeatureAvailability;
  using linecode::presentation::HostPlatform;
  using linecode::presentation::PlatformFeature;

  static_assert(FeatureAvailability<PlatformFeature::keep_alive,
                                    HostPlatform::android>::value);
  static_assert(!FeatureAvailability<PlatformFeature::keep_alive,
                                     HostPlatform::windows>::value);
  static_assert(NormalizeLegacyChatMode("control") == ChatMode::agent);
  static_assert(NormalizeLegacyChatMode("plan") == ChatMode::plan);
  static_assert(ClassifyHistoricalTool("phone_screenshot") ==
                HistoricalToolDisposition::inert_generic);
  static_assert(ClassifyHistoricalTool("file_read") ==
                HistoricalToolDisposition::normal);
  static_assert(legacy_schema::user_version == 4);
  static_assert(legacy_schema::create_conversations.find("raw_json TEXT") !=
                std::string_view::npos);
  static_assert(legacy_schema::create_messages.find(
                    "UNIQUE(conversation_id, local_order)") !=
                std::string_view::npos);

  InMemoryConversationStore store;
  SendMessage send{store};

  const auto blank = send.Execute(" \t\n");
  assert(!blank.has_value());
  assert(blank.error() == SendMessageError::empty);
  assert(store.Messages().empty());

  const auto sent = send.Execute("hello");
  assert(sent.has_value());
  assert(sent->content == "hello");
  assert(store.Messages().size() == 1);
  assert(store.Messages().front().id == sent->id);

  store.Clear();
  assert(store.Messages().empty());

  store.Append({.id = 41,
                .role = linecode::domain::MessageRole::assistant,
                .content = "restored"});
  SendMessage resumed_send{store};
  const auto resumed = resumed_send.Execute("next");
  assert(resumed.has_value());
  assert(resumed->id == 42);

  linecode::application::ChatSession session{
      std::make_unique<InMemoryConversationStore>()};
  assert(session.Send("injected").has_value());
  assert(session.Messages().size() == 1);
  session.Clear();
  assert(session.Messages().empty());

  auto recording_store = std::make_unique<RecordingConversationStore>();
  auto *recording = recording_store.get();
  linecode::application::ChatSession managed{std::move(recording_store)};
  assert(managed.Conversations().size() == 1);
  assert(managed.Conversations().front().title == "First");
  assert(managed.CurrentConversationId() == "conversation-1");
  managed.StartNewConversation();
  managed.SelectConversation("conversation-1");
  managed.SelectConversation("");
  managed.DeleteConversation("conversation-1");
  managed.DeleteConversation("");
  assert(recording->started_new);
  assert(recording->selected_id == "conversation-1");
  assert(recording->deleted_id == "conversation-1");

  linecode::application::ConversationSelectionBarrier selection_barrier;
  const auto first_selection = selection_barrier.Begin("conversation-b");
  assert(selection_barrier.Matches(first_selection, "conversation-b"));
  assert(selection_barrier.DefersAppendTo("conversation-b"));
  assert(!selection_barrier.DefersAppendTo("conversation-a"));

  // A send immediately after select is tagged with this generation and must
  // defer local_order allocation until the selected history has loaded.
  assert(selection_barrier.Generation() == first_selection);
  selection_barrier.Settle(first_selection);
  assert(!selection_barrier.DefersAppendTo("conversation-b"));

  const auto selection_before_new = selection_barrier.Begin("conversation-b");
  selection_barrier.Invalidate();
  assert(!selection_barrier.Matches(selection_before_new, "conversation-b"));

  const auto selection_before_delete =
      selection_barrier.Begin("conversation-b");
  selection_barrier.Invalidate();
  assert(!selection_barrier.Matches(selection_before_delete,
                                    "conversation-b"));
}
