#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>

namespace linecode::application {

// Incremental SHA-256 (FIPS 180-4) over raw bytes.
//
// The built-in file tools identify a file revision by the digest of its bytes,
// so the digest must be deterministic and independent of how those bytes were
// obtained. Update() takes arbitrarily sized chunks, which lets a caller stream
// a file in bounded reads instead of loading it, and the running digest stays
// valid across chunk boundaries.
//
// Hex() finalizes the digest. A hasher is single-use: finalization appends the
// padding, so a second Hex() call would hash the padding again.
class Sha256Digest final {
public:
  Sha256Digest() noexcept = default;

  void Update(std::string_view bytes) noexcept;
  // Lowercase, zero-padded, 64-character hex digest.
  [[nodiscard]] std::string Hex();

private:
  static constexpr std::size_t kBlockBytes = 64;

  void Compress(const std::uint8_t *block) noexcept;

  // FIPS 180-4 initial hash value.
  std::array<std::uint32_t, 8> state_{
      0x6a09e667U, 0xbb67ae85U, 0x3c6ef372U, 0xa54ff53aU,
      0x510e527fU, 0x9b05688cU, 0x1f83d9abU, 0x5be0cd19U};
  std::array<std::uint8_t, kBlockBytes> buffer_{};
  // Bytes currently held in buffer_, and bytes consumed since construction.
  std::size_t buffered_{};
  std::uint64_t total_{};
};

// One-shot digest of an in-memory buffer.
[[nodiscard]] std::string Sha256Hex(std::string_view bytes);

} // namespace linecode::application
