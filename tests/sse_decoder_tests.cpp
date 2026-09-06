#include <array>
#include <cassert>
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
  assert(result.has_value());
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

  assert(events.size() == 2U);
  assert(events[0].event == "completion");
  assert(events[0].id == "请求-7");
  assert(events[0].retry == 1500ms);
  assert(events[0].data == "你\n好 🌍");
  assert(events[1].data == "[DONE]");
  assert(!events[1].event.has_value());
}

void SupportsLoneCrAndConsumesCrLfAsOneTerminator() {
  SseDecoder decoder;
  std::vector<SseEvent> events;

  Append(events, decoder.Feed("data: one\r"));
  Append(events, decoder.Feed("\ndata: two\rdata: three\r"));
  Append(events, decoder.Feed("\n\r"));

  assert(events.size() == 1U);
  assert(events[0].data == "one\ntwo\nthree");
}

void DecodesLfAndCrLfAndMultipleFramesPerChunk() {
  SseDecoder decoder;
  const auto result = decoder.Feed(
      "data:first\n\ndata: second\r\n\r\nevent\nid: 9\ndata\n\n");

  assert(result.has_value());
  assert(result->size() == 3U);
  assert((*result)[0].data == "first");
  assert((*result)[1].data == "second");
  assert((*result)[2].event == "");
  assert((*result)[2].id == "9");
  assert((*result)[2].data.empty());
}

void IgnoresCommentsUnknownFieldsAndInvalidMetadata() {
  SseDecoder decoder;
  std::string frame = ": ignore me\nunknown: value\nretry: 10x\n";
  frame.append("id: bad\0id\n", 11U);
  frame += "data: kept\n\n";

  const auto result = decoder.Feed(frame);
  assert(result.has_value());
  assert(result->size() == 1U);
  assert((*result)[0].data == "kept");
  assert(!(*result)[0].retry.has_value());
  assert(!(*result)[0].id.has_value());
}

void MetadataOnlyFramesUpdateStateWithoutDispatching() {
  SseDecoder decoder;
  const auto metadata =
      decoder.Feed("event: ignored\nid: stream-4\nretry: 250\n\n");
  assert(metadata.has_value());
  assert(metadata->empty());
  assert(decoder.LastEventId() == "stream-4");
  assert(decoder.RetryDelay() == 250ms);

  const auto data = decoder.Feed("data: payload\n\n");
  assert(data.has_value());
  assert(data->size() == 1U);
  assert((*data)[0].data == "payload");
  assert(!(*data)[0].event.has_value());
  assert((*data)[0].id == "stream-4");
  assert((*data)[0].retry == 250ms);
}

void LastEventIdPersistsAndAnIdContainingNullIsIgnored() {
  SseDecoder decoder;
  std::string source = "id: stable\ndata: first\n\n";
  constexpr char kNullIdFrame[] = "id: bad\0id\ndata: second\n\n";
  source.append(kNullIdFrame, sizeof(kNullIdFrame) - 1U);
  source += "data: third\n\nid:\ndata: fourth\n\n";

  const auto result = decoder.Feed(source);
  assert(result.has_value());
  assert(result->size() == 4U);
  assert((*result)[0].id == "stable");
  assert((*result)[1].id == "stable");
  assert((*result)[2].id == "stable");
  assert((*result)[3].id == "");
  assert(decoder.LastEventId() == "");
}

void IgnoresOnlyTheStreamInitialBom() {
  SseDecoder decoder;
  const auto first = decoder.Feed("\xEF");
  const auto second = decoder.Feed("\xBB");
  const auto third = decoder.Feed("\xBF" "data: first\n\n");
  assert(first.has_value() && first->empty());
  assert(second.has_value() && second->empty());
  assert(third.has_value());
  assert(third->size() == 1U);
  assert((*third)[0].data == "first");

  const auto later = decoder.Feed("data: \xEF\xBB\xBFkept\n\n");
  assert(later.has_value());
  assert(later->size() == 1U);
  assert((*later)[0].data == "\xEF\xBB\xBFkept");
}

void RetainsOnlyOneOptionalSpaceAndLastValidMetadata() {
  SseDecoder decoder;
  const auto result = decoder.Feed(
      "event: old\nevent: new\nid: 1\nid: 2\nretry: 25\nretry: nope\n"
      "data:  two-leading-spaces\ndata:\ndata\n\n");

  assert(result.has_value());
  assert(result->size() == 1U);
  assert((*result)[0].event == "new");
  assert((*result)[0].id == "2");
  assert((*result)[0].retry == 25ms);
  assert((*result)[0].data == " two-leading-spaces\n\n");
}

void WaitsForFrameBoundaryAndFlushesAtEndOfStream() {
  SseDecoder decoder;
  const auto partial = decoder.Feed("data: partial");
  assert(partial.has_value());
  assert(partial->empty());

  const auto finished = decoder.Finish();
  assert(finished.has_value());
  assert(finished->size() == 1U);
  assert((*finished)[0].data == "partial");

  const auto after_finish = decoder.Feed("data: impossible\n\n");
  assert(!after_finish.has_value());
  assert(after_finish.error().code ==
         SseDecodeErrorCode::input_after_finish);
}

void FinishDoesNotManufactureAnEventFromMetadata() {
  SseDecoder decoder;
  const auto line = decoder.Feed("event: status\r\nid: 3\r\nretry: 9\r\n");
  assert(line.has_value());
  assert(line->empty());

  const auto finished = decoder.Finish();
  assert(finished.has_value());
  assert(finished->empty());
  assert(decoder.LastEventId() == "3");
  assert(decoder.RetryDelay() == 9ms);
}

void ReportsAndLatchesBufferLimitErrors() {
  SseDecoder decoder(
      SseDecoderLimits{.max_buffer_bytes = 5U, .max_frame_bytes = 100U});
  const auto first = decoder.Feed("data:");
  assert(first.has_value());

  const auto overflow = decoder.Feed("x");
  assert(!overflow.has_value());
  assert(overflow.error().code ==
         SseDecodeErrorCode::buffer_limit_exceeded);
  assert(overflow.error().limit == 5U);
  assert(overflow.error().observed == 6U);
  assert(overflow.error().byte_offset == 5U);

  const auto latched = decoder.Feed("\n\n");
  assert(!latched.has_value());
  assert(latched.error() == overflow.error());
}

void ReportsAggregateFrameLimitAndResetRecovers() {
  SseDecoder decoder(
      SseDecoderLimits{.max_buffer_bytes = 32U, .max_frame_bytes = 12U});
  const auto first = decoder.Feed("data:a\n");
  assert(first.has_value());
  assert(first->empty());

  const auto overflow = decoder.Feed("data:b\n\n");
  assert(!overflow.has_value());
  assert(overflow.error().code == SseDecodeErrorCode::frame_limit_exceeded);
  assert(overflow.error().limit == 12U);
  assert(overflow.error().observed == 14U);

  decoder.Reset();
  const auto recovered = decoder.Feed("data: ok\n\n");
  assert(recovered.has_value());
  assert(recovered->size() == 1U);
  assert((*recovered)[0].data == "ok");
}

void ResetsFrameAccountingAtEveryBlankLine() {
  SseDecoder decoder(
      SseDecoderLimits{.max_buffer_bytes = 16U, .max_frame_bytes = 8U});
  const auto result = decoder.Feed("data:x\n\ndata:y\n\n");
  assert(result.has_value());
  assert(result->size() == 2U);
}

} // namespace

int main() {
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
