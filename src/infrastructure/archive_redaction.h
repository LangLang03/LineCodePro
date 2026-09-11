#pragma once

#include <string>
#include <string_view>

namespace linecode::infrastructure {

[[nodiscard]] bool IsSensitiveArchiveName(std::string_view value);
[[nodiscard]] std::string RedactArchiveJsonSecrets(std::string_view raw);
[[nodiscard]] std::string RedactArchiveHeaders(std::string_view raw);
[[nodiscard]] std::string RedactArchiveSettingValue(std::string_view key,
                                                    std::string_view raw);

} // namespace linecode::infrastructure
