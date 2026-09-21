#include "application/data_archive.h"

#include <string>

namespace linecode::application {

std::string DefaultArchiveName(std::chrono::milliseconds since_epoch) {
  return "LineCode-" + std::to_string(since_epoch.count()) + ".linecode";
}

std::string ExportSuccessMessage(const domain::ArchiveSummary &summary) {
  return "已导出 .linecode：" + std::to_string(summary.conversations) +
         " 个会话，" + std::to_string(summary.models) + " 个模型，" +
         std::to_string(summary.settings) + " 项设置。";
}

std::string ImportSuccessMessage(const domain::ArchiveSummary &summary) {
  return "已导入 .linecode：" + std::to_string(summary.conversations) +
         " 个会话，" + std::to_string(summary.models) + " 个模型，" +
         std::to_string(summary.settings) + " 项设置，" +
         std::to_string(summary.restored_files) + " 个工作区文件。";
}

} // namespace linecode::application
