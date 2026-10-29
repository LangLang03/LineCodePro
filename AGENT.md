# AGENT.md

Guidance for working in this repository.

## Project

LineCode Pro (`cn.lineai`) is an Android AI coding workspace: a streaming chat with a tool-call
loop over a user-selected project tree, plus SSH/Termux, MCP, Skills, image tools and phone control.
Pure Java 11, hand-written Views (no Kotlin/Compose/XML layouts), hand-written DI, AGP 9.2.0,
`compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`.

The legacy Java tree was removed in the rewrite; only `:ipc` and `:terminal-provider` remain from
the original repository. All code now lives under `modules/` (plus the thin `:app` shell).

## Module layout

- `:core-model` — pure domain models (`Message`, `ModelConfig`, `ThemePalette`, `Project`, `Skill`, ...)
- `:core-kernel` — `Graph` DI, `Scheduler`, `Clock`, `Observable`, `Scope`, errors, `Ids`
- `:core-mvi` — `Store/Presenter`, `ScreenId/ScreenKey/BackStack/ScreenRegistry`
- `:core-service` — cross-feature interfaces
- `:engine-protocol` — wire DTOs, SSE parser, OpenAI/Anthropic/Codex codecs, `ToolCallMerger`
- `:engine-prompt` — `assets/prompts/**` single source of prompt templates + strict renderer
- `:engine-ai` — `HttpModelClient`, probes, MCP/SkillHub/vision/image clients
- `:engine-local` — llama.cpp JNI backend (`NativeLlamaEngine`, arm64)
- `:engine-agent` — `ToolLoopEngine` state machine (`ToolLoopState/Decider/NextAction`)
- `:tool-api` — `Tool`, `ToolContext`, `ToolResult`, `ToolSchemaBuilder`, `PathPolicy`, `DiffSink`
- `:tool-builtin` — workspace file tools, shell, web search, TODO
- `:tool-mcp` / `:tool-agent` / `:tool-media` / `:tool-phone` — tool families
- `:data` — Store interfaces + SQLite implementations, migrations, archive
- `:ui-tokens` / `:ui-i18n` / `:ui-kit` / `:ui-markdown` — tokens, strings, Views, Markdown
- `:feature-chat/settings/models/extensions/workspace/terminal` — screens and controllers
- `:platform-android` — scheduler, link policy, accessibility service, keep-alive service
- `:runtime` — composition root (`AppGraph`), screen factories, dialog/archive helpers
- `:app` — `NextApp` + `MainActivity` shell only (≤1,500 Java lines, no page construction)
- `:arch-test` — mechanical architecture guards

## Data

`modules/data/.../LineCodeDatabase` owns schema **VERSION = 12**: conversations, messages,
message_blocks, message_tool_calls, settings, models, error_logs, memories, extension_agents,
extension_mcps, ipc_providers, projects, file_diffs. Stores are synchronous; callers use
`Scheduler.io`. `SettingsStore` is write-through with `invalidate()` after archive restore.

## Chat and tools

`ChatController` drives `ToolLoopEngine` (stream → tool batch → stream ...). Assistant tool calls
are persisted in `message_tool_calls`, TOOL results as messages (optionally with image blocks).
`ChatState` exposes pending approvals; `ChatScreenView` renders the approval panel. The composer attaches images and arbitrary files; FILE blocks persist their text and `Message.wireText()` injects them into model requests while user rows show file chips. Permission
modes (`auto`/`confirm`/`readonly`) and session modes (`chat`/`plan`/`agent`/`control`) both filter
the tool catalog. Enabled Agent/Skill extensions are injected into the prompt; enabled Agents are
also exposed as `agent_<slug>` tools (read-only nested loop). MCP tools come from `tools/list` and
always require approval. Context compaction summarizes older messages at 80% of the model context,
or 50% when soft compaction is enabled. Minimal mode uses a one-sentence role prompt and skips
extension, skill and learning sections; learning mode injects memories, recent conversation titles,
enabled skills and skill paths through `runtime.LearningContextProvider`. Thinking scroll/auto-expand
and process auto-expand preferences are applied by `MessageRow`/`ToolRow` without recreating views.

## UI rules

- All user-visible text uses `R.string` (en/zh/ru); no CJK literals in code.
- Views are hand-written; no XML layouts, Kotlin, Compose or new runtime dependencies.
- Tool presentation comes from `ToolPresentation`/`ToolIcon`; UI must not branch on tool names.
- `ThemeBinder` restyles in place; never `removeAllViews()` for theme changes.
- Files ≤600 lines, constructors ≤8 parameters; blocked by `linecode.verify`.

## Build and verification

- Debug: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew test :app:testDebugUnitTest`
- Guards: `./gradlew verifyAll :arch-test:test`
- Release: `./gradlew clean :app:assembleRelease` (slim) and
  `./gradlew clean :app:assembleRelease -PlinecodeEnableLocalModel=true` (local model); both
  are signed from `signing.properties` and publish as `-slim.APK` / `-local.APK`.
- Lint: `./gradlew :app:lintVitalRelease`

Architecture guards: `linecode.java`, `linecode.android`, `linecode.verify`, `linecode.app`
(build-logic); they enforce package ownership, dependency direction, file size, CJK-free code,
no raw threads, no UI string concatenation, stringly-typed state, en/zh/ru string key parity, and
prompt-catalog consistency (manifest ↔ `PromptId` ↔ placeholders, no duplicate or orphan assets).

## Agent environment

- When a built-in file tool is available (`read` / `edit` / `write` / `glob` / `grep`), use it to
  change files. Do not patch source files through `bash`, `python`, `sed` or heredocs; the shell is
  for builds, tests and repository commands only.

## Versioning and changelog

- `releaseVersionName` in `app/build.gradle.kts` is the public version (e.g. `2.0.0`, no suffix);
  `versionCode` increases by one for every release. Bump both in the same commit as the changelog.
- `update.md` is the single changelog, newest version first. Every version section must be
  bilingual: an English part first, then a Chinese part, describing the same changes.

## Do not / 禁止事项

- Do not resurrect `MainCoordinator`, `ScreenFactories`, `cn.lineai.mvp`, or the legacy
  `cn.lineai.ui.component` / `cn.lineai.data.repository` / `cn.lineai.ui.theme` packages.
- Do not add feature→feature dependencies, Android imports in core/engine, split packages, or a
  module without adding it to `settings.gradle.kts`, `Modules.java` and a package root.
- Do not add new branches to `PageFactory`, `SettingsPageFactory` or `FeaturePageFactory` for every
  screen; screen ownership and page construction belong to the feature that owns the screen.
- Do not branch on tool names, tool families or domain names in UI code. Add metadata through
  `ToolPresentation` / `ToolIcon` and let `ToolRegistry.presentation(...)` resolve it.
- Do not hand-concatenate JSON, tool schemas or prompts. Use `ToolSchemaBuilder`, the prompt assets
  in `engine-prompt`, `PromptRenderer` and the manifest/PromptId guards.
- Do not create a second Skill/SkillHub/GitHub install path. Reuse `SkillStore.importSkill` and
  `RemoteSkillInstall`.
- Do not add `new Thread`, `Executors`, `Handler`, locks/latches, `Thread.sleep` or `while(true)` in
  feature/core/engine code. `platform-android` owns the scheduler; `NestedAgentRunner` is the single
  documented synchronous-agent exception.
- Do not add static mutable fields, `rawJson`/`raw_json`, stringly-typed state, empty catches,
  `getMessage()` in user-visible text, CJK literals or UI text concatenation.
- Do not build views with XML layouts, Kotlin, Compose or a new UI framework. Views are written in
  Java 11; user-visible text lives in `R.string` for en/zh/ru with identical key sets.
- Do not call `removeAllViews()` from `applyTokens`/theme callbacks. Restyle existing views in place.
- Do not exceed 600 lines per file or 8 constructor parameters. Keep methods and field counts small;
  the 600-line limit is a hard failure, not a target.
- Do not add a prompt asset without a `manifest.json` entry, a `PromptId` entry and no orphan
  assets. Do not reintroduce prompt fallback literals in Java or `strings.xml`.
- Do not put local-model code in the default APK. The slim build must contain no
  `engine-local`, `LocalModelClient`, `LocalModelPage`, `LocalModelBridgeImpl` or llama/ggml `.so`.
  Gate local sources with `linecodeEnableLocalModel`; never add them to `src/main`.
- Do not build slim and local variants over the same incremental output without `clean`. Release
  artifacts are named `LineCode Pro <version>-slim.APK` and `-local.APK`; neither may overwrite
  the other.
- Do not extend the schema without a migration and tests, and never silently open a new database
  file while abandoning existing user history.
- Do not bypass `PathPolicy` except through the explicit user switch, and never log or archive
  credentials, API keys, signing material or private source.
- Do not commit `.workbuddy/`, `signing.properties`, build outputs, `local.properties`, generated
  archives or GGUF model files. They are intentionally ignored. The vendored llama.cpp headers and
  arm64 runtime in `third_party/llama-cpp` are a deliberate exception so CI can build the local
  model APK without a sibling checkout.
- Do not add CI on both pull requests and master merges. PRs are checked once; a master merge is
  handled by Release. Keep the CI trigger limited to pull requests.

## Notes

- Android ICU `Pattern` rejects an unescaped `}}`; prompt placeholders must be `\}\}`.
- `org.json` is `compileOnly` in JVM modules; Android provides it at runtime (never bundle the Maven jar).
- SkillHub integration covers store search/install, detail with SKILL.md and comments, WebView login
  and publishing through the official API.
- Local GGUF models require the arm64 native build (`linecodeEnableLocalModel=true` and the
  vendored `third_party/llama-cpp` headers/prebuilt runtime).
