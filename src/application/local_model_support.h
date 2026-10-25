#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "domain/model_config.h"

// Build-time switch for the in-process llama.cpp backend.
//
// CMake passes `-DLINCODE_LOCAL_MODEL_ENABLED=1` only for a build configured
// with `-DLINCODE_ENABLE_LOCAL_MODEL=ON`. Every other entry point -- the Gradle
// Android build with the default properties, the GoogleTest targets, and an
// IDE that compiles a single translation unit -- sees the off state. That
// default is what keeps the shipped artifact free of llama.cpp and ggml: with
// the switch off no translation unit includes `llama.h`, nothing links the
// runtime, and no local-model entry is rendered.
#ifndef LINCODE_LOCAL_MODEL_ENABLED
#define LINCODE_LOCAL_MODEL_ENABLED 0
#endif

// Whether the native libraries are packaged into the artifact. A build can
// contain the backend yet keep the libraries out of the package; the packaged
// flag records what the current artifact actually carries.
#ifndef LINCODE_LOCAL_MODEL_PACKAGED
#define LINCODE_LOCAL_MODEL_PACKAGED 0
#endif

// One macro per accelerator backend, so a build names exactly what it linked
// rather than inferring it from the platform.
#ifndef LINCODE_LOCAL_BACKEND_VULKAN
#define LINCODE_LOCAL_BACKEND_VULKAN 0
#endif

#ifndef LINCODE_LOCAL_BACKEND_OPENCL
#define LINCODE_LOCAL_BACKEND_OPENCL 0
#endif

#ifndef LINCODE_LOCAL_BACKEND_CUDA
#define LINCODE_LOCAL_BACKEND_CUDA 0
#endif

#ifndef LINCODE_LOCAL_BACKEND_METAL
#define LINCODE_LOCAL_BACKEND_METAL 0
#endif

// Revision of the llama.cpp runtime this build linked, supplied by CMake. Empty
// for a build without the backend and for a build that linked a prebuilt
// runtime which did not announce a revision.
#ifndef LINCODE_LLAMA_REVISION
#define LINCODE_LLAMA_REVISION ""
#endif

namespace linecode::application {

inline constexpr bool local_model_enabled = LINCODE_LOCAL_MODEL_ENABLED != 0;
inline constexpr bool local_model_packaged = LINCODE_LOCAL_MODEL_PACKAGED != 0;

// NPU acceleration is not supported yet.
//
// Upstream llama.cpp's only NPU backend is the Qualcomm Hexagon one. Building it
// needs the Qualcomm Hexagon SDK: the HTP skels are compiled with the Hexagon
// toolchain and the host side compiles against SDK headers that are not
// redistributable with this project. Depending on that SDK would make an
// otherwise self-contained build require a vendor account, so the feature is
// parked and stated as unsupported instead of being offered and failing. GPU
// acceleration through Vulkan and OpenCL is unaffected.
inline constexpr bool local_model_npu_supported = false;

// A linked compute backend. Kept separate from `domain::LocalAcceleration`
// because more than one backend can serve one acceleration mode: Vulkan and
// OpenCL are both GPU paths.
//
// There is deliberately no NPU backend here. Upstream llama.cpp's only NPU
// backend is the Hexagon one, which cannot be built without the Qualcomm
// Hexagon SDK; this project does not require that SDK, so NPU acceleration is
// not supported yet (see `local_model_npu_supported` below).
enum class LocalModelBackend : std::uint8_t {
  vulkan,
  opencl,
  cuda,
  metal,
  count,
};

inline constexpr std::size_t local_model_backend_count =
    static_cast<std::size_t>(LocalModelBackend::count);

struct LocalModelBackendDescriptor final {
  LocalModelBackend backend;
  std::string_view id;
  // The acceleration mode a model asks for when it wants this backend.
  domain::LocalAcceleration acceleration;

  bool operator==(const LocalModelBackendDescriptor &) const = default;
};

inline constexpr std::array local_model_backend_catalog{
    LocalModelBackendDescriptor{LocalModelBackend::vulkan, "vulkan",
                                domain::LocalAcceleration::gpu},
    LocalModelBackendDescriptor{LocalModelBackend::opencl, "opencl",
                                domain::LocalAcceleration::gpu},
    LocalModelBackendDescriptor{LocalModelBackend::cuda, "cuda",
                                domain::LocalAcceleration::gpu},
    LocalModelBackendDescriptor{LocalModelBackend::metal, "metal",
                                domain::LocalAcceleration::gpu},
};

[[nodiscard]] constexpr const LocalModelBackendDescriptor &
LocalModelBackendInfo(LocalModelBackend backend) noexcept {
  for (const auto &descriptor : local_model_backend_catalog) {
    if (descriptor.backend == backend)
      return descriptor;
  }
  std::unreachable();
}

[[nodiscard]] constexpr bool
LocalModelBackendCompiled(LocalModelBackend backend) noexcept {
  switch (backend) {
  case LocalModelBackend::vulkan:
    return LINCODE_LOCAL_BACKEND_VULKAN != 0;
  case LocalModelBackend::opencl:
    return LINCODE_LOCAL_BACKEND_OPENCL != 0;
  // No NPU backend exists in this build; see the note on the enum above.
  case LocalModelBackend::cuda:
    return LINCODE_LOCAL_BACKEND_CUDA != 0;
  case LocalModelBackend::metal:
    return LINCODE_LOCAL_BACKEND_METAL != 0;
  case LocalModelBackend::count:
    return false;
  }
  return false;
}

// Backends this build linked, in catalog order. Empty when the local model
// support is off, which is the case the UI reads to hide its entry points.
[[nodiscard]] inline std::vector<LocalModelBackend>
EnabledLocalModelBackends() {
  std::vector<LocalModelBackend> enabled;
  if constexpr (!local_model_enabled) {
    return enabled;
  }
  for (const auto &descriptor : local_model_backend_catalog) {
    if (LocalModelBackendCompiled(descriptor.backend)) {
      enabled.push_back(descriptor.backend);
    }
  }
  return enabled;
}

[[nodiscard]] inline std::vector<std::string_view>
EnabledLocalModelBackendIds() {
  std::vector<std::string_view> ids;
  for (const auto backend : EnabledLocalModelBackends()) {
    ids.push_back(LocalModelBackendInfo(backend).id);
  }
  return ids;
}

// The same list as owning strings, for runtime information that outlives the
// static catalog.
[[nodiscard]] inline std::vector<std::string> EnabledLocalModelBackendNames() {
  std::vector<std::string> names;
  for (const auto id : EnabledLocalModelBackendIds()) {
    names.emplace_back(id);
  }
  return names;
}

// Which accelerators a build can offer before any device is inspected. A
// device backend that the build linked may still be unusable at run time
// because its driver is missing; the engine narrows this further with the
// devices ggml actually reports.
[[nodiscard]] constexpr domain::LocalAccelerationAvailability
BuildLocalAccelerationAvailability() noexcept {
  if constexpr (!local_model_enabled) {
    return {};
  }
  return domain::LocalAccelerationAvailability{
      .gpu = LINCODE_LOCAL_BACKEND_VULKAN != 0 ||
             LINCODE_LOCAL_BACKEND_OPENCL != 0 ||
             LINCODE_LOCAL_BACKEND_CUDA != 0 ||
             LINCODE_LOCAL_BACKEND_METAL != 0,
  };
}

[[nodiscard]] constexpr bool
LocalModelAccelerationCompiled(domain::LocalAcceleration acceleration) noexcept {
  if constexpr (!local_model_enabled) {
    return false;
  }
  const auto available = BuildLocalAccelerationAvailability();
  switch (acceleration) {
  case domain::LocalAcceleration::automatic:
  case domain::LocalAcceleration::cpu:
    return true;
  case domain::LocalAcceleration::gpu:
    return available.gpu;
  case domain::LocalAcceleration::count:
    return false;
  }
  return false;
}

[[nodiscard]] constexpr std::string_view LocalModelEngineName() noexcept {
  return local_model_enabled ? std::string_view{"llama.cpp"}
                             : std::string_view{};
}

[[nodiscard]] constexpr std::string_view LocalModelEngineRevision() noexcept {
  return local_model_enabled ? std::string_view{LINCODE_LLAMA_REVISION}
                             : std::string_view{};
}

// The accelerators a model form should offer. Options the build cannot serve
// are removed rather than disabled, matching how the local entry point itself
// disappears when the backend is off.
[[nodiscard]] inline std::vector<domain::LocalAcceleration>
SelectableLocalAccelerations() {
  std::vector<domain::LocalAcceleration> selectable;
  if constexpr (!local_model_enabled) {
    return selectable;
  }
  for (const auto &descriptor : domain::local_acceleration_catalog) {
    if (LocalModelAccelerationCompiled(descriptor.acceleration)) {
      selectable.push_back(descriptor.acceleration);
    }
  }
  return selectable;
}

} // namespace linecode::application
