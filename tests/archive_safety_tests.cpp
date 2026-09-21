#include <algorithm>
#include "gtest_support.h"
#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <zlib.h>

#include "infrastructure/archive_json.h"
#include "infrastructure/archive_validation.h"
#include "infrastructure/linecode_zip.h"

namespace {

using huxerui::Bytes;
using linecode::infrastructure::ReadLineCodeZip;
using linecode::infrastructure::ValidateArchiveManifest;
using linecode::infrastructure::ValidateDatabaseSnapshot;
using linecode::infrastructure::WriteLineCodeZip;
using linecode::infrastructure::ZipEntryData;
namespace json = linecode::infrastructure::archive_json;

void Put16(Bytes &bytes, std::uint16_t value) {
  bytes.push_back(static_cast<std::byte>(value & 0xFFU));
  bytes.push_back(static_cast<std::byte>((value >> 8U) & 0xFFU));
}

void Put32(Bytes &bytes, std::uint32_t value) {
  Put16(bytes, static_cast<std::uint16_t>(value));
  Put16(bytes, static_cast<std::uint16_t>(value >> 16U));
}

void PutText(Bytes &bytes, std::string_view text) {
  std::ranges::transform(text, std::back_inserter(bytes),
                         [](char value) { return static_cast<std::byte>(value); });
}

std::size_t FindSignature(const Bytes &bytes, std::uint32_t signature) {
  for (std::size_t offset = 0; offset + 4U <= bytes.size(); ++offset) {
    const auto value = std::to_integer<unsigned>(bytes[offset]) |
                       (std::to_integer<unsigned>(bytes[offset + 1]) << 8U) |
                       (std::to_integer<unsigned>(bytes[offset + 2]) << 16U) |
                       (std::to_integer<unsigned>(bytes[offset + 3]) << 24U);
    if (value == signature) {
      return offset;
    }
  }
  EXPECT_EXPRESSION(false);
  return 0;
}

Bytes Text(std::string_view text) {
  Bytes bytes;
  PutText(bytes, text);
  return bytes;
}

Bytes DeflatedZip(std::string_view name, std::string_view content,
                  bool data_descriptor) {
  std::vector<unsigned char> compressed(compressBound(content.size()));
  z_stream stream{};
  stream.next_in = reinterpret_cast<Bytef *>(const_cast<char *>(content.data()));
  stream.avail_in = static_cast<uInt>(content.size());
  stream.next_out = compressed.data();
  stream.avail_out = static_cast<uInt>(compressed.size());
  EXPECT_EXPRESSION(deflateInit2(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -MAX_WBITS,
                      8, Z_DEFAULT_STRATEGY) == Z_OK);
  EXPECT_EXPRESSION(deflate(&stream, Z_FINISH) == Z_STREAM_END);
  EXPECT_EXPRESSION(deflateEnd(&stream) == Z_OK);
  compressed.resize(stream.total_out);

  const auto checksum = static_cast<std::uint32_t>(
      crc32(0, reinterpret_cast<const Bytef *>(content.data()),
            static_cast<uInt>(content.size())));
  const auto compressed_size = static_cast<std::uint32_t>(compressed.size());
  const auto size = static_cast<std::uint32_t>(content.size());
  const std::uint16_t flags = data_descriptor ? 0x0808U : 0x0800U;
  Bytes bytes;
  Put32(bytes, 0x04034B50U);
  Put16(bytes, 20);
  Put16(bytes, flags);
  Put16(bytes, 8);
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put32(bytes, data_descriptor ? 0U : checksum);
  Put32(bytes, data_descriptor ? 0U : compressed_size);
  Put32(bytes, data_descriptor ? 0U : size);
  Put16(bytes, static_cast<std::uint16_t>(name.size()));
  Put16(bytes, 0);
  PutText(bytes, name);
  for (const auto value : compressed) {
    bytes.push_back(static_cast<std::byte>(value));
  }
  if (data_descriptor) {
    Put32(bytes, 0x08074B50U);
    Put32(bytes, checksum);
    Put32(bytes, compressed_size);
    Put32(bytes, size);
  }
  const auto central_offset = static_cast<std::uint32_t>(bytes.size());
  Put32(bytes, 0x02014B50U);
  Put16(bytes, 20);
  Put16(bytes, 20);
  Put16(bytes, flags);
  Put16(bytes, 8);
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put32(bytes, checksum);
  Put32(bytes, compressed_size);
  Put32(bytes, size);
  Put16(bytes, static_cast<std::uint16_t>(name.size()));
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put32(bytes, 0);
  Put32(bytes, 0);
  PutText(bytes, name);
  const auto central_size = static_cast<std::uint32_t>(bytes.size()) -
                            central_offset;
  Put32(bytes, 0x06054B50U);
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put16(bytes, 1);
  Put16(bytes, 1);
  Put32(bytes, central_size);
  Put32(bytes, central_offset);
  Put16(bytes, 0);
  return bytes;
}

void TestZipRoundTripAndLegacyDeflate() {
  const std::vector entries{
      ZipEntryData{"manifest.json", Text("{}")},
      ZipEntryData{"home/a.txt", Text("alpha")},
  };
  const auto encoded = WriteLineCodeZip(entries);
  EXPECT_EXPRESSION(encoded);
  const auto decoded = ReadLineCodeZip(*encoded);
  EXPECT_EXPRESSION(decoded && *decoded == entries);

  for (const bool descriptor : {false, true}) {
    const auto legacy = DeflatedZip("database.json", "legacy", descriptor);
    const auto legacy_decoded = ReadLineCodeZip(legacy);
    EXPECT_EXPRESSION(legacy_decoded && legacy_decoded->size() == 1);
    EXPECT_EXPRESSION(legacy_decoded->front().content == Text("legacy"));
  }
  const auto empty = DeflatedZip("empty", "", true);
  const auto empty_decoded = ReadLineCodeZip(empty);
  EXPECT_EXPRESSION(empty_decoded && empty_decoded->front().content.empty());
}

void TestZipRejectsTraversalAndTampering() {
  EXPECT_EXPRESSION(!linecode::infrastructure::IsSafeArchivePath("../escape"));
  EXPECT_EXPRESSION(!linecode::infrastructure::IsSafeArchivePath("a\\b"));
  EXPECT_EXPRESSION(!linecode::infrastructure::IsSafeArchivePath("C:/escape"));
  EXPECT_EXPRESSION(!linecode::infrastructure::IsSafeArchivePath("home/C:/escape"));

  std::string maximum_depth{"home"};
  for (std::size_t depth = 1; depth <
                              linecode::infrastructure::kMaximumArchivePathDepth;
       ++depth) {
    maximum_depth += "/d";
  }
  EXPECT_EXPRESSION(linecode::infrastructure::IsSafeArchivePath(maximum_depth));
  EXPECT_EXPRESSION(!linecode::infrastructure::IsSafeArchivePath(maximum_depth + "/d"));

  auto encoded = WriteLineCodeZip(
      std::vector{ZipEntryData{"home/a.txt", Text("alpha")}});
  EXPECT_EXPRESSION(encoded);
  auto crc_corrupt = *encoded;
  const auto central = FindSignature(crc_corrupt, 0x02014B50U);
  crc_corrupt[central + 16U] ^= std::byte{1};
  EXPECT_EXPRESSION(!ReadLineCodeZip(crc_corrupt));

  auto local_name_corrupt = *encoded;
  local_name_corrupt[30] = std::byte{'x'};
  EXPECT_EXPRESSION(!ReadLineCodeZip(local_name_corrupt));

  auto trailing = *encoded;
  trailing.push_back(std::byte{0});
  EXPECT_EXPRESSION(!ReadLineCodeZip(trailing));

  auto multi_disk = *encoded;
  const auto end = FindSignature(multi_disk, 0x06054B50U);
  multi_disk[end + 4U] = std::byte{1};
  EXPECT_EXPRESSION(!ReadLineCodeZip(multi_disk));

  auto bomb = *encoded;
  const auto bomb_central = FindSignature(bomb, 0x02014B50U);
  constexpr std::uint32_t too_large = 128U * 1024U * 1024U + 1U;
  for (unsigned index = 0; index < 4; ++index) {
    bomb[bomb_central + 24U + index] =
        static_cast<std::byte>((too_large >> (index * 8U)) & 0xFFU);
  }
  EXPECT_EXPRESSION(!ReadLineCodeZip(bomb));
}

void TestManifestValidation() {
  constexpr std::string_view valid = R"({
    "format":"linecode","formatVersion":1,"container":"zip",
    "createdAt":123,"database":true,
    "workspaceRoots":["home","project","skills"]
  })";
  const auto manifest = ValidateArchiveManifest(valid);
  EXPECT_EXPRESSION(manifest && manifest->contains_database);
  EXPECT_EXPRESSION(!ValidateArchiveManifest(R"({"format":"other"})"));
  EXPECT_EXPRESSION(!ValidateArchiveManifest(R"({
    "format":"linecode","formatVersion":2,"container":"zip",
    "createdAt":123,"database":true,"workspaceRoots":[]
  })"));
  EXPECT_EXPRESSION(!ValidateArchiveManifest(R"({
    "format":"linecode","formatVersion":1,"container":"zip",
    "createdAt":123,"database":true,"workspaceRoots":["../home"]
  })"));
}

void TestTypedDatabaseCells() {
  constexpr std::string_view valid = R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["key","value","weight","gone"],
      "rows":[{"key":{"type":"string","value":"name"},
               "value":{"type":"blob","value":"AQI="},
               "weight":{"type":"float","value":1.5},
               "gone":{"type":"null"}}]}}
  })";
  EXPECT_EXPRESSION(ValidateDatabaseSnapshot(valid, 4));
  EXPECT_EXPRESSION(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":5,"tables":{}
  })", 4));
  EXPECT_EXPRESSION(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["key"],"rows":[
      {"key":{"type":"integer","value":"1"}}]}}}
  })", 4));
  EXPECT_EXPRESSION(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["key","value"],"rows":[
      {"key":{"type":"string","value":"a"}}]}}}
  })", 4));
  EXPECT_EXPRESSION(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["value"],"rows":[
      {"value":{"type":"blob","value":"%%%="}}]}}}
  })", 4));
}

json::Value StorageEntry(std::string key, std::string value) {
  return json::Object{{"key", std::move(key)}, {"value", std::move(value)}};
}

std::vector<ZipEntryData> LegacyFixture() {
  json::Array models{
      json::Object{{"id", "m-openai"},
                   {"name", "OpenAI"},
                   {"protocolType", "openai"},
                   {"baseUrl", "https://example.test/v1"},
                   {"apiKey", "legacy-secret"},
                   {"modelId", "gpt-test"},
                   {"tool_call_limit", std::int64_t{18}},
                   {"compression_model_enabled", true},
                   {"compression_model_auto", false},
                   {"compression_model_id", " compact "},
                   {"context_size", std::int64_t{32000}}},
      json::Object{{"id", ""}, {"name", "ignored"}},
  };
  json::Array messages{
      json::Object{{"id", "m1"},
                   {"role", "assistant"},
                   {"content", "answer"},
                   {"reasoning", "legacy reasoning"},
                   {"timestamp", std::int64_t{102}},
                   {"hidden", false}},
      json::Object{{"role", "unexpected"},
                   {"content", "question"},
                   {"timestamp", std::int64_t{103}}},
  };
  json::Value conversation = json::Object{
      {"id", "c1"},          {"title", "Legacy chat"},
      {"projectId", "p1"},  {"createdAt", std::int64_t{100}},
      {"updatedAt", std::int64_t{110}},
      {"messages", std::move(messages)},
  };
  json::Array list{json::Object{{"id", "c1"}}};
  json::Array storage{
      StorageEntry("@lineai_models", json::Serialize(models)),
      StorageEntry("@lineai_selected_model", "m-openai"),
      StorageEntry("@lineai_current_conversation", "c1"),
      StorageEntry("@lineai_conversation_list", json::Serialize(list)),
      StorageEntry("@lineai_conv_c1",
                   json::Serialize(json::Object{{"storage", "file"},
                                                {"fileName", "../escape"}})),
      StorageEntry("@linecode_chat_mode", "agent"),
      StorageEntry("unrelated", "ignored"),
  };
  return {
      ZipEntryData{"async-storage.json", Text(json::Serialize(storage))},
      ZipEntryData{"conversations/c1.json.bak",
                   Text(json::Serialize(conversation))},
  };
}

void TestLegacyAsyncStorageFixture() {
  const auto decoded = linecode::infrastructure::DecodeLegacyArchive(
      LegacyFixture(), 999);
  EXPECT_EXPRESSION(decoded);
  EXPECT_EXPRESSION(decoded->models.size() == 1);
  const auto &model = decoded->models.front().config;
  EXPECT_EXPRESSION(model.id == "m-openai");
  EXPECT_EXPRESSION(model.api_key == "legacy-secret");
  EXPECT_EXPRESSION(model.tool_call_limit == 18);
  EXPECT_EXPRESSION(model.compression_model_enabled);
  EXPECT_EXPRESSION(!model.compression_model_auto);
  EXPECT_EXPRESSION(model.compression_model_id == "compact");
  EXPECT_EXPRESSION(model.context_size == 32000);
  EXPECT_EXPRESSION(decoded->selected_model_id == "m-openai");
  EXPECT_EXPRESSION(decoded->settings.size() == 1);
  EXPECT_EXPRESSION(decoded->settings.at("@linecode_chat_mode") == "agent");
  EXPECT_EXPRESSION(decoded->conversations.size() == 1);
  const auto &conversation = decoded->conversations.front();
  EXPECT_EXPRESSION(conversation.id == "c1");
  EXPECT_EXPRESSION(conversation.title == "Legacy chat");
  EXPECT_EXPRESSION(conversation.created_at == 100);
  EXPECT_EXPRESSION(conversation.messages.size() == 2);
  EXPECT_EXPRESSION(conversation.messages[0].id == "c1:m1");
  EXPECT_EXPRESSION(conversation.messages[0].role == "assistant");
  EXPECT_EXPRESSION(conversation.messages[0].reasoning_content == "legacy reasoning");
  EXPECT_EXPRESSION(conversation.messages[1].id == "imported_1");
  EXPECT_EXPRESSION(conversation.messages[1].role == "user");
  EXPECT_EXPRESSION(decoded->current_conversation_id == "c1");
}

void TestLegacyChunkedConversationFixture() {
  const std::string conversation = json::Serialize(json::Object{
      {"id", "chunked"},
      {"messages", json::Array{json::Object{{"content", "joined"}}}},
  });
  const auto midpoint = conversation.size() / 2U;
  json::Array storage{
      StorageEntry("@lineai_conv_chunked",
                   json::Serialize(json::Object{{"chunked", true},
                                                {"chunks", std::int64_t{2}}})),
      StorageEntry("@lineai_conv_chunk_chunked_0",
                   conversation.substr(0, midpoint)),
      StorageEntry("@lineai_conv_chunk_chunked_1", conversation.substr(midpoint)),
  };
  const std::vector entries{
      ZipEntryData{"async-storage.json", Text(json::Serialize(storage))}};
  const auto decoded =
      linecode::infrastructure::DecodeLegacyArchive(entries, 777);
  EXPECT_EXPRESSION(decoded && decoded->conversations.size() == 1);
  EXPECT_EXPRESSION(decoded->conversations.front().created_at == 777);
  EXPECT_EXPRESSION(decoded->conversations.front().messages.front().content == "joined");
}

void TestInvalidLegacyArchiveDoesNotReachDatabaseBoundary() {
  struct RecordingDatabase final {
    bool mutated{};
    void Import(const linecode::application::LegacyArchiveData &) {
      mutated = true;
    }
  } database;
  json::Array storage{
      StorageEntry("@lineai_conv_broken",
                   json::Serialize(json::Object{{"storage", "file"},
                                                {"fileName", "missing.json"}})),
  };
  const std::vector entries{
      ZipEntryData{"async-storage.json", Text(json::Serialize(storage))}};
  const auto staged =
      linecode::infrastructure::DecodeLegacyArchive(entries, 123);
  if (staged)
    database.Import(*staged);
  EXPECT_EXPRESSION(!staged);
  EXPECT_EXPRESSION(!database.mutated);

  json::Array chunked{
      StorageEntry("@lineai_conv_broken",
                   json::Serialize(json::Object{{"chunked", true},
                                                {"chunks", std::int64_t{1}}})),
  };
  const std::vector missing_chunk{
      ZipEntryData{"async-storage.json", Text(json::Serialize(chunked))}};
  EXPECT_EXPRESSION(!linecode::infrastructure::DecodeLegacyArchive(missing_chunk, 123));
  EXPECT_EXPRESSION(!database.mutated);
}

} // namespace

TEST(archive_safety_tests, LegacySuite) {
  TestZipRoundTripAndLegacyDeflate();
  TestZipRejectsTraversalAndTampering();
  TestManifestValidation();
  TestTypedDatabaseCells();
  TestLegacyAsyncStorageFixture();
  TestLegacyChunkedConversationFixture();
  TestInvalidLegacyArchiveDoesNotReachDatabaseBoundary();
}
