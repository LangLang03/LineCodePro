#include <cassert>
#include <memory>
#include <string>

#include "application/chat_session.h"
#include "application/send_message.h"
#include "application/legacy_compatibility.h"
#include "infrastructure/in_memory_conversation_store.h"
#include "presentation/platform_features.h"

int main() {
  using linecode::application::SendMessage;
  using linecode::application::SendMessageError;
  using linecode::application::HistoricalToolDisposition;
  using linecode::application::ClassifyHistoricalTool;
  using linecode::application::NormalizeLegacyChatMode;
  using linecode::domain::ChatMode;
  using linecode::infrastructure::InMemoryConversationStore;
  using linecode::presentation::FeatureAvailability;
  using linecode::presentation::HostPlatform;
  using linecode::presentation::PlatformFeature;

  static_assert(FeatureAvailability<PlatformFeature::keep_alive, HostPlatform::android>::value);
  static_assert(!FeatureAvailability<PlatformFeature::keep_alive, HostPlatform::windows>::value);
  static_assert(NormalizeLegacyChatMode("control") == ChatMode::agent);
  static_assert(NormalizeLegacyChatMode("plan") == ChatMode::plan);
  static_assert(ClassifyHistoricalTool("phone_screenshot") == HistoricalToolDisposition::inert_generic);
  static_assert(ClassifyHistoricalTool("file_read") == HistoricalToolDisposition::normal);

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

  linecode::application::ChatSession session{std::make_unique<InMemoryConversationStore>()};
  assert(session.Send("injected").has_value());
  assert(session.Messages().size() == 1);
  session.Clear();
  assert(session.Messages().empty());
}
