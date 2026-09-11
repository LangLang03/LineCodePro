#include "infrastructure/linecode_zip.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include <zlib.h>

namespace linecode::infrastructure {
namespace {

constexpr std::uint32_t kLocalHeader = 0x04034B50U;
constexpr std::uint32_t kCentralHeader = 0x02014B50U;
constexpr std::uint32_t kEndHeader = 0x06054B50U;
constexpr std::size_t kMaximumEntries = 4096;
constexpr std::size_t kMaximumEntryBytes = 128U * 1024U * 1024U;
constexpr std::size_t kMaximumArchiveBytes = 512U * 1024U * 1024U;

void Put16(huxerui::Bytes &output, std::uint16_t value) {
  output.push_back(static_cast<std::byte>(value & 0xFFU));
  output.push_back(static_cast<std::byte>((value >> 8U) & 0xFFU));
}

void Put32(huxerui::Bytes &output, std::uint32_t value) {
  Put16(output, static_cast<std::uint16_t>(value & 0xFFFFU));
  Put16(output, static_cast<std::uint16_t>((value >> 16U) & 0xFFFFU));
}

void PutText(huxerui::Bytes &output, std::string_view text) {
  for (const unsigned char value : text) {
    output.push_back(static_cast<std::byte>(value));
  }
}

void PutBytes(huxerui::Bytes &output, std::span<const std::byte> bytes) {
  output.insert(output.end(), bytes.begin(), bytes.end());
}

std::uint16_t Read16(std::span<const std::byte> bytes, std::size_t offset) {
  return static_cast<std::uint16_t>(std::to_integer<unsigned>(bytes[offset]) |
                                    (std::to_integer<unsigned>(bytes[offset + 1])
                                     << 8U));
}

std::uint32_t Read32(std::span<const std::byte> bytes, std::size_t offset) {
  return static_cast<std::uint32_t>(Read16(bytes, offset)) |
         (static_cast<std::uint32_t>(Read16(bytes, offset + 2)) << 16U);
}

bool HasRange(std::span<const std::byte> bytes, std::size_t offset,
              std::size_t length) {
  return offset <= bytes.size() && length <= bytes.size() - offset;
}

std::uint32_t Checksum(std::span<const std::byte> bytes) {
  return static_cast<std::uint32_t>(
      crc32(0, reinterpret_cast<const Bytef *>(bytes.data()),
            static_cast<uInt>(bytes.size())));
}

std::expected<huxerui::Bytes, ZipError>
Inflate(std::span<const std::byte> compressed, std::size_t output_size) {
  huxerui::Bytes output(output_size);
  std::byte empty_output{};
  z_stream stream{};
  stream.next_in = reinterpret_cast<Bytef *>(
      const_cast<std::byte *>(compressed.data()));
  stream.avail_in = static_cast<uInt>(compressed.size());
  stream.next_out = reinterpret_cast<Bytef *>(
      output.empty() ? &empty_output : output.data());
  stream.avail_out = static_cast<uInt>(output.empty() ? 1U : output.size());
  if (inflateInit2(&stream, -MAX_WBITS) != Z_OK) {
    return std::unexpected(ZipError{"cannot initialize ZIP inflater"});
  }
  const int status = inflate(&stream, Z_FINISH);
  inflateEnd(&stream);
  if (status != Z_STREAM_END || stream.total_out != output_size ||
      stream.total_in != compressed.size()) {
    return std::unexpected(ZipError{"invalid Deflate data in .linecode"});
  }
  return output;
}

struct CentralEntry final {
  std::string name;
  bool directory{};
  std::uint16_t flags{};
  std::uint16_t method{};
  std::uint32_t checksum{};
  std::uint32_t compressed_size{};
  std::uint32_t size{};
  std::uint32_t local_offset{};
};

} // namespace

bool IsSafeArchivePath(std::string_view name) noexcept {
  if (name.empty() || name.front() == '/' || name.front() == '\\' ||
      name.find('\0') != std::string_view::npos ||
      name.find('\\') != std::string_view::npos) {
    return false;
  }
  std::size_t depth{};
  for (std::size_t start = 0; start <= name.size();) {
    if (++depth > kMaximumArchivePathDepth) {
      return false;
    }
    const std::size_t slash = name.find('/', start);
    const auto part = name.substr(start, slash == std::string_view::npos
                                            ? name.size() - start
                                            : slash - start);
    if (part.empty() || part == "." || part == ".." ||
        part.find(':') != std::string_view::npos) {
      return false;
    }
    if (slash == std::string_view::npos) {
      break;
    }
    start = slash + 1;
  }
  return true;
}

std::expected<huxerui::Bytes, ZipError>
WriteLineCodeZip(std::span<const ZipEntryData> entries) {
  if (entries.size() > kMaximumEntries) {
    return std::unexpected(ZipError{"too many .linecode entries"});
  }
  huxerui::Bytes output;
  std::vector<CentralEntry> central;
  std::unordered_set<std::string> names;
  std::size_t content_total{};
  central.reserve(entries.size());
  for (const auto &entry : entries) {
    if (!IsSafeArchivePath(entry.name) || !names.insert(entry.name).second) {
      return std::unexpected(ZipError{"invalid or duplicate archive path"});
    }
    if (entry.name.size() > std::numeric_limits<std::uint16_t>::max() ||
        entry.content.size() > kMaximumEntryBytes ||
        entry.content.size() > std::numeric_limits<std::uint32_t>::max()) {
      return std::unexpected(ZipError{".linecode entry is too large"});
    }
    content_total += entry.content.size();
    const std::size_t local_size = 30U + entry.name.size() + entry.content.size();
    if (content_total > kMaximumArchiveBytes ||
        local_size > kMaximumArchiveBytes - output.size() ||
        output.size() > std::numeric_limits<std::uint32_t>::max()) {
      return std::unexpected(ZipError{".linecode archive is too large"});
    }
    const auto size = static_cast<std::uint32_t>(entry.content.size());
    const auto checksum = Checksum(entry.content);
    const auto offset = static_cast<std::uint32_t>(output.size());
    Put32(output, kLocalHeader);
    Put16(output, 20);
    Put16(output, 0x0800);
    Put16(output, 0);
    Put16(output, 0);
    Put16(output, 0);
    Put32(output, checksum);
    Put32(output, size);
    Put32(output, size);
    Put16(output, static_cast<std::uint16_t>(entry.name.size()));
    Put16(output, 0);
    PutText(output, entry.name);
    PutBytes(output, entry.content);
    central.push_back(CentralEntry{.name = entry.name,
                                   .directory = false,
                                   .flags = 0x0800,
                                   .method = 0,
                                   .checksum = checksum,
                                   .compressed_size = size,
                                   .size = size,
                                   .local_offset = offset});
  }

  const auto central_offset = static_cast<std::uint32_t>(output.size());
  for (const auto &entry : central) {
    const std::size_t record_size = 46U + entry.name.size();
    if (record_size > kMaximumArchiveBytes - output.size()) {
      return std::unexpected(ZipError{".linecode archive is too large"});
    }
    Put32(output, kCentralHeader);
    Put16(output, 20);
    Put16(output, 20);
    Put16(output, entry.flags);
    Put16(output, entry.method);
    Put16(output, 0);
    Put16(output, 0);
    Put32(output, entry.checksum);
    Put32(output, entry.compressed_size);
    Put32(output, entry.size);
    Put16(output, static_cast<std::uint16_t>(entry.name.size()));
    Put16(output, 0);
    Put16(output, 0);
    Put16(output, 0);
    Put16(output, 0);
    Put32(output, 0);
    Put32(output, entry.local_offset);
    PutText(output, entry.name);
  }
  const auto central_size =
      static_cast<std::uint32_t>(output.size() - central_offset);
  if (22U > kMaximumArchiveBytes - output.size()) {
    return std::unexpected(ZipError{".linecode archive is too large"});
  }
  Put32(output, kEndHeader);
  Put16(output, 0);
  Put16(output, 0);
  Put16(output, static_cast<std::uint16_t>(central.size()));
  Put16(output, static_cast<std::uint16_t>(central.size()));
  Put32(output, central_size);
  Put32(output, central_offset);
  Put16(output, 0);
  return output;
}

ZipResult ReadLineCodeZip(std::span<const std::byte> archive) {
  if (archive.size() < 22 || archive.size() > kMaximumArchiveBytes) {
    return std::unexpected(ZipError{"invalid .linecode ZIP size"});
  }
  const std::size_t search_start =
      archive.size() > 65557 ? archive.size() - 65557 : 0;
  std::optional<std::size_t> end_offset;
  for (std::size_t offset = archive.size() - 22;; --offset) {
    if (Read32(archive, offset) == kEndHeader &&
        HasRange(archive, offset, 22) &&
        offset + 22U + Read16(archive, offset + 20U) == archive.size()) {
      end_offset = offset;
      break;
    }
    if (offset == search_start) {
      break;
    }
  }
  if (!end_offset || !HasRange(archive, *end_offset, 22)) {
    return std::unexpected(ZipError{"missing .linecode ZIP directory"});
  }
  const std::uint16_t disk = Read16(archive, *end_offset + 4);
  const std::uint16_t directory_disk = Read16(archive, *end_offset + 6);
  const std::uint16_t disk_count = Read16(archive, *end_offset + 8);
  const std::uint16_t count = Read16(archive, *end_offset + 10);
  const std::uint32_t directory_size = Read32(archive, *end_offset + 12);
  const std::uint32_t directory_offset = Read32(archive, *end_offset + 16);
  if (disk != 0 || directory_disk != 0 || disk_count != count ||
      count > kMaximumEntries ||
      !HasRange(archive, directory_offset, directory_size) ||
      static_cast<std::size_t>(directory_offset) + directory_size !=
          *end_offset) {
    return std::unexpected(ZipError{"invalid .linecode ZIP directory"});
  }

  std::vector<CentralEntry> central;
  std::unordered_set<std::string> names;
  central.reserve(count);
  std::size_t cursor = directory_offset;
  std::size_t total_size{};
  for (std::size_t index = 0; index < count; ++index) {
    if (!HasRange(archive, cursor, 46) ||
        Read32(archive, cursor) != kCentralHeader) {
      return std::unexpected(ZipError{"invalid ZIP central entry"});
    }
    const auto flags = Read16(archive, cursor + 8);
    const auto method = Read16(archive, cursor + 10);
    const auto checksum = Read32(archive, cursor + 16);
    const auto compressed_size = Read32(archive, cursor + 20);
    const auto size = Read32(archive, cursor + 24);
    const auto name_size = Read16(archive, cursor + 28);
    const auto extra_size = Read16(archive, cursor + 30);
    const auto comment_size = Read16(archive, cursor + 32);
    const auto local_offset = Read32(archive, cursor + 42);
    const std::size_t record_size =
        46ULL + name_size + extra_size + comment_size;
    if (!HasRange(archive, cursor, record_size) ||
        (flags & static_cast<std::uint16_t>(~0x0808U)) != 0U ||
        (method != 0 && method != 8) || size > kMaximumEntryBytes) {
      return std::unexpected(ZipError{"unsupported or invalid ZIP entry"});
    }
    std::string name;
    name.reserve(name_size);
    for (std::size_t n = 0; n < name_size; ++n) {
      name.push_back(static_cast<char>(
          std::to_integer<unsigned char>(archive[cursor + 46 + n])));
    }
    const bool is_directory = name.ends_with('/');
    const auto safe_name = is_directory
                               ? std::string_view{name}.substr(0, name.size() - 1)
                               : std::string_view{name};
    if (!IsSafeArchivePath(safe_name) || !names.insert(name).second ||
        (is_directory &&
         (method != 0 || size != 0 || compressed_size != 0))) {
      return std::unexpected(ZipError{"unsafe or duplicate ZIP path"});
    }
    total_size += size;
    if (total_size > kMaximumArchiveBytes) {
      return std::unexpected(ZipError{"expanded .linecode is too large"});
    }
    central.push_back({name, is_directory, flags, method, checksum,
                       compressed_size, size, local_offset});
    cursor += record_size;
  }
  if (cursor != static_cast<std::size_t>(directory_offset) + directory_size) {
    return std::unexpected(ZipError{"invalid .linecode ZIP directory size"});
  }

  std::vector<ZipEntryData> entries;
  std::unordered_set<std::uint32_t> local_offsets;
  entries.reserve(central.size());
  for (const auto &entry : central) {
    if (!local_offsets.insert(entry.local_offset).second ||
        entry.local_offset >= directory_offset ||
        !HasRange(archive, entry.local_offset, 30) ||
        Read32(archive, entry.local_offset) != kLocalHeader) {
      return std::unexpected(ZipError{"invalid ZIP local entry"});
    }
    const auto local_flags = Read16(archive, entry.local_offset + 6);
    const auto local_method = Read16(archive, entry.local_offset + 8);
    const auto local_checksum = Read32(archive, entry.local_offset + 14);
    const auto local_compressed_size = Read32(archive, entry.local_offset + 18);
    const auto local_size = Read32(archive, entry.local_offset + 22);
    const auto local_name_size = Read16(archive, entry.local_offset + 26);
    const auto local_extra_size = Read16(archive, entry.local_offset + 28);
    const std::size_t data_offset =
        static_cast<std::size_t>(entry.local_offset) + 30 + local_name_size +
        local_extra_size;
    if (local_flags != entry.flags || local_method != entry.method ||
        local_name_size != entry.name.size() ||
        !HasRange(archive, entry.local_offset + 30U, local_name_size) ||
        !std::equal(entry.name.begin(), entry.name.end(),
                    archive.begin() + entry.local_offset + 30U,
                    [](char expected, std::byte actual) {
                      return static_cast<unsigned char>(expected) ==
                             std::to_integer<unsigned char>(actual);
                    }) ||
        ((entry.flags & 0x0008U) == 0U &&
         (local_checksum != entry.checksum ||
          local_compressed_size != entry.compressed_size ||
          local_size != entry.size)) ||
        !HasRange(archive, data_offset, entry.compressed_size) ||
        data_offset + entry.compressed_size > directory_offset) {
      return std::unexpected(ZipError{"truncated ZIP entry"});
    }
    const auto compressed =
        archive.subspan(data_offset, entry.compressed_size);
    std::expected<huxerui::Bytes, ZipError> content =
        entry.method == 0
            ? std::expected<huxerui::Bytes, ZipError>(
                  huxerui::Bytes(compressed.begin(), compressed.end()))
            : Inflate(compressed, entry.size);
    if (!content || content->size() != entry.size ||
        Checksum(*content) != entry.checksum) {
      return std::unexpected(content ? ZipError{"ZIP checksum mismatch"}
                                     : std::move(content.error()));
    }
    if (entry.directory)
      continue;
    entries.push_back({entry.name, std::move(*content)});
  }
  return entries;
}

} // namespace linecode::infrastructure
