#include "gtest_support.h"
#include <memory>
#include <optional>
#include <ranges>
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

  [[nodiscard]] std::optional<linecode::domain::ChatMessage>
  RecallUserMessage(std::uint64_t message_id) override {
    const auto found = std::ranges::find(messages, message_id,
                                         &linecode::domain::ChatMessage::id);
    if (found == messages.end() ||
        found->role != linecode::domain::MessageRole::user) {
      return std::nullopt;
    }
    auto recalled = *found;
    messages.erase(found, messages.end());
    return recalled;
  }

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

TEST(application_tests, LegacySuite) {
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
  // "Windows hides the Android-only entries": every gated feature resolves at
  // compile time, so on Windows (and any other host) `if constexpr` discards
  // the Android-only service and UI branches entirely. These assertions are
  // what keeps a newly added feature from silently defaulting to visible.
  static_assert(FeatureAvailability<PlatformFeature::keep_alive,
                                    HostPlatform::android>::value);
  static_assert(FeatureAvailability<PlatformFeature::termux,
                                    HostPlatform::android>::value);
  static_assert(FeatureAvailability<PlatformFeature::terminal_provider,
                                    HostPlatform::android>::value);
  static_assert(FeatureAvailability<PlatformFeature::android_storage_permission,
                                    HostPlatform::android>::value);
  static_assert(FeatureAvailability<PlatformFeature::workspace_directory_share,
                                    HostPlatform::android>::value);
  static_assert(!FeatureAvailability<PlatformFeature::termux,
                                     HostPlatform::windows>::value);
  static_assert(!FeatureAvailability<PlatformFeature::terminal_provider,
                                     HostPlatform::windows>::value);
  static_assert(!FeatureAvailability<PlatformFeature::android_storage_permission,
                                     HostPlatform::windows>::value);
  static_assert(!FeatureAvailability<PlatformFeature::workspace_directory_share,
                                     HostPlatform::windows>::value);
  // A non-Android, non-Windows host (such as the test runner) behaves like
  // Windows here: nothing Android-only is exposed.
  static_assert(!FeatureAvailability<PlatformFeature::keep_alive,
                                     HostPlatform::other>::value);
  static_assert(!FeatureAvailability<PlatformFeature::termux,
                                     HostPlatform::other>::value);
  static_assert(!FeatureAvailability<PlatformFeature::terminal_provider,
                                     HostPlatform::other>::value);
  static_assert(!FeatureAvailability<PlatformFeature::android_storage_permission,
                                     HostPlatform::other>::value);
  static_assert(!FeatureAvailability<PlatformFeature::workspace_directory_share,
                                     HostPlatform::other>::value);
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
  EXPECT_EXPRESSION(!blank.has_value());
  EXPECT_EXPRESSION(blank.error() == SendMessageError::empty);
  EXPECT_EXPRESSION(store.Messages().empty());

  const auto sent = send.Execute("hello");
  EXPECT_EXPRESSION(sent.has_value());
  EXPECT_EXPRESSION(sent->content == "hello");
  EXPECT_EXPRESSION(store.Messages().size() == 1);
  EXPECT_EXPRESSION(store.Messages().front().id == sent->id);

  store.Clear();
  EXPECT_EXPRESSION(store.Messages().empty());

  store.Append({.id = 41,
                .role = linecode::domain::MessageRole::assistant,
                .content = "restored",
                .attachments = {}});
  SendMessage resumed_send{store};
  const auto resumed = resumed_send.Execute("next");
  EXPECT_EXPRESSION(resumed.has_value());
  EXPECT_EXPRESSION(resumed->id == 42);

  store.Clear();
  store.Append({.id = 50,
                .role = linecode::domain::MessageRole::user,
                .content = "try again",
                .attachments = {}});
  store.Append({.id = 51,
                .role = linecode::domain::MessageRole::assistant,
                .content = "answer",
                .attachments = {}});
  const auto recalled = store.RecallUserMessage(50);
  EXPECT_EXPRESSION(recalled && recalled->content == "try again");
  EXPECT_EXPRESSION(store.Messages().empty());
  EXPECT_EXPRESSION(!store.RecallUserMessage(51));

  linecode::application::ChatSession session{
      std::make_unique<InMemoryConversationStore>()};
  EXPECT_EXPRESSION(session.Send("injected").has_value());
  EXPECT_EXPRESSION(session.Messages().size() == 1);
  session.Clear();
  EXPECT_EXPRESSION(session.Messages().empty());

  // An attached image with no text still produces a turn, using the legacy
  // placeholder so the model gets a non-empty prompt
  // (`ChatInteractionController.java:160-162`).
  {
    linecode::domain::ChatImage image;
    image.name = "photo.jpg";
    image.mime_type = "image/jpeg";
    image.base64 = "QUJD";
    linecode::application::ChatSession with_image{
        std::make_unique<InMemoryConversationStore>()};
    const auto sent = with_image.Send("", {}, image);
    EXPECT_EXPRESSION(sent.has_value());
    EXPECT_EXPRESSION(sent->content == "已附加图片：photo.jpg");
    EXPECT_EXPRESSION(sent->image.has_value());
    EXPECT_EXPRESSION(sent->image->base64 == "QUJD");

    // A named-less image falls back to the shorter notice.
    auto anonymous = image;
    anonymous.name.clear();
    const auto unnamed = with_image.Send("  ", {}, anonymous);
    EXPECT_EXPRESSION(unnamed.has_value());
    EXPECT_EXPRESSION(unnamed->content == "已附加图片");

    // Text wins when present, and an unusable image is dropped rather than
    // producing an empty payload.
    const auto with_text = with_image.Send("look", {}, image);
    EXPECT_EXPRESSION(with_text.has_value());
    EXPECT_EXPRESSION(with_text->content == "look");
    auto broken = image;
    broken.base64.clear();
    const auto dropped = with_image.Send("plain", {}, broken);
    EXPECT_EXPRESSION(dropped.has_value());
    EXPECT_EXPRESSION(!dropped->image.has_value());
    // Nothing at all is still rejected.
    EXPECT_EXPRESSION(!with_image.Send("", {}, broken).has_value());
  }

  auto recording_store = std::make_unique<RecordingConversationStore>();
  auto *recording = recording_store.get();
  linecode::application::ChatSession managed{std::move(recording_store)};
  EXPECT_EXPRESSION(managed.Conversations().size() == 1);
  EXPECT_EXPRESSION(managed.Conversations().front().title == "First");
  EXPECT_EXPRESSION(managed.CurrentConversationId() == "conversation-1");
  managed.StartNewConversation();
  managed.DeleteCurrentConversation();
  EXPECT_EXPRESSION(recording->deleted_id == "conversation-1");
  recording->deleted_id.clear();
  recording->current_id.clear();
  managed.DeleteCurrentConversation();
  EXPECT_EXPRESSION(recording->deleted_id.empty());
  recording->current_id = "conversation-1";
  managed.SelectConversation("conversation-1");
  managed.SelectConversation("");
  managed.DeleteConversation("conversation-1");
  managed.DeleteConversation("");
  EXPECT_EXPRESSION(recording->started_new);
  EXPECT_EXPRESSION(recording->selected_id == "conversation-1");
  EXPECT_EXPRESSION(recording->deleted_id == "conversation-1");

  linecode::application::ConversationSelectionBarrier selection_barrier;
  const auto first_selection = selection_barrier.Begin("conversation-b");
  EXPECT_EXPRESSION(selection_barrier.Matches(first_selection, "conversation-b"));
  EXPECT_EXPRESSION(selection_barrier.DefersAppendTo("conversation-b"));
  EXPECT_EXPRESSION(!selection_barrier.DefersAppendTo("conversation-a"));

  // A send immediately after select is tagged with this generation and must
  // defer local_order allocation until the selected history has loaded.
  EXPECT_EXPRESSION(selection_barrier.Generation() == first_selection);
  selection_barrier.Settle(first_selection);
  EXPECT_EXPRESSION(!selection_barrier.DefersAppendTo("conversation-b"));

  const auto selection_before_new = selection_barrier.Begin("conversation-b");
  selection_barrier.Invalidate();
  EXPECT_EXPRESSION(!selection_barrier.Matches(selection_before_new, "conversation-b"));

  const auto selection_before_delete =
      selection_barrier.Begin("conversation-b");
  selection_barrier.Invalidate();
  EXPECT_EXPRESSION(!selection_barrier.Matches(selection_before_delete,
                                    "conversation-b"));
}
