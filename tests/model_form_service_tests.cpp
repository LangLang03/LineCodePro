#include <cassert>
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

int main() {
  const auto &presets = linecode::domain::ModelProviderPresets();
  assert(presets.size() == 17);
  const auto deepseek = linecode::domain::FindModelProviderPreset("deepseek");
  assert(deepseek.has_value());
  assert(deepseek->base_url == "https://api.deepseek.com/v1");
  const auto preset_draft = ModelFormService::New(deepseek, false);
  assert(preset_draft.provider_label == "DeepSeek");

  const auto custom = ModelFormService::New(std::nullopt, false);
  assert(custom.protocol == ModelProtocol::openai_compatible);
  assert(custom.tool_call_limit == "200");
  assert(ModelFormService::EffectiveBaseUrl(custom) ==
         "https://api.openai.com/v1");

  auto anthropic = custom;
  anthropic.protocol = ModelProtocol::anthropic_messages;
  assert(ModelFormService::EffectiveBaseUrl(anthropic) ==
         "https://api.anthropic.com");

  assert(ModelFormService::ParseContextSize("") == 0);
  assert(ModelFormService::ParseContextSize("128k") == 128000);
  assert(ModelFormService::ParseContextSize("1M") == 1000000);
  assert(ModelFormService::ParseContextSize("12.5k") == 12500);
  assert(ModelFormService::ParseContextSize("invalid") == 0);
  assert(ModelFormService::FormatContextSize(128000) == "128K");
  assert(ModelFormService::FormatContextSize(1000000) == "1M");

  auto valid = ValidDraft();
  auto built = ModelFormService::Build(valid);
  assert(built.has_value());
  assert(built->model_id == "test-model");
  assert(built->tool_call_limit == 200);

  valid.name.clear();
  built = ModelFormService::Build(valid);
  assert(built.has_value());
  assert(built->name == "test-model");

  auto missing_key = ValidDraft();
  missing_key.api_key = " ";
  const auto missing_key_result = ModelFormService::Build(missing_key);
  assert(!missing_key_result.has_value());
  assert(missing_key_result.error().code ==
         ModelValidationCode::missing_api_key);

  auto invalid_limit = ValidDraft();
  invalid_limit.tool_call_limit = "-2";
  const auto invalid_limit_result = ModelFormService::Build(invalid_limit);
  assert(!invalid_limit_result.has_value());
  assert(invalid_limit_result.error().code ==
         ModelValidationCode::invalid_tool_call_limit);
  const auto probe_with_invalid_limit =
      ModelFormService::BuildForProbe(invalid_limit);
  assert(probe_with_invalid_limit.has_value());
  assert(probe_with_invalid_limit->tool_call_limit == 200);

  auto unlimited = ValidDraft();
  unlimited.tool_call_limit = "-1";
  assert(ModelFormService::Build(unlimited)->tool_call_limit == -1);

  auto compression = ValidDraft();
  compression.compression_enabled = true;
  compression.compression_auto = false;
  const auto compression_result = ModelFormService::Build(compression);
  assert(!compression_result.has_value());
  assert(compression_result.error().code ==
         ModelValidationCode::missing_compression_model_id);

  compression.protocol = ModelProtocol::anthropic_messages;
  compression.compression_model_id = "compressor";
  const auto normalized = ModelFormService::Build(compression);
  assert(normalized.has_value());
  assert(!normalized->compression_model_enabled);

  const auto local = ModelFormService::New(std::nullopt, true);
  assert(local.context_size == "4096");
  const auto local_result = ModelFormService::Build(local);
  assert(!local_result.has_value());
  assert(local_result.error().code ==
         ModelValidationCode::local_backend_unavailable);

  const auto edited =
      ModelFormService::Edit(*ModelFormService::Build(ValidDraft()));
  assert(edited.name == "Test");
  assert(edited.model_id == "test-model");
}
