#include "gtest_support.h"
#include <string>

#include "application/model_form_service.h"
#include "domain/model_config.h"

namespace {

using linecode::application::ModelDraft;
using linecode::application::ModelFormService;
using linecode::application::ModelValidationCode;
using linecode::domain::ModelProtocol;

ModelDraft ValidDraft() {
  auto draft = ModelFormService::New(std::nullopt, false);
  draft.name = "Test";
  draft.api_key = "secret";
  draft.model_id = "test-model";
  return draft;
}

} // namespace

TEST(model_form_service_tests, LegacySuite) {
  const auto &protocols = linecode::domain::model_protocol_catalog;
  static_assert(protocols.size() == 4);
  EXPECT_EXPRESSION(linecode::domain::ModelProtocolInfo(ModelProtocol::codex_responses)
             .label == "Codex");
  EXPECT_EXPRESSION(linecode::domain::DefaultModelBaseUrl(
             ModelProtocol::codex_responses) == "https://api.openai.com/v1");
  EXPECT_EXPRESSION(linecode::domain::DefaultModelBaseUrl(
             ModelProtocol::anthropic_messages) == "https://api.anthropic.com");
  EXPECT_EXPRESSION(
      linecode::domain::DefaultModelBaseUrl(ModelProtocol::local_gguf).empty());
  EXPECT_EXPRESSION(linecode::domain::SupportsDedicatedCompression(
      ModelProtocol::openai_compatible));
  EXPECT_EXPRESSION(!linecode::domain::SupportsDedicatedCompression(
      ModelProtocol::anthropic_messages));

  const auto &presets = linecode::domain::ModelProviderPresets();
  EXPECT_EXPRESSION(presets.size() == 17);
  const auto deepseek = linecode::domain::FindModelProviderPreset("deepseek");
  EXPECT_EXPRESSION(deepseek.has_value());
  EXPECT_EXPRESSION(deepseek->base_url == "https://api.deepseek.com/v1");
  const auto preset_draft = ModelFormService::New(deepseek, false);
  EXPECT_EXPRESSION(preset_draft.provider_label == "DeepSeek");

  const auto custom = ModelFormService::New(std::nullopt, false);
  EXPECT_EXPRESSION(custom.protocol == ModelProtocol::openai_compatible);
  EXPECT_EXPRESSION(custom.tool_call_limit == "200");
  EXPECT_EXPRESSION(ModelFormService::EffectiveBaseUrl(custom) ==
         "https://api.openai.com/v1");

  auto anthropic = custom;
  anthropic.protocol = ModelProtocol::anthropic_messages;
  EXPECT_EXPRESSION(ModelFormService::EffectiveBaseUrl(anthropic) ==
         "https://api.anthropic.com");

  auto codex = custom;
  codex.protocol = ModelProtocol::codex_responses;
  EXPECT_EXPRESSION(ModelFormService::EffectiveBaseUrl(codex) ==
         "https://api.openai.com/v1");

  EXPECT_EXPRESSION(ModelFormService::ParseContextSize("") == 0);
  EXPECT_EXPRESSION(ModelFormService::ParseContextSize("128k") == 128000);
  EXPECT_EXPRESSION(ModelFormService::ParseContextSize("1M") == 1000000);
  EXPECT_EXPRESSION(ModelFormService::ParseContextSize("12.5k") == 12500);
  EXPECT_EXPRESSION(ModelFormService::ParseContextSize("invalid") == 0);
  EXPECT_EXPRESSION(ModelFormService::FormatContextSize(128000) == "128K");
  EXPECT_EXPRESSION(ModelFormService::FormatContextSize(1000000) == "1M");

  auto valid = ValidDraft();
  auto built = ModelFormService::Build(valid);
  EXPECT_EXPRESSION(built.has_value());
  EXPECT_EXPRESSION(built->model_id == "test-model");
  EXPECT_EXPRESSION(built->tool_call_limit == 200);

  valid.name.clear();
  built = ModelFormService::Build(valid);
  EXPECT_EXPRESSION(built.has_value());
  EXPECT_EXPRESSION(built->name == "test-model");

  auto missing_key = ValidDraft();
  missing_key.api_key = " ";
  const auto missing_key_result = ModelFormService::Build(missing_key);
  EXPECT_EXPRESSION(!missing_key_result.has_value());
  EXPECT_EXPRESSION(missing_key_result.error().code ==
         ModelValidationCode::missing_api_key);

  auto invalid_limit = ValidDraft();
  invalid_limit.tool_call_limit = "-2";
  const auto invalid_limit_result = ModelFormService::Build(invalid_limit);
  EXPECT_EXPRESSION(!invalid_limit_result.has_value());
  EXPECT_EXPRESSION(invalid_limit_result.error().code ==
         ModelValidationCode::invalid_tool_call_limit);
  const auto probe_with_invalid_limit =
      ModelFormService::BuildForProbe(invalid_limit);
  EXPECT_EXPRESSION(probe_with_invalid_limit.has_value());
  EXPECT_EXPRESSION(probe_with_invalid_limit->tool_call_limit == 200);

  auto unlimited = ValidDraft();
  unlimited.tool_call_limit = "-1";
  EXPECT_EXPRESSION(ModelFormService::Build(unlimited)->tool_call_limit == -1);

  auto compression = ValidDraft();
  compression.compression_enabled = true;
  compression.compression_auto = false;
  const auto compression_result = ModelFormService::Build(compression);
  EXPECT_EXPRESSION(!compression_result.has_value());
  EXPECT_EXPRESSION(compression_result.error().code ==
         ModelValidationCode::missing_compression_model_id);

  compression.protocol = ModelProtocol::anthropic_messages;
  compression.compression_model_id = "compressor";
  const auto normalized = ModelFormService::Build(compression);
  EXPECT_EXPRESSION(normalized.has_value());
  EXPECT_EXPRESSION(!normalized->compression_model_enabled);

  auto local = ModelFormService::New(std::nullopt, true);
  EXPECT_EXPRESSION(local.context_size == "4096");
  EXPECT_EXPRESSION(local.protocol == ModelProtocol::local_gguf);
  EXPECT_EXPRESSION(ModelFormService::EffectiveBaseUrl(local).empty());
  EXPECT_EXPRESSION(!ModelFormService::CanQuery(local));

  local.name = "Qwen local";
  const auto local_result = ModelFormService::Build(local);
  EXPECT_EXPRESSION(!local_result.has_value());
  EXPECT_EXPRESSION(local_result.error().code ==
         ModelValidationCode::local_backend_unavailable);
  EXPECT_EXPRESSION(!ModelFormService::CanSave(local));

  auto local_route_draft = local;
  local_route_draft.protocol = ModelProtocol::openai_compatible;
  const auto normalized_local = ModelFormService::Build(local_route_draft);
  EXPECT_EXPRESSION(!normalized_local.has_value());
  EXPECT_EXPRESSION(normalized_local.error().code ==
         ModelValidationCode::local_backend_unavailable);

  const auto local_probe = ModelFormService::BuildForProbe(local);
  EXPECT_EXPRESSION(!local_probe.has_value());
  EXPECT_EXPRESSION(local_probe.error().code ==
         ModelValidationCode::local_backend_unavailable);

  local.name = " ";
  const auto unnamed_local = ModelFormService::Build(local);
  EXPECT_EXPRESSION(!unnamed_local.has_value());
  EXPECT_EXPRESSION(unnamed_local.error().code ==
         ModelValidationCode::local_backend_unavailable);

  auto stored = *ModelFormService::Build(ValidDraft());
  stored.id = "stable-model-id";
  auto edited = ModelFormService::Edit(stored);
  EXPECT_EXPRESSION(edited.name == "Test");
  EXPECT_EXPRESSION(edited.model_id == "test-model");
  edited.name = "Updated";
  edited.model_id = "updated-model";
  const auto updated = ModelFormService::Build(edited);
  EXPECT_EXPRESSION(updated.has_value());
  EXPECT_EXPRESSION(updated->id == "stable-model-id");
  EXPECT_EXPRESSION(updated->name == "Updated");
  EXPECT_EXPRESSION(updated->model_id == "updated-model");
}
