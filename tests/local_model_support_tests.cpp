#include "gtest_support.h"

#include <string>
#include <vector>

#include "application/local_model_device.h"
#include "application/local_model_support.h"
#include "application/model_form_service.h"
#include "domain/model_config.h"
#include "presentation/model_form_presentation.h"

namespace {

using linecode::application::ClassifyLocalModelDevice;
using linecode::application::LocalDeviceKind;
using linecode::application::ModelDraft;
using linecode::application::ModelFormService;
using linecode::application::ModelValidationCode;
using linecode::domain::LocalAcceleration;
using linecode::domain::LocalAccelerationAvailability;
using linecode::domain::ModelConfig;
using linecode::domain::ModelProtocol;
using linecode::presentation::LocalModelProtocolSelectable;
using linecode::presentation::ProtocolTabAction;
using linecode::presentation::ResolveProtocolTabDecision;

} // namespace

// The default configuration is the one the shipped artifact uses, so the whole
// point of the feature switch is that this translation unit -- compiled without
// any CMake definition -- sees the backend as absent.
TEST(local_model_support_tests, BuildWithoutTheBackendReportsItAsAbsent) {
  EXPECT_EXPRESSION(!linecode::application::local_model_enabled);
  EXPECT_EXPRESSION(!linecode::application::local_model_packaged);
  EXPECT_EXPRESSION(linecode::application::LocalModelEngineName().empty());
  EXPECT_EXPRESSION(linecode::application::LocalModelEngineRevision().empty());
  EXPECT_EXPRESSION(linecode::application::EnabledLocalModelBackends().empty());
  EXPECT_EXPRESSION(
      linecode::application::EnabledLocalModelBackendIds().empty());
  EXPECT_EXPRESSION(
      linecode::application::SelectableLocalAccelerations().empty());
  EXPECT_EXPRESSION(
      linecode::application::BuildLocalAccelerationAvailability() ==
      LocalAccelerationAvailability{});
  EXPECT_EXPRESSION(!linecode::application::LocalModelAccelerationCompiled(
      LocalAcceleration::cpu));
  EXPECT_EXPRESSION(!linecode::application::LocalModelAccelerationCompiled(
      LocalAcceleration::gpu));
  // NPU acceleration is parked, and the build says so in one constant rather
  // than by omitting code in several places.
  EXPECT_EXPRESSION(!linecode::application::local_model_npu_supported);
}

TEST(local_model_support_tests, LocalEntryPointsAreHidden) {
  EXPECT_EXPRESSION(!LocalModelProtocolSelectable());
  // Entering the local form and leaving it are both refused, so a leftover
  // navigation path cannot reach a form this build cannot serve.
  EXPECT_EXPRESSION(!ResolveProtocolTabDecision(
                        ModelProtocol::openai_compatible,
                        ModelProtocol::local_gguf, false)
                         .enabled);
  EXPECT_EXPRESSION(!ResolveProtocolTabDecision(ModelProtocol::local_gguf,
                                                ModelProtocol::openai_compatible,
                                                false)
                         .enabled);
  // The protocols this build does serve are untouched.
  const auto codex = ResolveProtocolTabDecision(
      ModelProtocol::openai_compatible, ModelProtocol::codex_responses, false);
  EXPECT_EXPRESSION(codex.enabled);
  EXPECT_EXPRESSION(codex.action == ProtocolTabAction::select_protocol);
}

TEST(local_model_support_tests, LocalDraftsCannotBeSaved) {
  auto draft = ModelFormService::New(std::nullopt, true);
  draft.local_model_path = "/tmp/model.gguf";
  EXPECT_EXPRESSION(draft.protocol == ModelProtocol::local_gguf);
  EXPECT_EXPRESSION(draft.local);

  const auto built = ModelFormService::Build(draft);
  EXPECT_EXPRESSION(!built.has_value());
  EXPECT_EXPRESSION(built.error().code ==
                    ModelValidationCode::local_backend_unavailable);
  EXPECT_EXPRESSION(!ModelFormService::CanSave(draft));
  // A local model has no HTTP endpoint, so it is never queryable regardless of
  // whether the backend is compiled in.
  EXPECT_EXPRESSION(!ModelFormService::CanQuery(draft));
}

// ggml reports BLAS/AMX/OpenVINO as accelerator devices. None of them replaces
// the CPU backend, and no NPU mode is shipped, so an accelerator stays on the
// CPU instead of being advertised as a device the user did not ask for.
TEST(local_model_support_tests, AcceleratorDevicesStayOnTheCpu) {
  EXPECT_EXPRESSION(ClassifyLocalModelDevice(LocalDeviceKind::cpu, "CPU") ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(ClassifyLocalModelDevice(LocalDeviceKind::gpu, "CUDA0") ==
                    LocalAcceleration::gpu);
  EXPECT_EXPRESSION(
      ClassifyLocalModelDevice(LocalDeviceKind::integrated_gpu, "Vulkan0") ==
      LocalAcceleration::gpu);
  EXPECT_EXPRESSION(ClassifyLocalModelDevice(LocalDeviceKind::meta, "Meta0") ==
                    LocalAcceleration::gpu);
  for (const auto name : {"BLAS", "AMX", "OpenVINO", "QNN", "NNAPI",
                          "hexagon-npu", "HTP", "Mystery"}) {
    EXPECT_EXPRESSION(ClassifyLocalModelDevice(LocalDeviceKind::accelerator,
                                               name) == LocalAcceleration::cpu);
  }
}

TEST(local_model_support_tests, AccelerationPreferenceDegradesInsteadOfFailing) {
  const LocalAccelerationAvailability gpu_available{.gpu = true};
  const LocalAccelerationAvailability nothing{};

  EXPECT_EXPRESSION(linecode::domain::ResolveLocalAcceleration(
                        LocalAcceleration::automatic, gpu_available) ==
                    LocalAcceleration::gpu);
  EXPECT_EXPRESSION(linecode::domain::ResolveLocalAcceleration(
                        LocalAcceleration::automatic, nothing) ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(linecode::domain::ResolveLocalAcceleration(
                        LocalAcceleration::gpu, nothing) ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(linecode::domain::ResolveLocalAcceleration(
                        LocalAcceleration::gpu, gpu_available) ==
                    LocalAcceleration::gpu);
  EXPECT_EXPRESSION(linecode::domain::ResolveLocalAcceleration(
                        LocalAcceleration::cpu, gpu_available) ==
                    LocalAcceleration::cpu);

  // A device mode offloads every layer, the CPU mode offloads none, and
  // llama.cpp reads a negative layer count as "all".
  EXPECT_EXPRESSION(linecode::domain::LocalGpuLayerCount(
                        LocalAcceleration::gpu) < 0);
  EXPECT_EXPRESSION(linecode::domain::LocalGpuLayerCount(
                        LocalAcceleration::cpu) == 0);
}

TEST(local_model_support_tests, AccelerationRoundTripsThroughStorage) {
  for (const auto &descriptor : linecode::domain::local_acceleration_catalog) {
    EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration(
                          descriptor.storage_name) == descriptor.acceleration);
    EXPECT_EXPRESSION(linecode::domain::LocalAccelerationStorageName(
                          descriptor.acceleration) == descriptor.storage_name);
  }
  // Aliases a hand-edited archive or an older build may contain.
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("vulkan") ==
                    LocalAcceleration::gpu);
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("opencl") ==
                    LocalAcceleration::gpu);
  // NPU-era spellings from outside this app degrade to the CPU, which is what
  // the engine would do with them anyway.
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("npu") ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("nnapi") ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("qnn") ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("hexagon") ==
                    LocalAcceleration::cpu);
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("") ==
                    LocalAcceleration::automatic);
  EXPECT_EXPRESSION(linecode::domain::ParseLocalAcceleration("nonsense") ==
                    LocalAcceleration::automatic);
  static_assert(linecode::domain::local_acceleration_catalog.size() ==
                linecode::domain::local_acceleration_count);
}

TEST(local_model_support_tests, NormalizeKeepsLocalStateOnlyOnLocalModels) {
  ModelConfig remote;
  remote.protocol = ModelProtocol::openai_compatible;
  remote.local_model_path = "/tmp/stale.gguf";
  remote.local_acceleration = LocalAcceleration::gpu;
  remote.Normalize();
  EXPECT_EXPRESSION(remote.local_model_path.empty());
  EXPECT_EXPRESSION(remote.local_acceleration == LocalAcceleration::automatic);
  EXPECT_EXPRESSION(!remote.IsLocal());

  ModelConfig local;
  local.protocol = ModelProtocol::local_gguf;
  local.local_model_path = "  /tmp/model.gguf  ";
  local.local_acceleration = LocalAcceleration::count;
  local.Normalize();
  EXPECT_EXPRESSION(local.local_model_path == "/tmp/model.gguf");
  EXPECT_EXPRESSION(local.local_acceleration == LocalAcceleration::automatic);
  EXPECT_EXPRESSION(local.IsLocal());
}
