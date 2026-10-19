#pragma once

#include <cstdint>
#include <string_view>

#include "domain/model_config.h"

namespace linecode::application {

// The device kinds a compute runtime can report. Mirrors the ggml device enum
// without depending on it, so the classification can be tested on a build that
// does not link llama.cpp.
enum class LocalDeviceKind : std::uint8_t {
  cpu,
  gpu,
  integrated_gpu,
  accelerator,
  meta,
};

// Maps a reported device onto the acceleration mode that can use it.
//
// ggml reports BLAS and AMX -- CPU-side tensor accelerators -- as `accelerator`
// devices, so those stay on the CPU rather than being promoted to a device the
// user did not ask for. No NPU mode is classified because none is shipped; an
// NPU backend would be recognised here once NPU support exists.
//
// The reported name is currently unused, and is kept in the signature because
// the classification of an accelerator is exactly what must consult it.
[[nodiscard]] domain::LocalAcceleration ClassifyLocalModelDevice(
    LocalDeviceKind kind, std::string_view name) noexcept;

} // namespace linecode::application
