#include <array>
#include "gtest_support.h"
#include <chrono>
#include <cstddef>
#include <iterator>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "infrastructure/sse_decoder.h"

namespace {

using namespace std::chrono_literals;
using linecode::infrastructure::SseDecodeErrorCode;
using linecode::infrastructure::SseDecoder;
using linecode::infrastructure::SseDecoderLimits;
using linecode::infrastructure::SseEvent;

void Append(std::vector<SseEvent> &destination,
            SseDecoder::Result result) {
  EXPECT_EXPRESSION(result.has_value());
  destination.insert(destination.end(),
                     std::make_move_iterator(result->begin()),
                     std::make_move_iterator(result->end()));
}

void DecodesEveryByteBoundaryWithoutChangingUtf8() {
  const std::string source =
      "\xEF\xBB\xBF: heartbeat\r\nevent: completion\r\nid: 请求-7\r\n"
      "retry: 1500\r\ndata: 你\r\ndata: 好 🌍\r\n\r\n"
      "data: [DONE]\n\n";
  SseDecoder decoder;
  std::vector<SseEvent> events;

  for (const char character : source) {
    const std::array one_byte{std::byte{static_cast<unsigned char>(character)}};
    Append(events, decoder.Feed(std::span<const std::byte>{one_byte}));
  }

  EXPECT_EXPRESSION(events.size() == 2U);
  EXPECT_EXPRESSION(events[0].event == "completion");
  EXPECT_EXPRESSION(events[0].id == "请求-7");
  EXPECT_EXPRESSION(events[0].retry == 1500ms);
  EXPECT_EXPRESSION(events[0].data == "你\n好 🌍");
  EXPECT_EXPRESSION(events[1].data == "[DONE]");
  EXPECT_EXPRESSION(!events[1].event.has_value());
}

void SupportsLoneCrAndConsumesCrLfAsOneTerminator() {
  SseDecoder decoder;
  std::vector<SseEvent> events;

  Append(events, decoder.Feed("data: one\r"));
  Append(events, decoder.Feed("\ndata: two\rdata: three\r"));
  Append(events, decoder.Feed("\n\r"));

  EXPECT_EXPRESSION(events.size() == 1U);
  EXPECT_EXPRESSION(events[0].data == "one\ntwo\nthree");
}

void DecodesLfAndCrLfAndMultipleFramesPerChunk() {
  SseDecoder decoder;
  const auto result = decoder.Feed(
      "data:first\n\ndata: second\r\n\r\nevent\nid: 9\ndata\n\n");

  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->size() == 3U);
  EXPECT_EXPRESSION((*result)[0].data == "first");
  EXPECT_EXPRESSION((*result)[1].data == "second");
  EXPECT_EXPRESSION((*result)[2].event == "");
  EXPECT_EXPRESSION((*result)[2].id == "9");
  EXPECT_EXPRESSION((*result)[2].data.empty());
}

void IgnoresCommentsUnknownFieldsAndInvalidMetadata() {
  SseDecoder decoder;
  std::string frame = ": ignore me\nunknown: value\nretry: 10x\n";
  frame.append("id: bad\0id\n", 11U);
  frame += "data: kept\n\n";

  const auto result = decoder.Feed(frame);
  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->size() == 1U);
  EXPECT_EXPRESSION((*result)[0].data == "kept");
  EXPECT_EXPRESSION(!(*result)[0].retry.has_value());
  EXPECT_EXPRESSION(!(*result)[0].id.has_value());
}

void MetadataOnlyFramesUpdateStateWithoutDispatching() {
  SseDecoder decoder;
  const auto metadata =
      decoder.Feed("event: ignored\nid: stream-4\nretry: 250\n\n");
  EXPECT_EXPRESSION(metadata.has_value());
  EXPECT_EXPRESSION(metadata->empty());
  EXPECT_EXPRESSION(decoder.LastEventId() == "stream-4");
  EXPECT_EXPRESSION(decoder.RetryDelay() == 250ms);

  const auto data = decoder.Feed("data: payload\n\n");
  EXPECT_EXPRESSION(data.has_value());
  EXPECT_EXPRESSION(data->size() == 1U);
  EXPECT_EXPRESSION((*data)[0].data == "payload");
  EXPECT_EXPRESSION(!(*data)[0].event.has_value());
  EXPECT_EXPRESSION((*data)[0].id == "stream-4");
  EXPECT_EXPRESSION((*data)[0].retry == 250ms);
}

void LastEventIdPersistsAndAnIdContainingNullIsIgnored() {
  SseDecoder decoder;
  std::string source = "id: stable\ndata: first\n\n";
  constexpr char kNullIdFrame[] = "id: bad\0id\ndata: second\n\n";
  source.append(kNullIdFrame, sizeof(kNullIdFrame) - 1U);
  source += "data: third\n\nid:\ndata: fourth\n\n";

  const auto result = decoder.Feed(source);
  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->size() == 4U);
  EXPECT_EXPRESSION((*result)[0].id == "stable");
  EXPECT_EXPRESSION((*result)[1].id == "stable");
  EXPECT_EXPRESSION((*result)[2].id == "stable");
  EXPECT_EXPRESSION((*result)[3].id == "");
  EXPECT_EXPRESSION(decoder.LastEventId() == "");
}

void IgnoresOnlyTheStreamInitialBom() {
  SseDecoder decoder;
  const auto first = decoder.Feed("\xEF");
  const auto second = decoder.Feed("\xBB");
  const auto third = decoder.Feed("\xBF" "data: first\n\n");
  EXPECT_EXPRESSION(first.has_value() && first->empty());
  EXPECT_EXPRESSION(second.has_value() && second->empty());
  EXPECT_EXPRESSION(third.has_value());
  EXPECT_EXPRESSION(third->size() == 1U);
  EXPECT_EXPRESSION((*third)[0].data == "first");

  const auto later = decoder.Feed("data: \xEF\xBB\xBFkept\n\n");
  EXPECT_EXPRESSION(later.has_value());
  EXPECT_EXPRESSION(later->size() == 1U);
  EXPECT_EXPRESSION((*later)[0].data == "\xEF\xBB\xBFkept");
}

void RetainsOnlyOneOptionalSpaceAndLastValidMetadata() {
  SseDecoder decoder;
  const auto result = decoder.Feed(
      "event: old\nevent: new\nid: 1\nid: 2\nretry: 25\nretry: nope\n"
      "data:  two-leading-spaces\ndata:\ndata\n\n");

  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->size() == 1U);
  EXPECT_EXPRESSION((*result)[0].event == "new");
  EXPECT_EXPRESSION((*result)[0].id == "2");
  EXPECT_EXPRESSION((*result)[0].retry == 25ms);
  EXPECT_EXPRESSION((*result)[0].data == " two-leading-spaces\n\n");
}

void WaitsForFrameBoundaryAndFlushesAtEndOfStream() {
  SseDecoder decoder;
  const auto partial = decoder.Feed("data: partial");
  EXPECT_EXPRESSION(partial.has_value());
  EXPECT_EXPRESSION(partial->empty());

  const auto finished = decoder.Finish();
  EXPECT_EXPRESSION(finished.has_value());
  EXPECT_EXPRESSION(finished->size() == 1U);
  EXPECT_EXPRESSION((*finished)[0].data == "partial");

  const auto after_finish = decoder.Feed("data: impossible\n\n");
  EXPECT_EXPRESSION(!after_finish.has_value());
  EXPECT_EXPRESSION(after_finish.error().code ==
         SseDecodeErrorCode::input_after_finish);
}

void FinishDoesNotManufactureAnEventFromMetadata() {
  SseDecoder decoder;
  const auto line = decoder.Feed("event: status\r\nid: 3\r\nretry: 9\r\n");
  EXPECT_EXPRESSION(line.has_value());
  EXPECT_EXPRESSION(line->empty());

  const auto finished = decoder.Finish();
  EXPECT_EXPRESSION(finished.has_value());
  EXPECT_EXPRESSION(finished->empty());
  EXPECT_EXPRESSION(decoder.LastEventId() == "3");
  EXPECT_EXPRESSION(decoder.RetryDelay() == 9ms);
}

void ReportsAndLatchesBufferLimitErrors() {
  SseDecoder decoder(
      SseDecoderLimits{.max_buffer_bytes = 5U, .max_frame_bytes = 100U});
  const auto first = decoder.Feed("data:");
  EXPECT_EXPRESSION(first.has_value());

  const auto overflow = decoder.Feed("x");
  EXPECT_EXPRESSION(!overflow.has_value());
  EXPECT_EXPRESSION(overflow.error().code ==
         SseDecodeErrorCode::buffer_limit_exceeded);
  EXPECT_EXPRESSION(overflow.error().limit == 5U);
  EXPECT_EXPRESSION(overflow.error().observed == 6U);
  EXPECT_EXPRESSION(overflow.error().byte_offset == 5U);

  const auto latched = decoder.Feed("\n\n");
  EXPECT_EXPRESSION(!latched.has_value());
  EXPECT_EXPRESSION(latched.error() == overflow.error());
}

void ReportsAggregateFrameLimitAndResetRecovers() {
  SseDecoder decoder(
      SseDecoderLimits{.max_buffer_bytes = 32U, .max_frame_bytes = 12U});
  const auto first = decoder.Feed("data:a\n");
  EXPECT_EXPRESSION(first.has_value());
  EXPECT_EXPRESSION(first->empty());

  const auto overflow = decoder.Feed("data:b\n\n");
  EXPECT_EXPRESSION(!overflow.has_value());
  EXPECT_EXPRESSION(overflow.error().code == SseDecodeErrorCode::frame_limit_exceeded);
  EXPECT_EXPRESSION(overflow.error().limit == 12U);
  EXPECT_EXPRESSION(overflow.error().observed == 14U);

  decoder.Reset();
  const auto recovered = decoder.Feed("data: ok\n\n");
  EXPECT_EXPRESSION(recovered.has_value());
  EXPECT_EXPRESSION(recovered->size() == 1U);
  EXPECT_EXPRESSION((*recovered)[0].data == "ok");
}

void ResetsFrameAccountingAtEveryBlankLine() {
  SseDecoder decoder(
      SseDecoderLimits{.max_buffer_bytes = 16U, .max_frame_bytes = 8U});
  const auto result = decoder.Feed("data:x\n\ndata:y\n\n");
  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->size() == 2U);
}

} // namespace

TEST(sse_decoder_tests, LegacySuite) {
  DecodesEveryByteBoundaryWithoutChangingUtf8();
  SupportsLoneCrAndConsumesCrLfAsOneTerminator();
  DecodesLfAndCrLfAndMultipleFramesPerChunk();
  IgnoresCommentsUnknownFieldsAndInvalidMetadata();
  MetadataOnlyFramesUpdateStateWithoutDispatching();
  LastEventIdPersistsAndAnIdContainingNullIsIgnored();
  IgnoresOnlyTheStreamInitialBom();
  RetainsOnlyOneOptionalSpaceAndLastValidMetadata();
  WaitsForFrameBoundaryAndFlushesAtEndOfStream();
  FinishDoesNotManufactureAnEventFromMetadata();
  ReportsAndLatchesBufferLimitErrors();
  ReportsAggregateFrameLimitAndResetRecovers();
  ResetsFrameAccountingAtEveryBlankLine();
}
