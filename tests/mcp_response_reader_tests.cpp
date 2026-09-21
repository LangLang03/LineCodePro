#include "gtest_support.h"
#include <string>
#include <vector>

#include <huxerui/http.h>

#include "infrastructure/mcp_response_reader.h"

namespace {

using linecode::infrastructure::HttpHeaderValue;
using linecode::infrastructure::McpResponseReader;
using linecode::infrastructure::McpResponseReadErrorCode;
using linecode::infrastructure::PutHttpHeader;

void CompletesSseBeforeEof() {
  McpResponseReader reader{"text/event-stream; charset=utf-8", "request-1"};
  const auto consumed = reader.Consume(
      "event: message\r\n"
      "data: "
      "{\"jsonrpc\":\"2.0\",\"id\":\"request-1\",\"result\":{\"tools\":[]}}\r\n"
      "\r\n");

  EXPECT_EXPRESSION(consumed);
  EXPECT_EXPRESSION(consumed->has_value());
  EXPECT_EXPRESSION(**consumed ==
         R"({"jsonrpc":"2.0","id":"request-1","result":{"tools":[]}})");
  // Deliberately do not call Finish(): a real HttpResponseStream may remain
  // open indefinitely after this complete event.
}

void JoinsMultipleDataLinesAndSkipsOtherIds() {
  McpResponseReader reader{"TEXT/EVENT-STREAM", "request-2"};
  auto consumed = reader.Consume(
      "data: {\"jsonrpc\":\"2.0\",\"id\":\"other\",\"result\":{}}\n\n"
      "data: {\"jsonrpc\":\"2.0\",\n"
      "data: \"id\":\"request-2\",\n");
  EXPECT_EXPRESSION(consumed);
  EXPECT_EXPRESSION(!consumed->has_value());

  consumed = reader.Consume("data: \"result\":{\"content\":\"done\"}}\n\n");
  EXPECT_EXPRESSION(consumed);
  EXPECT_EXPRESSION(consumed->has_value());
  EXPECT_EXPRESSION(**consumed == "{\"jsonrpc\":\"2.0\",\n\"id\":\"request-2\",\n"
                       "\"result\":{\"content\":\"done\"}}");
}

void CompletesJsonBeforeEof() {
  McpResponseReader reader{"application/json", "request-3"};
  auto consumed = reader.Consume(
      R"({"jsonrpc":"2.0","id":"request-3","result":{"ok":true}})");
  EXPECT_EXPRESSION(consumed);
  EXPECT_EXPRESSION(consumed->has_value());
}

void RecognizesErrorAndEnforcesBound() {
  McpResponseReader reader{"text/event-stream", "request-4"};
  auto consumed =
      reader.Consume("data: {\"jsonrpc\":\"2.0\",\"id\":\"request-4\","
                     "\"error\":{\"message\":\"denied\"}}\n\n");
  EXPECT_EXPRESSION(consumed);
  EXPECT_EXPRESSION(consumed->has_value());

  McpResponseReader bounded{"application/json", "request-5", 4U};
  consumed = bounded.Consume("12345");
  EXPECT_EXPRESSION(!consumed);
  EXPECT_EXPRESSION(consumed.error().code == McpResponseReadErrorCode::response_too_large);
}

void MergesAndReadsHeadersCaseInsensitively() {
  std::vector<huxerui::HttpHeader> headers{
      {.name = "Content-Type", .value = "application/json"},
      {.name = "Authorization", .value = "old"},
  };
  PutHttpHeader(headers, "authorization", "new");
  EXPECT_EXPRESSION(headers.size() == 2U);
  EXPECT_EXPRESSION(HttpHeaderValue(headers, "AUTHORIZATION") == "new");
  EXPECT_EXPRESSION(HttpHeaderValue(headers, "content-type") == "application/json");
}

} // namespace

TEST(mcp_response_reader_tests, LegacySuite) {
  CompletesSseBeforeEof();
  JoinsMultipleDataLinesAndSkipsOtherIds();
  CompletesJsonBeforeEof();
  RecognizesErrorAndEnforcesBound();
  MergesAndReadsHeadersCaseInsensitively();
}
