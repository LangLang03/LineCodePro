#include "gtest_support.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <random>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/file_content_hash.h"
#include "application/file_observation_store.h"

namespace {

using namespace linecode;

// Reference digests produced by an independent SHA-256 implementation, so a
// mistake in the local one cannot be confirmed by the local one.
void CheckDigest(std::string_view bytes, std::string_view expected) {
  EXPECT_EXPRESSION(application::Sha256Hex(bytes) == expected);
}

TEST(Sha256Digest, MatchesTheKnownVectors) {
  CheckDigest("",
              "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  CheckDigest("abc",
              "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  // 56 bytes: the shortest input that spans two compression blocks once the
  // length padding is added.
  CheckDigest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
              "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1");
  // Exactly one block, so the padding block starts clean.
  CheckDigest(
      "1234567890123456789012345678901234567890123456789012345678901234",
      "676491965ed3ec50cb7a63ee96315480a95c54426b0b72bca8a0d4ad1285ad55");
  CheckDigest(std::string(1000U, 'a'),
              "41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3");
}

TEST(Sha256Digest, HashesArbitraryBytesNotJustText) {
  std::string bytes;
  for (std::uint32_t value = 0; value < 200U; ++value)
    bytes.push_back(static_cast<char>(value));
  EXPECT_EXPRESSION(application::Sha256Hex(bytes) ==
                    "1901da1c9f699b48f6b2636e65cbf73abf99d0441ef67f5c540a42f7051dec6f");
}

TEST(Sha256Digest, IsIndependentOfChunking) {
  std::string bytes;
  bytes.reserve(4096U);
  std::mt19937 generator{20260101U};
  for (std::size_t index = 0; index < 4096U; ++index)
    bytes.push_back(static_cast<char>(generator() & 0xffU));

  const std::string expected = application::Sha256Hex(bytes);
  for (const std::size_t chunk : {std::size_t{1}, std::size_t{63},
                                  std::size_t{64}, std::size_t{65},
                                  std::size_t{1000}}) {
    application::Sha256Digest digest;
    std::size_t offset = 0;
    while (offset < bytes.size()) {
      const std::size_t length = std::min(chunk, bytes.size() - offset);
      digest.Update(std::string_view{bytes}.substr(offset, length));
      offset += length;
    }
    EXPECT_EXPRESSION(digest.Hex() == expected);
  }
  // An explicitly empty chunk must not disturb a running digest.
  application::Sha256Digest digest;
  digest.Update(std::string_view{});
  digest.Update("abc");
  EXPECT_EXPRESSION(digest.Hex() ==
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
}

TEST(FileObservationStore, TracksTheRevisionOfEveryReadPath) {
  application::FileObservationStore store;
  EXPECT_EXPRESSION(store.Size() == 0U);
  EXPECT_EXPRESSION(!store.ObservedHash("/workspace/a.txt").has_value());

  store.Record("/workspace/a.txt", "first");
  store.Record("/workspace/b.txt", "second");
  EXPECT_EXPRESSION(store.Size() == 2U);
  EXPECT_EXPRESSION(store.ObservedHash("/workspace/a.txt") ==
                    std::optional<std::string>{"first"});
  EXPECT_EXPRESSION(store.ObservedHash("/workspace/b.txt") ==
                    std::optional<std::string>{"second"});

  // Reading the same path again replaces the recorded revision.
  store.Record("/workspace/a.txt", "third");
  EXPECT_EXPRESSION(store.ObservedHash("/workspace/a.txt") ==
                    std::optional<std::string>{"third"});
  EXPECT_EXPRESSION(store.Size() == 2U);

  // Paths are matched exactly: a sibling directory is a different file.
  EXPECT_EXPRESSION(!store.ObservedHash("/elsewhere/a.txt").has_value());

  store.Forget("/workspace/a.txt");
  EXPECT_EXPRESSION(!store.ObservedHash("/workspace/a.txt").has_value());
  EXPECT_EXPRESSION(store.Size() == 1U);
  // Forgetting an unknown path is a no-op.
  store.Forget("/workspace/missing.txt");
  EXPECT_EXPRESSION(store.Size() == 1U);

  store.Clear();
  EXPECT_EXPRESSION(store.Size() == 0U);
  EXPECT_EXPRESSION(!store.ObservedHash("/workspace/b.txt").has_value());
}

TEST(FileObservationStore, StaysBoundedByDroppingTheRecord) {
  application::FileObservationStore store;
  for (std::size_t index = 0; index < application::FileObservationStore::
                                          kMaximumObservedPaths;
       ++index) {
    store.Record("/workspace/file-" + std::to_string(index) + ".txt",
                 std::to_string(index));
  }
  EXPECT_EXPRESSION(store.Size() ==
                    application::FileObservationStore::kMaximumObservedPaths);

  // One more path drops the whole record, so a long-lived process cannot grow
  // without bound; the newest path is still recorded.
  store.Record("/workspace/overflow.txt", "overflow");
  EXPECT_EXPRESSION(store.Size() <=
                    application::FileObservationStore::kMaximumObservedPaths);
  EXPECT_EXPRESSION(store.ObservedHash("/workspace/overflow.txt") ==
                    std::optional<std::string>{"overflow"});
}

} // namespace
