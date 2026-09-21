#include "gtest_support.h"
#include <algorithm>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

#include <huxerui/file.h>
#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>
#include <zlib.h>

#include "application/skill_repository.h"
#include "domain/skill.h"
#include "infrastructure/hux_skill_files.h"
#include "infrastructure/linecode_zip.h"
#include "infrastructure/skill_hub_codec.h"

namespace {

using huxerui::Bytes;
using linecode::application::SkillDirectoryPackage;
using linecode::application::SkillInstallRequest;
using linecode::application::SkillMarkdownPackage;
using linecode::application::SkillRoots;
using linecode::application::SkillZipPackage;
using linecode::domain::SkillLocation;
using linecode::infrastructure::HuxSkillFiles;

class FixedClock final : public linecode::application::SkillClock {
public:
  std::int64_t NowMilliseconds() const noexcept override { return 123456; }
};

class TemporaryDirectory final {
public:
  TemporaryDirectory() {
    const auto seed = std::chrono::steady_clock::now().time_since_epoch().count();
    path_ = std::filesystem::temp_directory_path() /
            ("linecode-skill-tests-" + std::to_string(seed));
    EXPECT_EXPRESSION(std::filesystem::create_directories(path_));
  }

  ~TemporaryDirectory() {
    std::error_code ignored;
    std::filesystem::remove_all(path_, ignored);
  }

  [[nodiscard]] const std::filesystem::path &Path() const { return path_; }

private:
  std::filesystem::path path_;
};

Bytes Text(const std::string_view text) {
  const auto *first = reinterpret_cast<const std::byte *>(text.data());
  return text.empty() ? Bytes{} : Bytes(first, first + text.size());
}

void Put16(Bytes &bytes, const std::uint16_t value) {
  bytes.push_back(static_cast<std::byte>(value & 0xFFU));
  bytes.push_back(static_cast<std::byte>((value >> 8U) & 0xFFU));
}

void Put32(Bytes &bytes, const std::uint32_t value) {
  Put16(bytes, static_cast<std::uint16_t>(value));
  Put16(bytes, static_cast<std::uint16_t>(value >> 16U));
}

void PutText(Bytes &bytes, const std::string_view text) {
  const auto content = Text(text);
  bytes.insert(bytes.end(), content.begin(), content.end());
}

Bytes GitHubStyleZip() {
  struct Entry final {
    std::string name;
    Bytes content;
    std::uint32_t offset{};
    std::uint32_t checksum{};
  };
  std::vector<Entry> entries{
      {.name = "repo-main/", .content = {}},
      {.name = "repo-main/SKILL.md",
       .content = Text("# ZIP Skill\nzip description\n")},
      {.name = "repo-main/scripts/", .content = {}},
      {.name = "repo-main/scripts/run.py", .content = Text("print('ok')")},
  };
  Bytes bytes;
  for (auto &entry : entries) {
    entry.offset = static_cast<std::uint32_t>(bytes.size());
    entry.checksum = static_cast<std::uint32_t>(
        crc32(0, reinterpret_cast<const Bytef *>(entry.content.data()),
              static_cast<uInt>(entry.content.size())));
    Put32(bytes, 0x04034B50U);
    Put16(bytes, 20);
    Put16(bytes, 0x0800);
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put32(bytes, entry.checksum);
    Put32(bytes, static_cast<std::uint32_t>(entry.content.size()));
    Put32(bytes, static_cast<std::uint32_t>(entry.content.size()));
    Put16(bytes, static_cast<std::uint16_t>(entry.name.size()));
    Put16(bytes, 0);
    PutText(bytes, entry.name);
    bytes.insert(bytes.end(), entry.content.begin(), entry.content.end());
  }
  const auto central_offset = static_cast<std::uint32_t>(bytes.size());
  for (const auto &entry : entries) {
    Put32(bytes, 0x02014B50U);
    Put16(bytes, 20);
    Put16(bytes, 20);
    Put16(bytes, 0x0800);
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put32(bytes, entry.checksum);
    Put32(bytes, static_cast<std::uint32_t>(entry.content.size()));
    Put32(bytes, static_cast<std::uint32_t>(entry.content.size()));
    Put16(bytes, static_cast<std::uint16_t>(entry.name.size()));
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put16(bytes, 0);
    Put32(bytes, entry.name.ends_with('/') ? 0x10U : 0U);
    Put32(bytes, entry.offset);
    PutText(bytes, entry.name);
  }
  const auto central_size = static_cast<std::uint32_t>(bytes.size()) -
                            central_offset;
  Put32(bytes, 0x06054B50U);
  Put16(bytes, 0);
  Put16(bytes, 0);
  Put16(bytes, static_cast<std::uint16_t>(entries.size()));
  Put16(bytes, static_cast<std::uint16_t>(entries.size()));
  Put32(bytes, central_size);
  Put32(bytes, central_offset);
  Put16(bytes, 0);
  return bytes;
}

void DomainMetadataAndMarkdownMatchLegacy() {
  const auto frontmatter = linecode::domain::ParseSkillMetadata(
      "---\nname: 'PDF Helper'\ndescription: \"中文说明\"\n---\n# Ignored\n",
      "fallback");
  EXPECT_EXPRESSION(frontmatter.name == "PDF Helper");
  EXPECT_EXPRESSION(frontmatter.description == "中文说明");

  const auto fallback = linecode::domain::ParseSkillMetadata(
      "# Markdown Name\n\nFirst description line\n", "directory");
  EXPECT_EXPRESSION(fallback.name == "Markdown Name");
  EXPECT_EXPRESSION(fallback.description == "First description line");

  const auto generated =
      linecode::domain::BuildSkillMarkdown("A: Skill", "line\nbreak", "");
  EXPECT_EXPRESSION(generated);
  EXPECT_EXPRESSION(generated->contains("name: \"A: Skill\""));
  EXPECT_EXPRESSION(generated->contains("description: \"line\\nbreak\""));
  EXPECT_EXPRESSION(generated->contains("## 触发条件"));
}

void LocalCreateDiscoverInstallAndDelete() {
  TemporaryDirectory temporary;
  const auto app = temporary.Path() / "app-skills";
  const auto project = temporary.Path() / "workspace";
  EXPECT_EXPRESSION(std::filesystem::create_directories(app));
  EXPECT_EXPRESSION(std::filesystem::create_directories(project));
  const SkillRoots roots{.app = huxerui::File{app.string()},
                         .project = huxerui::File{project.string()}};
  HuxSkillFiles files{std::make_shared<FixedClock>()};

  const auto created = files.Create(roots, SkillLocation::app, "Local Skill",
                                    "Description", "# Local Skill\nBody");
  EXPECT_EXPRESSION(created);
  EXPECT_EXPRESSION(created->name == "Local Skill");
  EXPECT_EXPRESSION(created->location == SkillLocation::app);
  EXPECT_EXPRESSION(huxerui::File{created->skill_markdown_path}.IsFile());

  const auto project_skill = files.Install(SkillInstallRequest{
      .roots = roots,
      .location = SkillLocation::project,
      .name = "Imported Markdown",
      .package = SkillMarkdownPackage{
          .markdown = "---\nname: imported\ndescription: installed\n---\n"},
  });
  EXPECT_EXPRESSION(project_skill);
  EXPECT_EXPRESSION(project_skill->location == SkillLocation::project);

  const auto discovered = files.Discover(roots);
  EXPECT_EXPRESSION(discovered && discovered->size() == 4);
  EXPECT_EXPRESSION(std::ranges::any_of(*discovered, [](const auto &skill) {
    return skill.name == "imported";
  }));
  EXPECT_EXPRESSION(std::ranges::any_of(*discovered, [](const auto &skill) {
    return skill.name == "Local Skill";
  }));
  EXPECT_EXPRESSION(std::ranges::any_of(*discovered, [](const auto &skill) {
    return skill.name == "skill-creator";
  }));

  const auto creator = huxerui::File{app.string()}.Resolve(
      "skill-creator/SKILL.md");
  EXPECT_EXPRESSION(creator.WriteString("legacy tool: skill_create\n"));
  const auto resynchronized = files.Discover(roots);
  const auto creator_text = creator.ReadString();
  EXPECT_EXPRESSION(resynchronized && creator_text.Succeeded());
  EXPECT_EXPRESSION(creator_text.Value().contains("# Skill Creator"));
  EXPECT_EXPRESSION(!creator_text.Value().contains("skill_create"));

  EXPECT_EXPRESSION(files.Delete(roots, *project_skill));
  EXPECT_EXPRESSION(!huxerui::File{project_skill->root_path}.Exists());

  auto outside = *created;
  outside.root_path = temporary.Path().string();
  EXPECT_EXPRESSION(!files.Delete(roots, outside));

  auto already_missing = *created;
  EXPECT_EXPRESSION(huxerui::File{already_missing.root_path}.DeleteRecursively());
  EXPECT_EXPRESSION(files.Delete(roots, already_missing));
}

void DirectoryAndZipPackagesAreInstalledWithRollback() {
  TemporaryDirectory temporary;
  const auto app = temporary.Path() / "app-skills";
  const auto source = temporary.Path() / "source";
  EXPECT_EXPRESSION(std::filesystem::create_directories(app));
  EXPECT_EXPRESSION(std::filesystem::create_directories(source / "references"));
  EXPECT_EXPRESSION(huxerui::File{(source / "SKILL.md").string()}.WriteString(
      "# Directory Skill\nDirectory description\n"));
  EXPECT_EXPRESSION(huxerui::File{(source / "references" / "guide.md").string()}
             .WriteString("guide"));
  const SkillRoots roots{.app = huxerui::File{app.string()},
                         .project = std::nullopt};
  HuxSkillFiles files{std::make_shared<FixedClock>()};

  const auto copied = files.Install(SkillInstallRequest{
      .roots = roots,
      .location = SkillLocation::app,
      .name = "directory",
      .package = SkillDirectoryPackage{huxerui::File{source.string()}},
  });
  EXPECT_EXPRESSION(copied && copied->name == "Directory Skill");
  EXPECT_EXPRESSION(huxerui::File{copied->root_path}.Resolve("references/guide.md").IsFile());

  std::error_code symlink_error;
  const auto source_link = temporary.Path() / "source-link";
  std::filesystem::create_directory_symlink(source, source_link,
                                            symlink_error);
  if (!symlink_error) {
    const auto linked = files.Install(SkillInstallRequest{
        .roots = roots,
        .location = SkillLocation::app,
        .name = "linked-directory",
        .package =
            SkillDirectoryPackage{huxerui::File{source_link.string()}},
    });
    EXPECT_EXPRESSION(!linked);
  }

  const auto archive = GitHubStyleZip();
  const auto decoded = linecode::infrastructure::ReadLineCodeZip(archive);
  EXPECT_EXPRESSION(decoded && decoded->size() == 2);
  const auto installed_zip = files.Install(SkillInstallRequest{
      .roots = roots,
      .location = SkillLocation::app,
      .name = "repo",
      .package = SkillZipPackage{.archive = archive,
                                 .strip_common_root = true},
  });
  EXPECT_EXPRESSION(installed_zip && installed_zip->name == "ZIP Skill");
  EXPECT_EXPRESSION(huxerui::File{installed_zip->root_path}.Resolve("scripts/run.py").IsFile());

  const auto before = files.Discover(roots);
  EXPECT_EXPRESSION(before);
  const auto invalid = files.Install(SkillInstallRequest{
      .roots = roots,
      .location = SkillLocation::app,
      .name = "invalid",
      .package = SkillMarkdownPackage{.markdown = "not empty but no metadata"},
  });
  EXPECT_EXPRESSION(invalid); // A standalone SKILL.md is itself a valid package.
  const auto bad_zip = files.Install(SkillInstallRequest{
      .roots = roots,
      .location = SkillLocation::app,
      .name = "bad-zip",
      .package = SkillZipPackage{.archive = Text("not-a-zip")},
  });
  EXPECT_EXPRESSION(!bad_zip);
  EXPECT_EXPRESSION(!huxerui::File{app.string()}.Child("bad-zip").Exists());
}

void SkillHubRequestsValidateAndEncodeInputs() {
  using namespace linecode::infrastructure;
  auto list = BuildSkillHubListRequest({.page = 0,
                                        .page_size = 100,
                                        .keyword = "PDF 工具",
                                        .category = "office",
                                        .source = "all",
                                        .sort_by = "stars",
                                        .order = "desc"});
  EXPECT_EXPRESSION(list.url.contains("page=1&pageSize=50"));
  EXPECT_EXPRESSION(list.url.contains("keyword=PDF%20%E5%B7%A5%E5%85%B7"));
  EXPECT_EXPRESSION(!list.url.contains("source="));

  const auto file = BuildSkillHubFileRequest(
      "pdf-helper", "1.2.0", "references/api.md", "community");
  EXPECT_EXPRESSION(file);
  EXPECT_EXPRESSION(file->url.ends_with(
      "version=1.2.0&path=references%2Fapi.md&namespace=community"));
  EXPECT_EXPRESSION(!BuildSkillHubFileRequest("../escape", "1", "SKILL.md"));
  EXPECT_EXPRESSION(!BuildSkillHubFileRequest("safe", "1", "../SKILL.md"));
  EXPECT_EXPRESSION(ValidateSkillHubCookie("sid=value"));
  EXPECT_EXPRESSION(!ValidateSkillHubCookie("sid=value\r\nX-Test: injected"));
  const auto session_request = BuildSkillHubSessionRequest(
      SkillHubMethod::post, "/api/v1/auth/logout", "sid=value", "{}");
  EXPECT_EXPRESSION(session_request);
  EXPECT_EXPRESSION(session_request->method == SkillHubMethod::post);
  EXPECT_EXPRESSION(std::ranges::find(session_request->headers,
                           std::pair<std::string, std::string>{"Cookie",
                                                               "sid=value"}) !=
         session_request->headers.end());
  EXPECT_EXPRESSION(!BuildSkillHubSessionRequest(
      SkillHubMethod::get, "/api/v1/auth/me", "sid=x\nInjected: yes"));
  EXPECT_EXPRESSION(!BuildSkillHubSessionRequest(SkillHubMethod::get, "/outside", ""));
  EXPECT_EXPRESSION(!BuildSkillHubSessionRequest(SkillHubMethod::post,
                                      "/api/v1/comments", "",
                                      std::string(16U * 1024U + 1U, 'x')));
  EXPECT_EXPRESSION(ValidateSkillHubIconUrl("https://api.skillhub.cn/icon.png"));
  EXPECT_EXPRESSION(!ValidateSkillHubIconUrl("https://skillhub.cn.evil.test/icon.png"));
}

void SkillHubModelsDecodeLegacyShapes() {
  using namespace linecode::infrastructure;
  const auto page = DecodeSkillHubPage(R"json({
    "code":0,"data":{"total":1,"skills":[{
      "slug":"pdf-helper","name":"PDF Helper","description":"English",
      "description_zh":"中文说明","ownerName":"author","category":"office",
      "source":"community","version":"1.2.0","iconUrl":"https://skillhub.cn/i.png",
      "downloads":42,"stars":7,"updated_at":99,"verified":true,
      "labels":{"requires_api_key":"TRUE"},
      "subCategories":[{"key":"pdf","name":"PDF 工具"}]
    }]}
  })json");
  EXPECT_EXPRESSION(page && page->total == 1 && page->skills.size() == 1);
  EXPECT_EXPRESSION(page->skills[0].description == "中文说明");
  EXPECT_EXPRESSION(page->skills[0].requires_api_key);
  EXPECT_EXPRESSION(page->skills[0].subcategories ==
         std::vector<std::string>{"PDF 工具"});

  const auto comments = DecodeSkillHubComments(R"json({"items":[{
    "id":1,"authorName":"作者","content":"评论","createdAt":10,
    "likeCount":3,"replies":{"total":1,"preview":[
      {"id":2,"authorName":"回复者","content":"回复","createdAt":20}
    ]}
  }]})json");
  EXPECT_EXPRESSION(comments && comments->size() == 1);
  EXPECT_EXPRESSION(comments->front().reply_count == 1);
  EXPECT_EXPRESSION(comments->front().replies.front().content == "回复");

  const auto session = DecodeSkillHubSession(
      R"json({"user":{"displayName":"测试用户","handle":"tester","avatarUrl":"avatar"}})json");
  EXPECT_EXPRESSION(session && session->authenticated);
  EXPECT_EXPRESSION(session->account.handle == "tester");

  const auto detail = DecodeSkillHubDetail(R"json({
    "skill":{"slug":"pdf-helper","displayName":"PDF Helper",
      "summary":"English","summary_zh":"中文详情","category":"office",
      "source":"community","iconUrl":"icon","updatedAt":123,
      "isAuthorVerified":true,"stats":{"downloads":9,"stars":4},
      "labels":{"requires_api_key":"TrUe"},"tags":[" pdf ","tool"],
      "subCategories":[{"name":"文档"}]},
    "latestVersion":{"version":"2.0.0"},
    "namespace":{"canonicalName":"@community/pdf-helper","handle":"community"},
    "owner":{"displayName":"作者","handle":"fallback"},
    "publisher":{"name":"发布者"},
    "securityReports":{"keen":{"status":"BENIGN","statusText":"安全"},
      "sanbu":{"status":"suspicious","statusText":"需审查"}}
  })json");
  EXPECT_EXPRESSION(detail && detail->slug == "pdf-helper");
  EXPECT_EXPRESSION(detail->name == "PDF Helper" && detail->description == "中文详情");
  EXPECT_EXPRESSION(detail->version == "2.0.0" && detail->downloads == 9);
  EXPECT_EXPRESSION(detail->verified && detail->requires_api_key);
  EXPECT_EXPRESSION(detail->canonical_name == "@community/pdf-helper");
  EXPECT_EXPRESSION(detail->namespace_handle == "community");
  EXPECT_EXPRESSION(detail->security_status == "suspicious");
  EXPECT_EXPRESSION(detail->tags == std::vector<std::string>({"pdf", "tool"}));

  const auto fallback_detail = DecodeSkillHubDetail(
      R"json({"skill":{"displayName":"No Slug"}})json", "known-slug");
  EXPECT_EXPRESSION(fallback_detail && fallback_detail->slug == "known-slug");

  const auto files = DecodeSkillHubFiles(
      R"json({"files":[{"path":"SKILL.md","sha256":"abc","size":17},
      {"path":"scripts/run.py","sha256":"def","size":42}]})json");
  EXPECT_EXPRESSION(files && files->size() == 2);
  EXPECT_EXPRESSION(files->front().path == "SKILL.md" && files->back().size == 42);

  const auto versions = DecodeSkillHubVersions(R"json({"versions":[{
    "version":"2.0.0","changelog":"更新","createdAt":55,
    "securityReports":{"keen":{"status":"benign","statusText":"安全"}}
  }]})json");
  EXPECT_EXPRESSION(versions && versions->size() == 1);
  EXPECT_EXPRESSION(versions->front().security_status == "benign");

  const auto evaluation = DecodeSkillHubEvaluation(R"json({
    "userSummary":"总体很好","dimensions":{
      "effectiveness":{"score":8,"userReason":" 有效 ","suggestion":""},
      "trust":{"score":6,"userReason":"","suggestion":" 加测试 "}
    }
  })json");
  EXPECT_EXPRESSION(evaluation && evaluation->status == "completed");
  EXPECT_EXPRESSION(evaluation->score == 7.0 && evaluation->summary == "总体很好");
  EXPECT_EXPRESSION(evaluation->highlights == std::vector<std::string>{"有效"});
  EXPECT_EXPRESSION(evaluation->suggestions == std::vector<std::string>{"加测试"});

  const auto test_cases = DecodeSkillHubTestCases(R"json({"testcases":[
    {"question":"如何用？","answer":"这样用。"},
    {"question":"安全吗？","answer":"先审计。"}
  ]})json");
  EXPECT_EXPRESSION(test_cases && test_cases->size() == 2);
  EXPECT_EXPRESSION(test_cases->front().title == "测试用例 1");
  EXPECT_EXPRESSION(test_cases->back().expected == "先审计。");

  const auto api_error = DecodeSkillHubPage(
      R"json({"code":-1,"message":"invalid request"})json");
  EXPECT_EXPRESSION(!api_error && api_error.error().message.contains("invalid request"));
}

void GitHubSourcesAreResolvedWithoutUnboundedRetry() {
  using namespace linecode::infrastructure;
  const auto root =
      ResolveGitHubSkillSource("https://github.com/acme/demo-skill");
  EXPECT_EXPRESSION(root && !root->markdown);
  EXPECT_EXPRESSION(root->primary_url ==
         "https://codeload.github.com/acme/demo-skill/zip/refs/heads/main");
  EXPECT_EXPRESSION(root->fallback_url ==
         "https://codeload.github.com/acme/demo-skill/zip/refs/heads/master");

  const auto blob = ResolveGitHubSkillSource(
      "https://github.com/acme/demo/blob/main/skills/foo/SKILL.md");
  EXPECT_EXPRESSION(blob && blob->markdown);
  EXPECT_EXPRESSION(blob->primary_url ==
         "https://raw.githubusercontent.com/acme/demo/main/skills/foo/SKILL.md");

  EXPECT_EXPRESSION(!ResolveGitHubSkillSource("https://example.com/skill"));
  EXPECT_EXPRESSION(!ResolveGitHubSkillSource("http://github.com/acme/demo"));
}

class ProbeSkillFiles final : public linecode::application::SkillFiles {
public:
  linecode::application::SkillResult<std::vector<linecode::domain::SkillRecord>>
  Discover(const SkillRoots &) const override {
    return std::vector{record};
  }
  linecode::application::SkillResult<linecode::domain::SkillRecord>
  Create(const SkillRoots &, SkillLocation, std::string, std::string,
         std::string) const override {
    return record;
  }
  linecode::application::SkillResult<linecode::domain::SkillRecord>
  Install(SkillInstallRequest) const override {
    return record;
  }
  linecode::application::SkillResult<void>
  Delete(const SkillRoots &, const linecode::domain::SkillRecord &skill) const override {
    deleted_id = skill.id;
    return {};
  }
  linecode::application::SkillResult<std::string>
  ReadPrompt(const linecode::domain::SkillRecord &,
             std::size_t maximum_characters) const override {
    auto value = std::string{"# Test\nPrompt body"};
    value.resize(std::min(value.size(), maximum_characters));
    return value;
  }

  linecode::domain::SkillRecord record{
      .id = "app:/skills/test/SKILL.md",
      .name = "Test",
      .description = "Description",
      .root_path = "/skills/test",
      .skill_markdown_path = "/skills/test/SKILL.md",
      .location = SkillLocation::app,
      .enabled = true,
      .discovered_at = 1,
      .updated_at = 2,
  };
  mutable std::string deleted_id;
};

class ProbeSkillRecords final : public linecode::application::SkillRecordStore {
public:
  huxerui::Task<linecode::application::SkillResult<
      std::vector<linecode::domain::SkillRecord>>>
  List() override {
    co_return records;
  }
  huxerui::Task<linecode::application::SkillResult<void>>
  UpsertDiscovered(std::vector<linecode::domain::SkillRecord> values) override {
    for (auto &value : values) {
      const auto existing = std::ranges::find(records, value.id,
                                              &linecode::domain::SkillRecord::id);
      if (existing != records.end())
        value.enabled = existing->enabled;
      if (existing == records.end())
        records.push_back(std::move(value));
      else
        *existing = std::move(value);
    }
    co_return linecode::application::SkillResult<void>{};
  }
  huxerui::Task<linecode::application::SkillResult<void>>
  SetEnabled(std::string id, bool enabled) override {
    const auto value = std::ranges::find(records, id,
                                         &linecode::domain::SkillRecord::id);
    if (value != records.end())
      value->enabled = enabled;
    co_return linecode::application::SkillResult<void>{};
  }
  huxerui::Task<linecode::application::SkillResult<void>>
  Delete(std::vector<std::string> ids) override {
    std::erase_if(records, [&](const auto &record) {
      return std::ranges::find(ids, record.id) != ids.end();
    });
    co_return linecode::application::SkillResult<void>{};
  }

  std::vector<linecode::domain::SkillRecord> records;
};

struct RepositoryScenario final {
  std::shared_ptr<ProbeSkillFiles> files;
  std::shared_ptr<ProbeSkillRecords> records;
  std::shared_ptr<linecode::application::SkillRepository> repository;
  bool done{};
  bool passed{};
};

std::shared_ptr<RepositoryScenario> repository_scenario;

huxerui::View RepositoryProbe() {
  const auto scenario = repository_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      scenario->records->records.push_back(
          {.id = scenario->files->record.id,
           .name = "Old",
           .description = {},
           .root_path = scenario->files->record.root_path,
           .skill_markdown_path = scenario->files->record.skill_markdown_path,
           .location = SkillLocation::app,
           .enabled = false});
      const SkillRoots roots{.app = huxerui::File{"/tmp/skills"},
                             .project = std::nullopt};
      const auto listed = co_await scenario->repository->List(roots);
      scenario->passed = listed && listed->size() == 1 &&
                         !listed->front().enabled &&
                         listed->front().name == "Test";
      const auto enabled = co_await scenario->repository->SetEnabled(
          scenario->files->record.id, true);
      scenario->passed = scenario->passed && enabled.has_value();
      const auto prompt =
          co_await scenario->repository->BuildExtensionPrompt();
      scenario->passed = scenario->passed && prompt &&
                         prompt->contains("#### Skill: Test") &&
                         prompt->contains("# Test\nPrompt body");
      const auto deleted = co_await scenario->repository->Delete(
          roots, scenario->files->record.id);
      scenario->passed = scenario->passed && deleted.has_value() &&
                         scenario->records->records.empty() &&
                         scenario->files->deleted_id ==
                             scenario->files->record.id;
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("skill-repository-probe");
}

void RepositoryPreservesEnabledStateAndCoordinatesDeletion() {
  repository_scenario = std::make_shared<RepositoryScenario>();
  repository_scenario->files = std::make_shared<ProbeSkillFiles>();
  repository_scenario->records = std::make_shared<ProbeSkillRecords>();
  repository_scenario->repository =
      std::make_shared<linecode::application::SkillRepository>(
          repository_scenario->files, repository_scenario->records);
  const huxerui::Application app(RepositoryProbe,
                                 {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(app);
  ui.PumpUntil([] { return repository_scenario->done; });
  EXPECT_EXPRESSION(repository_scenario->passed);
  repository_scenario.reset();
}

} // namespace

TEST(skill_services_tests, LegacySuite) {
  DomainMetadataAndMarkdownMatchLegacy();
  LocalCreateDiscoverInstallAndDelete();
  DirectoryAndZipPackagesAreInstalledWithRollback();
  SkillHubRequestsValidateAndEncodeInputs();
  SkillHubModelsDecodeLegacyShapes();
  GitHubSourcesAreResolvedWithoutUnboundedRetry();
  RepositoryPreservesEnabledStateAndCoordinatesDeletion();
  std::cout << "skill service tests passed\n";
}
