#include "gtest_support.h"
#include <iostream>
#include <string>
#include <string_view>

#include "infrastructure/model_catalog_codec.h"

namespace {

using linecode::domain::ModelConfig;
using linecode::domain::ModelProtocol;
using linecode::infrastructure::BuildModelCatalogRequest;
using linecode::infrastructure::BuildModelProbeRequest;
using linecode::infrastructure::DecodeModelCatalogResponse;
using linecode::infrastructure::DecodeModelProbeResponse;

std::string Header(const linecode::infrastructure::ModelHttpRequestDescriptor &request,
                   const std::string_view name) {
  for (const auto &[candidate, value] : request.headers) {
    if (candidate == name) {
      return value;
    }
  }
  return {};
}

void CatalogRequestsFollowLegacyProtocols() {
  const auto openai = BuildModelCatalogRequest(
      ModelProtocol::openai_compatible, " https://api.example.com/v1/ ",
      "secret");
  EXPECT_EXPRESSION(openai.has_value());
  EXPECT_EXPRESSION(openai->url == "https://api.example.com/v1/models");
  EXPECT_EXPRESSION(Header(*openai, "Authorization") == "Bearer secret");
  EXPECT_EXPRESSION(Header(*openai, "Accept") == "application/json");

  const auto codex = BuildModelCatalogRequest(
      ModelProtocol::codex_responses, "https://api.openai.com/v1", "token");
  EXPECT_EXPRESSION(codex.has_value());
  EXPECT_EXPRESSION(codex->url ==
         "https://api.openai.com/v1/models?client_version=0.120.0");
  EXPECT_EXPRESSION(Header(*codex, "version") == "0.120.0");
  EXPECT_EXPRESSION(Header(*codex, "originator") == "codex_cli_rs");
  EXPECT_EXPRESSION(Header(*codex, "User-Agent") ==
         "codex_cli_rs/0.120.0 (Android; LineCode)");

  const auto anthropic = BuildModelCatalogRequest(
      ModelProtocol::anthropic_messages,
      "https://proxy.example.com/custom/messages", "claude-key");
  EXPECT_EXPRESSION(anthropic.has_value());
  EXPECT_EXPRESSION(anthropic->url == "https://proxy.example.com/v1/models");
  EXPECT_EXPRESSION(Header(*anthropic, "Authorization").empty());
  EXPECT_EXPRESSION(Header(*anthropic, "x-api-key") == "claude-key");
  EXPECT_EXPRESSION(Header(*anthropic, "anthropic-version") == "2023-06-01");
}

void CatalogResponseIsDecodedAndSorted() {
  const auto decoded = DecodeModelCatalogResponse(
      R"json({"object":"list","data":[{"id":"zeta"},{"ignored":true},{"id":"alpha"},{"id":"\u6a21\u578b"},{"id":""}]})json");
  EXPECT_EXPRESSION(decoded.has_value());
  EXPECT_EXPRESSION((decoded.value() ==
          std::vector<std::string>{"alpha", "zeta", "模型"}));

  const auto empty = DecodeModelCatalogResponse(R"json({"data":null})json");
  EXPECT_EXPRESSION(empty.has_value());
  EXPECT_EXPRESSION(empty->empty());

  const auto malformed = DecodeModelCatalogResponse(R"json({"data":[})json");
  EXPECT_EXPRESSION(!malformed.has_value());

  const auto api_error = DecodeModelCatalogResponse(
      R"json({"error":{"message":"invalid token"}})json");
  EXPECT_EXPRESSION(!api_error.has_value());
  EXPECT_EXPRESSION(api_error.error().message == "invalid token");
}

ModelConfig ProbeModel(const ModelProtocol protocol) {
  ModelConfig result;
  result.protocol = protocol;
  result.base_url = "https://api.example.com/v1";
  result.api_key = "key";
  result.model_id = "model-1";
  return result;
}

void ProbeRequestsMatchEachWireProtocol() {
  const auto openai =
      BuildModelProbeRequest(ProbeModel(ModelProtocol::openai_compatible));
  EXPECT_EXPRESSION(openai.has_value());
  EXPECT_EXPRESSION(openai->url == "https://api.example.com/v1/chat/completions");
  EXPECT_EXPRESSION(openai->body ==
         R"json({"model":"model-1","messages":[{"role":"user","content":"Calculate 1+1 and reply with any result."}],"temperature":0.2})json");

  auto codex_model = ProbeModel(ModelProtocol::codex_responses);
  codex_model.base_url = "https://api.example.com/v1/chat/completions";
  const auto codex = BuildModelProbeRequest(codex_model);
  EXPECT_EXPRESSION(codex.has_value());
  EXPECT_EXPRESSION(codex->url == "https://api.example.com/v1/responses");
  EXPECT_EXPRESSION(codex->body.find("\"type\":\"input_text\"") !=
         std::string::npos);
  EXPECT_EXPRESSION(codex->body.find("Calculate 1+1 and reply with any result.") !=
         std::string::npos);
  EXPECT_EXPRESSION(codex->body.find("\"store\":false") != std::string::npos);

  codex_model.base_url =
      "https://example.openai.azure.com/openai/deployments/test";
  const auto azure_codex = BuildModelProbeRequest(codex_model);
  EXPECT_EXPRESSION(azure_codex.has_value());
  EXPECT_EXPRESSION(azure_codex->body.find("\"store\":true") != std::string::npos);

  auto anthropic_model = ProbeModel(ModelProtocol::anthropic_messages);
  anthropic_model.base_url = "https://api.anthropic.com";
  const auto anthropic = BuildModelProbeRequest(anthropic_model);
  EXPECT_EXPRESSION(anthropic.has_value());
  EXPECT_EXPRESSION(anthropic->url == "https://api.anthropic.com/v1/messages");
  EXPECT_EXPRESSION(Header(*anthropic, "x-api-key") == "key");
  EXPECT_EXPRESSION(anthropic->body.find("\"max_tokens\":4096") !=
         std::string::npos);
}

void ProbeResponsesMatchEachWireProtocol() {
  const auto openai = DecodeModelProbeResponse(
      ModelProtocol::openai_compatible,
      R"json({"choices":[{"message":{"content":"2"}}]})json");
  EXPECT_EXPRESSION(openai.has_value() && *openai == "2");

  const auto codex_direct = DecodeModelProbeResponse(
      ModelProtocol::codex_responses, R"json({"output_text":"two"})json");
  EXPECT_EXPRESSION(codex_direct.has_value() && *codex_direct == "two");
  const auto codex_parts = DecodeModelProbeResponse(
      ModelProtocol::codex_responses,
      R"json({"output":[{"content":[{"type":"output_text","text":"t"},{"type":"output_text","text":"wo"}]}]})json");
  EXPECT_EXPRESSION(codex_parts.has_value() && *codex_parts == "two");

  const auto anthropic = DecodeModelProbeResponse(
      ModelProtocol::anthropic_messages,
      R"json({"content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"2"}]})json");
  EXPECT_EXPRESSION(anthropic.has_value() && *anthropic == "2");

  const auto missing = DecodeModelProbeResponse(
      ModelProtocol::openai_compatible, R"json({"choices":[]})json");
  EXPECT_EXPRESSION(!missing.has_value());
}

} // namespace

TEST(model_catalog_codec_tests, LegacySuite) {
  CatalogRequestsFollowLegacyProtocols();
  CatalogResponseIsDecodedAndSorted();
  ProbeRequestsMatchEachWireProtocol();
  ProbeResponsesMatchEachWireProtocol();
  std::cout << "model catalog codec tests passed\n";
}
