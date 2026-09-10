#include <algorithm>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <zlib.h>

#include "infrastructure/archive_validation.h"
#include "infrastructure/linecode_zip.h"

namespace {

using huxerui::Bytes;
using linecode::infrastructure::ReadLineCodeZip;
using linecode::infrastructure::ValidateArchiveManifest;
using linecode::infrastructure::ValidateDatabaseSnapshot;
using linecode::infrastructure::WriteLineCodeZip;
using linecode::infrastructure::ZipEntryData;

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
  assert(false);
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
  assert(deflateInit2(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -MAX_WBITS,
                      8, Z_DEFAULT_STRATEGY) == Z_OK);
  assert(deflate(&stream, Z_FINISH) == Z_STREAM_END);
  assert(deflateEnd(&stream) == Z_OK);
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
  assert(encoded);
  const auto decoded = ReadLineCodeZip(*encoded);
  assert(decoded && *decoded == entries);

  for (const bool descriptor : {false, true}) {
    const auto legacy = DeflatedZip("database.json", "legacy", descriptor);
    const auto legacy_decoded = ReadLineCodeZip(legacy);
    assert(legacy_decoded && legacy_decoded->size() == 1);
    assert(legacy_decoded->front().content == Text("legacy"));
  }
  const auto empty = DeflatedZip("empty", "", true);
  const auto empty_decoded = ReadLineCodeZip(empty);
  assert(empty_decoded && empty_decoded->front().content.empty());
}

void TestZipRejectsTraversalAndTampering() {
  assert(!linecode::infrastructure::IsSafeArchivePath("../escape"));
  assert(!linecode::infrastructure::IsSafeArchivePath("a\\b"));
  assert(!linecode::infrastructure::IsSafeArchivePath("C:/escape"));
  assert(!linecode::infrastructure::IsSafeArchivePath("home/C:/escape"));

  auto encoded = WriteLineCodeZip(
      std::vector{ZipEntryData{"home/a.txt", Text("alpha")}});
  assert(encoded);
  auto crc_corrupt = *encoded;
  const auto central = FindSignature(crc_corrupt, 0x02014B50U);
  crc_corrupt[central + 16U] ^= std::byte{1};
  assert(!ReadLineCodeZip(crc_corrupt));

  auto local_name_corrupt = *encoded;
  local_name_corrupt[30] = std::byte{'x'};
  assert(!ReadLineCodeZip(local_name_corrupt));

  auto trailing = *encoded;
  trailing.push_back(std::byte{0});
  assert(!ReadLineCodeZip(trailing));

  auto multi_disk = *encoded;
  const auto end = FindSignature(multi_disk, 0x06054B50U);
  multi_disk[end + 4U] = std::byte{1};
  assert(!ReadLineCodeZip(multi_disk));

  auto bomb = *encoded;
  const auto bomb_central = FindSignature(bomb, 0x02014B50U);
  constexpr std::uint32_t too_large = 128U * 1024U * 1024U + 1U;
  for (unsigned index = 0; index < 4; ++index) {
    bomb[bomb_central + 24U + index] =
        static_cast<std::byte>((too_large >> (index * 8U)) & 0xFFU);
  }
  assert(!ReadLineCodeZip(bomb));
}

void TestManifestValidation() {
  constexpr std::string_view valid = R"({
    "format":"linecode","formatVersion":1,"container":"zip",
    "createdAt":123,"database":true,
    "workspaceRoots":["home","project","skills"]
  })";
  const auto manifest = ValidateArchiveManifest(valid);
  assert(manifest && manifest->contains_database);
  assert(!ValidateArchiveManifest(R"({"format":"other"})"));
  assert(!ValidateArchiveManifest(R"({
    "format":"linecode","formatVersion":2,"container":"zip",
    "createdAt":123,"database":true,"workspaceRoots":[]
  })"));
  assert(!ValidateArchiveManifest(R"({
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
  assert(ValidateDatabaseSnapshot(valid, 4));
  assert(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":5,"tables":{}
  })", 4));
  assert(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["key"],"rows":[
      {"key":{"type":"integer","value":"1"}}]}}}
  })", 4));
  assert(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["key","value"],"rows":[
      {"key":{"type":"string","value":"a"}}]}}}
  })", 4));
  assert(!ValidateDatabaseSnapshot(R"({
    "format":"linecode-database","schemaVersion":4,
    "tables":{"settings":{"columns":["value"],"rows":[
      {"value":{"type":"blob","value":"%%%="}}]}}}
  })", 4));
}

} // namespace

int main() {
  TestZipRoundTripAndLegacyDeflate();
  TestZipRejectsTraversalAndTampering();
  TestManifestValidation();
  TestTypedDatabaseCells();
}
