#include "application/file_content_hash.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>

namespace linecode::application {
namespace {

// FIPS 180-4 round constants: the first 32 bits of the fractional parts of the
// cube roots of the first 64 primes.
constexpr std::array<std::uint32_t, 64> kRoundConstants{
    0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U, 0x3956c25bU,
    0x59f111f1U, 0x923f82a4U, 0xab1c5ed5U, 0xd807aa98U, 0x12835b01U,
    0x243185beU, 0x550c7dc3U, 0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U,
    0xc19bf174U, 0xe49b69c1U, 0xefbe4786U, 0x0fc19dc6U, 0x240ca1ccU,
    0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU, 0x983e5152U,
    0xa831c66dU, 0xb00327c8U, 0xbf597fc7U, 0xc6e00bf3U, 0xd5a79147U,
    0x06ca6351U, 0x14292967U, 0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU,
    0x53380d13U, 0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U,
    0xa2bfe8a1U, 0xa81a664bU, 0xc24b8b70U, 0xc76c51a3U, 0xd192e819U,
    0xd6990624U, 0xf40e3585U, 0x106aa070U, 0x19a4c116U, 0x1e376c08U,
    0x2748774cU, 0x34b0bcb5U, 0x391c0cb3U, 0x4ed8aa4aU, 0x5b9cca4fU,
    0x682e6ff3U, 0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U,
    0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U,
};

constexpr std::array<char, 16> kHexDigits{'0', '1', '2', '3', '4', '5', '6',
                                          '7', '8', '9', 'a', 'b', 'c', 'd',
                                          'e', 'f'};

std::uint32_t RotateRight(const std::uint32_t value,
                          const std::uint32_t bits) noexcept {
  return (value >> bits) | (value << (32U - bits));
}

// Big-endian 32-bit load, so the digest does not depend on host endianness.
std::uint32_t LoadBigEndian(const std::uint8_t *bytes) noexcept {
  return (static_cast<std::uint32_t>(bytes[0]) << 24U) |
         (static_cast<std::uint32_t>(bytes[1]) << 16U) |
         (static_cast<std::uint32_t>(bytes[2]) << 8U) |
         static_cast<std::uint32_t>(bytes[3]);
}

} // namespace

void Sha256Digest::Compress(const std::uint8_t *block) noexcept {
  std::array<std::uint32_t, 64> schedule{};
  for (std::size_t index = 0; index < 16U; ++index)
    schedule[index] = LoadBigEndian(block + index * 4U);
  for (std::size_t index = 16U; index < schedule.size(); ++index) {
    const std::uint32_t previous = schedule[index - 15U];
    const std::uint32_t recent = schedule[index - 2U];
    const std::uint32_t s0 =
        RotateRight(previous, 7U) ^ RotateRight(previous, 18U) ^ (previous >> 3U);
    const std::uint32_t s1 =
        RotateRight(recent, 17U) ^ RotateRight(recent, 19U) ^ (recent >> 10U);
    schedule[index] = schedule[index - 16U] + s0 + schedule[index - 7U] + s1;
  }

  std::uint32_t a = state_[0];
  std::uint32_t b = state_[1];
  std::uint32_t c = state_[2];
  std::uint32_t d = state_[3];
  std::uint32_t e = state_[4];
  std::uint32_t f = state_[5];
  std::uint32_t g = state_[6];
  std::uint32_t h = state_[7];
  for (std::size_t index = 0; index < schedule.size(); ++index) {
    const std::uint32_t sum1 =
        RotateRight(e, 6U) ^ RotateRight(e, 11U) ^ RotateRight(e, 25U);
    const std::uint32_t choice = (e & f) ^ (~e & g);
    const std::uint32_t temporary1 =
        h + sum1 + choice + kRoundConstants[index] + schedule[index];
    const std::uint32_t sum0 =
        RotateRight(a, 2U) ^ RotateRight(a, 13U) ^ RotateRight(a, 22U);
    const std::uint32_t majority = (a & b) ^ (a & c) ^ (b & c);
    const std::uint32_t temporary2 = sum0 + majority;
    h = g;
    g = f;
    f = e;
    e = d + temporary1;
    d = c;
    c = b;
    b = a;
    a = temporary1 + temporary2;
  }

  state_[0] += a;
  state_[1] += b;
  state_[2] += c;
  state_[3] += d;
  state_[4] += e;
  state_[5] += f;
  state_[6] += g;
  state_[7] += h;
}

void Sha256Digest::Update(std::string_view bytes) noexcept {
  if (bytes.empty())
    return;
  total_ += bytes.size();
  const auto *data = reinterpret_cast<const std::uint8_t *>(bytes.data());
  std::size_t remaining = bytes.size();

  // Complete a partially filled block before consuming whole blocks, so the
  // digest is identical for any chunking of the same input.
  if (buffered_ > 0) {
    const std::size_t wanted = kBlockBytes - buffered_;
    const std::size_t taken = std::min(wanted, remaining);
    std::copy_n(data, taken, buffer_.begin() +
                                  static_cast<std::ptrdiff_t>(buffered_));
    buffered_ += taken;
    data += taken;
    remaining -= taken;
    if (buffered_ == kBlockBytes) {
      Compress(buffer_.data());
      buffered_ = 0;
    }
  }
  while (remaining >= kBlockBytes) {
    Compress(data);
    data += kBlockBytes;
    remaining -= kBlockBytes;
  }
  if (remaining > 0) {
    std::copy_n(data, remaining, buffer_.begin());
    buffered_ = remaining;
  }
}

std::string Sha256Digest::Hex() {
  const std::uint64_t bit_length = total_ * 8U;
  // Padding: the buffered message bytes, then a single 0x80 byte, zeros, and
  // the 64-bit big-endian length. The tail is one block unless the message
  // leaves fewer than 9 bytes free in it.
  const std::size_t tail_bytes = buffered_ < 56U ? kBlockBytes : 2 * kBlockBytes;
  std::array<std::uint8_t, 2 * kBlockBytes> tail{};
  std::copy_n(buffer_.begin(), buffered_, tail.begin());
  tail[buffered_] = 0x80U;
  for (std::size_t index = 0; index < 8U; ++index)
    tail[tail_bytes - 1U - index] =
        static_cast<std::uint8_t>(bit_length >> (8U * index));
  for (std::size_t offset = 0; offset < tail_bytes; offset += kBlockBytes)
    Compress(tail.data() + offset);

  std::string hex;
  hex.reserve(64U);
  for (const std::uint32_t word : state_) {
    for (std::uint32_t shift = 28U;; shift -= 4U) {
      hex.push_back(kHexDigits[(word >> shift) & 0x0fU]);
      if (shift == 0U)
        break;
    }
  }
  return hex;
}

std::string Sha256Hex(std::string_view bytes) {
  Sha256Digest digest;
  digest.Update(bytes);
  return digest.Hex();
}

} // namespace linecode::application
