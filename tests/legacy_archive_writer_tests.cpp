// The legacy-compatible half of an export.
//
// `DecodeLegacyArchive` reads what the legacy app writes; `EncodeLegacyArchive`
// is the matching writer. Round-tripping the real archive captured from the
// legacy app is the check that matters: if the encoder and decoder disagree,
// a legacy app importing our export silently restores less than it should.

#include "gtest_support.h"
#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <algorithm>
#include <variant>
#include <vector>

#include "infrastructure/archive_validation.h"
#include "infrastructure/linecode_zip.h"
#include "infrastructure/archive_json.h"

namespace {

namespace json = linecode::infrastructure::archive_json;
using linecode::infrastructure::DecodeLegacyArchive;
using linecode::infrastructure::EncodeLegacyArchive;
using linecode::infrastructure::ZipEntryData;

std::string ReadFixture() {
  std::ifstream input{"tests/fixtures/legacy-export-v1.linecode",
                      std::ios::binary};
  EXPECT_EXPRESSION(input && "the real legacy archive fixture must be present");
  return std::string{std::istreambuf_iterator<char>(input),
                     std::istreambuf_iterator<char>()};
}

std::vector<ZipEntryData> EntriesOf(const std::string &archive) {
  const auto *bytes = reinterpret_cast<const std::byte *>(archive.data());
  auto decoded = linecode::infrastructure::ReadLineCodeZip(
      std::span<const std::byte>{bytes, archive.size()});
  EXPECT_EXPRESSION(decoded.has_value());
  return *decoded;
}

// The fixture was produced by the legacy app itself.
void DecodesTheRealLegacyArchive() {
  const auto entries = EntriesOf(ReadFixture());
  auto decoded = DecodeLegacyArchive(entries, 0);
  EXPECT_EXPRESSION(decoded.has_value());
  EXPECT_EXPRESSION(decoded->models.size() == 1U);
  EXPECT_EXPRESSION(decoded->models.front().config.id == "lg-model-1");
  EXPECT_EXPRESSION(decoded->models.front().config.name == "Legacy Model");
  EXPECT_EXPRESSION(decoded->selected_model_id == "lg-model-1");
  EXPECT_EXPRESSION(decoded->current_conversation_id == "lg-conv-1");
  EXPECT_EXPRESSION(decoded->conversations.size() == 1U);
  EXPECT_EXPRESSION(decoded->conversations.front().id == "lg-conv-1");
  EXPECT_EXPRESSION(decoded->conversations.front().title == "Legacy Conversation");
  // Four `@linecode_*` settings travelled with the archive.
  EXPECT_EXPRESSION(decoded->settings.size() == 4U);
  EXPECT_EXPRESSION(decoded->settings.at("@linecode_chat_mode") == "agent");
}

// Re-encoding what we just decoded has to reproduce every entry the legacy
// wrote, so an export from here is loadable there.
void ReEncodingReproducesTheLegacyEntries() {
  auto decoded = DecodeLegacyArchive(EntriesOf(ReadFixture()), 0);
  EXPECT_EXPRESSION(decoded.has_value());
  const auto encoding = EncodeLegacyArchive(*decoded);

  auto parsed = json::Parse(encoding.async_storage_json);
  EXPECT_EXPRESSION(parsed.has_value());
  const auto *entries = json::AsArray(&*parsed);
  EXPECT_EXPRESSION(entries != nullptr);

  // The legacy fixture carries nine entries; re-encoding must not drop any.
  EXPECT_EXPRESSION(entries->size() == 9U);
  for (const auto key :
       {"@lineai_models", "@lineai_selected_model",
        "@lineai_current_conversation", "@lineai_conversation_list",
        "@lineai_conv_lg-conv-1", "@linecode_chat_mode",
        "@linecode_selected_project_local", "@linecode_user_agreement_accepted",
        "@linecode_user_agreement_version"}) {
    const auto found = std::ranges::find_if(*entries, [key](const auto &value) {
      const auto *object = json::AsObject(&value);
      const auto *text =
          object == nullptr ? nullptr : json::AsString(json::Find(*object, "key"));
      return text != nullptr && *text == key;
    });
    EXPECT_EXPRESSION(found != entries->end() && "missing async-storage entry");
  }

  // The conversation file is emitted under the same name and still parses.
  EXPECT_EXPRESSION(encoding.conversation_files.size() == 1U);
  EXPECT_EXPRESSION(encoding.conversation_files.front().first ==
         "conversations/lg-conv-1.json");
  auto conversation = json::Parse(encoding.conversation_files.front().second);
  EXPECT_EXPRESSION(conversation.has_value());
  const auto *object = json::AsObject(&*conversation);
  EXPECT_EXPRESSION(object != nullptr);
  EXPECT_EXPRESSION(*json::AsString(json::Find(*object, "id")) == "lg-conv-1");
  EXPECT_EXPRESSION(json::AsArray(json::Find(*object, "messages")) != nullptr);
}

// The metadata entry points at the file by name and byte size, so both have to
// agree with what we actually emit.
void ConversationMetadataMatchesTheEmittedFile() {
  auto decoded = DecodeLegacyArchive(EntriesOf(ReadFixture()), 0);
  EXPECT_EXPRESSION(decoded.has_value());
  const auto encoding = EncodeLegacyArchive(*decoded);
  const auto &content = encoding.conversation_files.front().second;

  auto parsed = json::Parse(encoding.async_storage_json);
  const auto *entries = json::AsArray(&*parsed);
  const auto *metadata = [&]() -> const json::Value * {
    for (const auto &value : *entries) {
      const auto *object = json::AsObject(&value);
      const auto *key =
          object == nullptr ? nullptr : json::AsString(json::Find(*object, "key"));
      if (key != nullptr && *key == "@lineai_conv_lg-conv-1")
        return json::Find(*object, "value");
    }
    return nullptr;
  }();
  EXPECT_EXPRESSION(metadata != nullptr);
  auto metadata_json = json::Parse(*json::AsString(metadata));
  EXPECT_EXPRESSION(metadata_json.has_value());
  const auto *object = json::AsObject(&*metadata_json);
  EXPECT_EXPRESSION(object != nullptr);
  EXPECT_EXPRESSION(*json::AsString(json::Find(*object, "storage")) == "file");
  EXPECT_EXPRESSION(*json::AsString(json::Find(*object, "fileName")) == "lg-conv-1.json");
  EXPECT_EXPRESSION(std::get<std::int64_t>(*json::Find(*object, "size")) ==
         static_cast<std::int64_t>(content.size()));
  EXPECT_EXPRESSION(std::get<std::int64_t>(*json::Find(*object, "messageCount")) == 0);
}

// An export of an empty installation still has to be a valid archive.
void EmptyDataProducesAValidAsyncStorage() {
  const linecode::application::LegacyArchiveData empty;
  const auto encoding = EncodeLegacyArchive(empty);
  auto parsed = json::Parse(encoding.async_storage_json);
  EXPECT_EXPRESSION(parsed.has_value());
  const auto *entries = json::AsArray(&*parsed);
  EXPECT_EXPRESSION(entries != nullptr);
  // The models entry is always present, exactly like the legacy builder, and
  // the conversation list closes the set.
  EXPECT_EXPRESSION(entries->size() == 2U);
  EXPECT_EXPRESSION(encoding.conversation_files.empty());
}

} // namespace

TEST(legacy_archive_writer_tests, LegacySuite) {
  DecodesTheRealLegacyArchive();
  ReEncodingReproducesTheLegacyEntries();
  ConversationMetadataMatchesTheEmittedFile();
  EmptyDataProducesAValidAsyncStorage();
  std::cout << "legacy_archive_writer_tests passed\n";
  return;
}
