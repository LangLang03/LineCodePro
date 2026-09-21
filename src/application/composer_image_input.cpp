#include "application/composer_image_input.h"

#include <array>
#include <ranges>
#include <string_view>

namespace linecode::application {
namespace {

bool StartsWith(const std::span<const std::byte> bytes,
                const std::span<const unsigned char> signature) noexcept {
  if (bytes.size() < signature.size())
    return false;
  for (std::size_t index = 0; index < signature.size(); ++index) {
    if (bytes[index] != static_cast<std::byte>(signature[index]))
      return false;
  }
  return true;
}

std::string_view MimeType(const std::span<const std::byte> bytes) noexcept {
  static constexpr std::array<unsigned char, 8> png_signature{
      0x89U, 0x50U, 0x4EU, 0x47U, 0x0DU, 0x0AU, 0x1AU, 0x0AU};
  static constexpr std::array<unsigned char, 3> jpeg_signature{
      0xFFU, 0xD8U, 0xFFU};
  struct FormatPolicy final {
    std::string_view mime_type;
    std::span<const unsigned char> signature;
  };
  static constexpr std::array formats{
      FormatPolicy{"image/png", png_signature},
      FormatPolicy{"image/jpeg", jpeg_signature},
  };
  const auto format = std::ranges::find_if(
      formats, [bytes](const FormatPolicy &candidate) {
        return StartsWith(bytes, candidate.signature);
      });
  return format == formats.end() ? std::string_view{} : format->mime_type;
}

std::string Base64Encode(const std::span<const std::byte> bytes) {
  constexpr std::string_view alphabet =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  std::string encoded;
  encoded.reserve(((bytes.size() + 2U) / 3U) * 4U);
  for (std::size_t index = 0; index < bytes.size(); index += 3U) {
    const auto first = std::to_integer<unsigned>(bytes[index]);
    const auto second = index + 1U < bytes.size()
                            ? std::to_integer<unsigned>(bytes[index + 1U])
                            : 0U;
    const auto third = index + 2U < bytes.size()
                           ? std::to_integer<unsigned>(bytes[index + 2U])
                           : 0U;
    const unsigned value = (first << 16U) | (second << 8U) | third;
    encoded.push_back(alphabet[(value >> 18U) & 0x3FU]);
    encoded.push_back(alphabet[(value >> 12U) & 0x3FU]);
    encoded.push_back(index + 1U < bytes.size()
                          ? alphabet[(value >> 6U) & 0x3FU]
                          : '=');
    encoded.push_back(index + 2U < bytes.size()
                          ? alphabet[value & 0x3FU]
                          : '=');
  }
  return encoded;
}

} // namespace

std::expected<domain::ChatImage, ComposerImageInputError>
EncodeComposerImage(std::string name, const std::span<const std::byte> bytes) {
  if (bytes.empty())
    return std::unexpected(ComposerImageInputError::empty);
  if (bytes.size() > max_composer_image_input_bytes)
    return std::unexpected(ComposerImageInputError::too_large);
  const auto mime_type = MimeType(bytes);
  if (mime_type.empty())
    return std::unexpected(ComposerImageInputError::unsupported_format);
  return domain::ChatImage{
      .name = std::move(name),
      .mime_type = std::string{mime_type},
      .base64 = Base64Encode(bytes),
  };
}

} // namespace linecode::application
