#include <array>
#include "gtest_support.h"
#include <cstddef>
#include <string>

#include "domain/image_understanding.h"
#include "domain/model_config.h"
#include "infrastructure/archive_json.h"
#include "infrastructure/image_understanding_codec.h"

namespace {

namespace json = linecode::infrastructure::archive_json;
using namespace linecode;

domain::ModelConfig Model(domain::ModelProtocol protocol) {
  domain::ModelConfig model;
  model.id = "vision";
  model.name = "Vision";
  model.protocol = protocol;
  model.base_url = "https://api.example.test/v1/chat/completions";
  model.api_key = "secret";
  model.model_id = "vision-model[128k]";
  return model;
}

domain::WorkspaceImage Image(std::string path = "assets/sample.png") {
  return {.resolved_path = std::move(path),
          .mime_type = "image/png",
          .bytes = {std::byte{0x89}, std::byte{0x50}, std::byte{0x4E},
                    std::byte{0x47}, std::byte{0x0D}, std::byte{0x0A},
                    std::byte{0x1A}, std::byte{0x0A}}};
}

const json::Object &Parse(const std::string &text, json::Value &storage) {
  auto parsed = json::Parse(text);
  EXPECT_EXPRESSION(parsed);
  storage = std::move(*parsed);
  const auto *object = json::AsObject(&storage);
  EXPECT_EXPRESSION(object);
  return *object;
}

void VerifyArgumentsAndImageValidation() {
  infrastructure::JsonImageUnderstandingToolCodec codec;
  auto request = codec.DecodeArguments(
      R"({"image_path":" assets/sample.png ","prompt":" identify "})");
  EXPECT_EXPRESSION(request);
  EXPECT_EXPRESSION(request->path == "assets/sample.png");
  EXPECT_EXPRESSION(request->prompt == "identify");
  auto default_prompt =
      codec.DecodeArguments(R"({"file_path":"assets/sample.png"})");
  EXPECT_EXPRESSION(default_prompt);
  EXPECT_EXPRESSION(default_prompt->prompt ==
         "Please describe the content of this image.");
  EXPECT_EXPRESSION(!codec.DecodeArguments("[]"));
  EXPECT_EXPRESSION(!codec.DecodeArguments(R"({"path":" "})"));

  auto image = Image();
  auto validated = codec.ValidateImage(image.resolved_path, image.bytes);
  EXPECT_EXPRESSION(validated);
  EXPECT_EXPRESSION(validated->mime_type == "image/png");
  image.bytes.front() = std::byte{0};
  EXPECT_EXPRESSION(!codec.ValidateImage(image.resolved_path, image.bytes));
  EXPECT_EXPRESSION(!codec.ValidateImage("sample.svg", {std::byte{'<'}}));
}

void VerifyOpenAi() {
  const domain::ImageUnderstandingRequest request{.path = "sample.png",
                                                   .prompt = "看图"};
  auto built = infrastructure::BuildImageUnderstandingRequest(
      Model(domain::ModelProtocol::openai_compatible), "system", request,
      Image());
  EXPECT_EXPRESSION(built);
  EXPECT_EXPRESSION(built->url == "https://api.example.test/v1/chat/completions");
  json::Value storage{json::Null{}};
  const auto &root = Parse(built->body, storage);
  EXPECT_EXPRESSION(*json::AsString(json::Find(root, "model")) == "vision-model");
  const auto *messages = json::AsArray(json::Find(root, "messages"));
  EXPECT_EXPRESSION(messages && messages->size() == 2U);
  const auto *user = json::AsObject(&messages->back());
  const auto *content = json::AsArray(json::Find(*user, "content"));
  EXPECT_EXPRESSION(content && content->size() == 2U);
  const auto *image_part = json::AsObject(&content->back());
  const auto *image_url =
      json::AsObject(json::Find(*image_part, "image_url"));
  EXPECT_EXPRESSION(json::AsString(json::Find(*image_url, "url"))
             ->starts_with("data:image/png;base64,iVBORw0KGgo="));

  auto decoded = infrastructure::DecodeImageUnderstandingResponse(
      domain::ModelProtocol::openai_compatible,
      R"({"choices":[{"message":{"content":"a terminal"}}]})");
  EXPECT_EXPRESSION(decoded && *decoded == "a terminal");
}

void VerifyCodexAndAnthropic() {
  const domain::ImageUnderstandingRequest request{.path = "sample.png",
                                                   .prompt = "describe"};
  auto codex = infrastructure::BuildImageUnderstandingRequest(
      Model(domain::ModelProtocol::codex_responses), "system", request,
      Image());
  EXPECT_EXPRESSION(codex && codex->url == "https://api.example.test/v1/responses");
  EXPECT_EXPRESSION(codex->headers.size() == 4U);
  auto codex_text = infrastructure::DecodeImageUnderstandingResponse(
      domain::ModelProtocol::codex_responses,
      R"({"output_text":"diagram","output":[{"type":"message","content":[{"type":"output_text","text":"diagram"}]}]})");
  EXPECT_EXPRESSION(codex_text && *codex_text == "diagram");
  auto codex_structured = infrastructure::DecodeImageUnderstandingResponse(
      domain::ModelProtocol::codex_responses,
      R"({"output":[{"type":"message","content":[{"type":"output_text","text":"structured"}]}]})");
  EXPECT_EXPRESSION(codex_structured && *codex_structured == "structured");

  auto anthropic = infrastructure::BuildImageUnderstandingRequest(
      Model(domain::ModelProtocol::anthropic_messages), "system", request,
      Image());
  EXPECT_EXPRESSION(anthropic &&
         anthropic->url == "https://api.example.test/v1/messages");
  json::Value storage{json::Null{}};
  const auto &root = Parse(anthropic->body, storage);
  const auto *messages = json::AsArray(json::Find(root, "messages"));
  const auto *user = json::AsObject(&messages->front());
  const auto *content = json::AsArray(json::Find(*user, "content"));
  const auto *image = json::AsObject(&content->back());
  const auto *source = json::AsObject(json::Find(*image, "source"));
  EXPECT_EXPRESSION(*json::AsString(json::Find(*source, "media_type")) == "image/png");
  auto anthropic_text = infrastructure::DecodeImageUnderstandingResponse(
      domain::ModelProtocol::anthropic_messages,
      R"({"content":[{"type":"text","text":"terminal screenshot"}]})");
  EXPECT_EXPRESSION(anthropic_text && *anthropic_text == "terminal screenshot");

  EXPECT_EXPRESSION(!infrastructure::BuildImageUnderstandingRequest(
      Model(domain::ModelProtocol::local_gguf), "system", request, Image()));
}

} // namespace

TEST(image_understanding_codec_tests, LegacySuite) {
  VerifyArgumentsAndImageValidation();
  VerifyOpenAi();
  VerifyCodexAndAnthropic();
}
