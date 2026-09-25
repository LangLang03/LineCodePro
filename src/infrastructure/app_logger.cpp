#include "infrastructure/app_logger.h"

#include <atomic>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <string>

#if defined(__ANDROID__)
#include <android/log.h>
#else
#include <cstdio>
#endif

namespace linecode::infrastructure {
namespace {

std::mutex log_mutex;
std::filesystem::path log_directory;
std::atomic<unsigned long long> sequence{};

} // namespace

void ConfigureAppLogger(std::string_view error_log_directory) noexcept {
  try {
    std::lock_guard lock(log_mutex);
    log_directory = std::filesystem::path{error_log_directory};
  } catch (...) {
  }
}

void LogAppError(std::string_view event) noexcept {
  // Logging must never turn a recoverable failure into another crash.
  try {
    const std::string message{event};
#if defined(__ANDROID__)
    __android_log_write(ANDROID_LOG_ERROR, "LineCodePro", message.c_str());
#else
    std::fprintf(stderr, "LineCodePro: %s\n", message.c_str());
#endif
    std::lock_guard lock(log_mutex);
    if (log_directory.empty())
      return;
    std::filesystem::create_directories(log_directory);
    const auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                               std::chrono::system_clock::now().time_since_epoch())
                               .count();
    const auto id = sequence.fetch_add(1, std::memory_order_relaxed);
    const auto path = log_directory /
                      ("cpp-" + std::to_string(timestamp) + "-" +
                       std::to_string(id) + ".log");
    std::ofstream output(path, std::ios::binary);
    output << "C++ " << event << '\n';
  } catch (...) {
    // The platform log entry above remains available if disk logging fails.
  }
}

} // namespace linecode::infrastructure
