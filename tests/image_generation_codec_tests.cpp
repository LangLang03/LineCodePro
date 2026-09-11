#include <array>
#include <cassert>
#include <cstddef>
#include <string>

#include "domain/image_generation.h"
#include "domain/model_config.h"
#include "infrastructure/archive_json.h"
#include "infrastructure/image_generation_codec.h"

namespace {

namespace json = linecode::infrastructure::archive_json;
using namespace linecode;

domain::ModelConfig Model(domain::ModelProtocol protocol,
                          std::string model_id = "image-model[128k]") {
  domain::ModelConfig model;
  model.id = "model-1";
  model.name = "Image";
  model.protocol = protocol;
  model.provider_label = "Provider";
  model.base_url = "https://api.example.test/v1/chat/completions";
  model.api_key = "secret";
  model.model_id = std::move(model_id);
  return model;
}

const json::Object &Object(const std::string &source, json::Value &storage) {
  const auto parsed = json::Parse(source);
  assert(parsed);
  storage = *parsed;
  const auto *object = json::AsObject(&storage);
  assert(object);
  return *object;
}

void VerifyToolCodec() {
  infrastructure::JsonImageGenerationToolCodec codec;
  const auto decoded = codec.DecodeArguments(
      R"({"prompt":" Draw [a] cat\nnow ","size":"1024x1536","quality":"high","background":"transparent"})");
  assert(decoded);
  assert(decoded->prompt == "Draw [a] cat\nnow");
  assert(decoded->size == "1024x1536");
  assert(decoded->quality == "high");
  assert(decoded->background == "transparent");
  assert(!codec.DecodeArguments("[]"));
  assert(!codec.DecodeArguments(R"({"prompt":"  "})"));

  const domain::GeneratedImage image{
      .mime_type = "image/png",
      .data_url = "data:image/png;base64,iVBORw0KGgo=",
      .revised_prompt = "a cat",
  };
  json::Value storage{json::Null{}};
  const auto encoded = codec.EncodeToolResult(*decoded, image);
  const auto &result = Object(encoded, storage);
  assert(std::get<bool>(*json::Find(result, "linecode_image_generation")));
  assert(json::AsString(json::Find(result, "display_markdown"))
             ->starts_with("![Draw  a  cat now](data:image/png;base64,"));
  assert(*json::AsString(json::Find(result, "revised_prompt")) == "a cat");
}

void VerifyOpenAiRequest() {
  const domain::ImageGenerationRequest request{
      .prompt = "a terminal wallpaper",
      .size = "1024x1024",
      .quality = "high",
      .background = "transparent",
  };
  const auto built = infrastructure::BuildImageGenerationRequest(
      Model(domain::ModelProtocol::openai_compatible), request);
  assert(built);
  assert(built->url == "https://api.example.test/v1/images/generations");
  assert(built->headers.size() == 1U);
  assert(built->headers.front().second == "Bearer secret");
  json::Value storage{json::Null{}};
  const auto &body = Object(built->body, storage);
  assert(*json::AsString(json::Find(body, "model")) == "image-model");
  assert(*json::AsString(json::Find(body, "response_format")) == "b64_json");
  assert(*json::AsString(json::Find(body, "quality")) == "high");

  const auto modern = infrastructure::BuildImageGenerationRequest(
      Model(domain::ModelProtocol::openai_compatible, "gpt-image-1"),
      request);
  assert(modern);
  storage = json::Null{};
  const auto &modern_body = Object(modern->body, storage);
  assert(json::Find(modern_body, "response_format") == nullptr);
}

void VerifyCodexRequest() {
  const domain::ImageGenerationRequest request{.prompt = "diagram",
                                                .size = "auto",
                                                .quality = {},
                                                .background = {}};
  const auto built = infrastructure::BuildImageGenerationRequest(
      Model(domain::ModelProtocol::codex_responses, "codex-image"), request);
  assert(built);
  assert(built->url == "https://api.example.test/v1/responses");
  assert(built->headers.size() == 4U);
  json::Value storage{json::Null{}};
  const auto &body = Object(built->body, storage);
  assert(*json::AsString(json::Find(body, "input")) == "diagram");
  const auto *tools = json::AsArray(json::Find(body, "tools"));
  assert(tools && tools->size() == 1U);
  const auto *tool = json::AsObject(&tools->front());
  assert(tool);
  assert(*json::AsString(json::Find(*tool, "type")) == "image_generation");

  assert(!infrastructure::BuildImageGenerationRequest(
      Model(domain::ModelProtocol::anthropic_messages), request));
  assert(!infrastructure::BuildImageGenerationRequest(
      Model(domain::ModelProtocol::local_gguf), request));
}

void VerifyResponses() {
  constexpr std::string_view png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB";
  const auto images = infrastructure::DecodeImageGenerationResponse(
      domain::ModelProtocol::openai_compatible,
      std::string{R"({"data":[{"b64_json":")"} + std::string{png} +
          R"(","mime_type":"image/png","revised_prompt":"revised"}]})");
  assert(images);
  assert(images->source == infrastructure::ImagePayloadSource::base64);
  assert(images->payload == png);
  assert(images->revised_prompt == "revised");

  const auto codex = infrastructure::DecodeImageGenerationResponse(
      domain::ModelProtocol::codex_responses,
      std::string{R"({"output":[{"type":"message"},{"type":"image_generation_call","result":")"} +
          std::string{png} + R"(","status":"completed"}]})");
  assert(codex);
  assert(codex->source == infrastructure::ImagePayloadSource::base64);

  assert(!infrastructure::DecodeImageGenerationResponse(
      domain::ModelProtocol::openai_compatible,
      R"({"data":[{"b64_json":"not-an-image"}]})"));
  assert(!infrastructure::DecodeImageGenerationResponse(
      domain::ModelProtocol::codex_responses,
      R"({"output":[{"type":"image_generation_call","status":"failed"}]})"));

  const std::array bytes{std::byte{0x89}, std::byte{0x50}, std::byte{0x4E},
                         std::byte{0x47}, std::byte{0x0D}, std::byte{0x0A},
                         std::byte{0x1A}, std::byte{0x0A}};
  assert(infrastructure::Base64Encode(bytes) == "iVBORw0KGgo=");
  assert(infrastructure::IsUsableImageBase64("iVBORw0KGgo="));
  assert(infrastructure::IsUsableImageDataUrl(
      "data:image/png;base64,iVBORw0KGgo="));
}

} // namespace

int main() {
  VerifyToolCodec();
  VerifyOpenAiRequest();
  VerifyCodexRequest();
  VerifyResponses();
}
