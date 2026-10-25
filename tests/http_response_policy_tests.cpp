// Contract tests for the response-side transport policy the completion gateway
// shares: how a rate-limit delay is read, and which SSE frames are heartbeats
// rather than malformed chunks.

#include "gtest_support.h"

#include <string>
#include <vector>

#include "infrastructure/http_response_policy.h"

namespace {

using linecode::infrastructure::HostOfUrl;
using linecode::infrastructure::IsIgnorableSsePayload;
using linecode::infrastructure::ParseRetryAfterSeconds;

std::vector<huxerui::HttpHeader> Headers(std::string name, std::string value) {
  return {huxerui::HttpHeader{.name = std::move(name), .value = std::move(value)}};
}

// delay-seconds, including the whitespace a proxy may add.
void ReadsNumericRetryAfter() {
  const auto plain = ParseRetryAfterSeconds(Headers("Retry-After", "5"));
  EXPECT_EXPRESSION(plain.has_value());
  EXPECT_EXPRESSION(*plain == 5);

  const auto padded = ParseRetryAfterSeconds(Headers("Retry-After", "  12  "));
  EXPECT_EXPRESSION(padded.has_value());
  EXPECT_EXPRESSION(*padded == 12);

  const auto lowercase = ParseRetryAfterSeconds(Headers("retry-after", "7"));
  EXPECT_EXPRESSION(lowercase.has_value());
  EXPECT_EXPRESSION(*lowercase == 7);
}

// Anything the client cannot act on is ignored instead of guessed.
void IgnoresUnusableRetryAfter() {
  EXPECT_EXPRESSION(!ParseRetryAfterSeconds({}).has_value());
  EXPECT_EXPRESSION(
      !ParseRetryAfterSeconds(Headers("Retry-After", "")).has_value());
  EXPECT_EXPRESSION(
      !ParseRetryAfterSeconds(Headers("Retry-After", "   ")).has_value());
  // An HTTP-date needs a clock; the retry policy only works in durations.
  EXPECT_EXPRESSION(!ParseRetryAfterSeconds(Headers("Retry-After",
                                                    "Wed, 21 Oct 2026 07:28:00 GMT"))
                         .has_value());
  EXPECT_EXPRESSION(
      !ParseRetryAfterSeconds(Headers("Retry-After", "-5")).has_value());
  EXPECT_EXPRESSION(
      !ParseRetryAfterSeconds(Headers("Retry-After", "5s")).has_value());
  EXPECT_EXPRESSION(
      !ParseRetryAfterSeconds(Headers("Content-Type", "5")).has_value());
}

// A bare or whitespace-only `data:` frame is a heartbeat.
void TreatsEmptySsePayloadAsAHeartbeat() {
  EXPECT_EXPRESSION(IsIgnorableSsePayload(""));
  EXPECT_EXPRESSION(IsIgnorableSsePayload("   "));
  EXPECT_EXPRESSION(IsIgnorableSsePayload("\r\n"));
  EXPECT_EXPRESSION(IsIgnorableSsePayload("\t"));
}

// Real payloads keep failing loudly: a malformed chunk is still an error.
void KeepsRealPayloadsDispatchable() {
  EXPECT_EXPRESSION(!IsIgnorableSsePayload("[DONE]"));
  EXPECT_EXPRESSION(!IsIgnorableSsePayload("{}"));
  EXPECT_EXPRESSION(!IsIgnorableSsePayload("{\"choices\":[]}"));
  EXPECT_EXPRESSION(!IsIgnorableSsePayload(" [DONE] "));
}

// The redirect guard compares hosts, so the extractor has to ignore the parts
// that legitimately change on a redirect (path, port, case).
void ExtractsTheHostForRedirectChecks() {
  EXPECT_EXPRESSION(HostOfUrl("https://api.example.com/v1/chat") ==
                    "api.example.com");
  EXPECT_EXPRESSION(HostOfUrl("https://API.Example.com:8443/v1") ==
                    "api.example.com");
  EXPECT_EXPRESSION(HostOfUrl("http://127.0.0.1:18080/v1") == "127.0.0.1");
  EXPECT_EXPRESSION(HostOfUrl("https://user:pw@api.example.com/v1") ==
                    "api.example.com");
  EXPECT_EXPRESSION(HostOfUrl("https://[::1]:8080/v1") == "[::1]");
  EXPECT_EXPRESSION(HostOfUrl("https://api.example.com") == "api.example.com");
  EXPECT_EXPRESSION(HostOfUrl("").empty());
  EXPECT_EXPRESSION(HostOfUrl("not a url").empty());
  EXPECT_EXPRESSION(HostOfUrl("https:///v1").empty());
}

} // namespace

TEST(http_response_policy_tests, ReadsNumericRetryAfter) {
  ReadsNumericRetryAfter();
}
TEST(http_response_policy_tests, IgnoresUnusableRetryAfter) {
  IgnoresUnusableRetryAfter();
}
TEST(http_response_policy_tests, TreatsEmptySsePayloadAsAHeartbeat) {
  TreatsEmptySsePayloadAsAHeartbeat();
}
TEST(http_response_policy_tests, KeepsRealPayloadsDispatchable) {
  KeepsRealPayloadsDispatchable();
}
TEST(http_response_policy_tests, ExtractsTheHostForRedirectChecks) {
  ExtractsTheHostForRedirectChecks();
}
