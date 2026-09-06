#pragma once

#include <chrono>
#include <string>

#include "domain/data_archive.h"

namespace linecode::application {

[[nodiscard]] std::string
DefaultArchiveName(std::chrono::milliseconds since_epoch);
[[nodiscard]] std::string
ExportSuccessMessage(const domain::ArchiveSummary &summary);
[[nodiscard]] std::string
ImportSuccessMessage(const domain::ArchiveSummary &summary);

} // namespace linecode::application
