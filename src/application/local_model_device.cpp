#include "application/local_model_device.h"

namespace linecode::application {
domain::LocalAcceleration
ClassifyLocalModelDevice(LocalDeviceKind kind, std::string_view) noexcept {
  switch (kind) {
  case LocalDeviceKind::cpu:
    return domain::LocalAcceleration::cpu;
  case LocalDeviceKind::gpu:
  case LocalDeviceKind::integrated_gpu:
  case LocalDeviceKind::meta:
    return domain::LocalAcceleration::gpu;
  case LocalDeviceKind::accelerator:
    // Accelerator devices -- BLAS, AMX, OpenVINO, ZenDNN -- accelerate the CPU
    // backend rather than replacing it, so they stay on the CPU. This is also
    // where an NPU backend would be recognised once NPU support exists; today
    // nothing can be classified as one because nothing can use it.
    return domain::LocalAcceleration::cpu;
  }
  return domain::LocalAcceleration::cpu;
}

} // namespace linecode::application
