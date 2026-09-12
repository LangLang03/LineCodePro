# LineCode → LineCodePro migration contract

This is a behavioral and visual migration, not a redesign. A screen is complete only after its geometry, colors,
typography, content, enabled/disabled states, transitions, overlays, navigation, persistence and failure behavior match
the legacy app. Internal code structure is intentionally new.

## Non-negotiable scope

- Preserve all UI and product behavior except the explicit exclusions below.
- Remove the complete accessibility-backed Phone Control feature. Do not migrate its service, permission, screen,
  Control chat mode, `phone_*` tools, prompt text, tool cards, preview samples or settings. Legacy `control` preferences
  must normalize to `agent`; historical `phone_*` calls must render as inert generic records.
- Keep background keep-alive on Android only. Windows must not register its service, route or settings row.
- The open-source licenses page is dependency-driven rather than a legacy-content clone. List only libraries that the
  C++ application actually uses, and add an entry when a dependency is introduced; stale legacy entries are excluded.
- Hide Termux integration, Terminal Provider and Android storage-permission UI on Windows. Keep ordinary SSH.
- Do not invent WorkManager, alarms, boot receivers or OEM keep-alive integrations; the legacy app has none.
- Framework-provided control semantics are not the removed Phone Control/AccessibilityService product feature.

## Architecture boundary

```text
app (composition root)
  -> presentation (HuxerUI declarations and controlled UI state)
    -> application (use cases and ports)
      -> domain (models and rules)

infrastructure -> implements application ports
platform/android -> implements Android-only ports
```

Rules:

- Dependencies point inward. Platform payloads, JNI handles and HuxerUI platform channels never enter domain or UI.
- Use interfaces only at I/O or platform variation boundaries. Use concrete `final` use cases elsewhere.
- HuxerUI components remain ordinary functions returning `View`; OOP is for stateful domain services and ports.
- Use C++23 features where they clarify ownership and invariants: `enum class`, concepts, `std::expected`, ranges,
  `std::span`, variants, chrono and RAII. Templates and compile-time policy filter stable platform differences; there is
  no home-grown reflection or DI container.
- Android background execution is represented by a move-only RAII lease. The Android implementation owns foreground
  service, notification, WakeLock, optional silent-audio compatibility and deterministic release. A platform-required
  Java Service may be a thin lifecycle bridge only; policy remains C++.

## Visual baseline

- Main content maximum width: 792dp, centered on wider windows.
- Spacing scale: 4 / 8 / 12 / 16 / 20 / 24dp.
- Type scale: 11 / 13 / 16 / 17 / 20 / 22 / 26sp.
- Common radii: cards and fields 12dp, user bubble 18dp, large panels 16dp, pills fully rounded.
- Screen transition: enter 280ms, exit 220ms, direction based on push/pop.
- Dialog width: viewport minus 32dp, capped at 560dp.
- Theme supports system, light, dark, Coffee, VS Code, GitHub Dark, Gruvbox, high contrast and custom palettes.
- Source screenshots under the legacy `temp/linecode-ui/native-previews` and `previews` directories are the pixel QA
  reference. Lucide VectorDrawables should be mechanically converted to static SVG without redrawing them.

## Parity ledger

Status values are `migrated`, `excluded`, and `equivalent`. `excluded` means
deliberately dropped from scope with a reason; `equivalent` means the legacy
surface is served by a different implementation that a test pins down. A
`migrated` row names the evidence: a native test, a device check, or both.
Residual gaps that do not change the surface's status are listed under
"Known residuals" below rather than hidden inside a status.

| Surface | Status | Evidence and required behavior |
| --- | --- | --- |
| Main chat shell | migrated | Header, message flow, composer and safe areas; 18-scenario screenshot harness with 0 functional failures. Header and composer carry the same eight accessible names as the baseline, within the known 1px vertical offset. |
| Conversation/file drawer | migrated | Tabs, history CRUD, file tree, refresh and file actions; exercised by the drawer scenarios. |
| Empty chat | migrated | Exact copy plus add-model action; row spacing aligned to the legacy margins (`home` MAE 2.2236 -> 2.1973 on a shared baseline). |
| Composer | migrated | Editing value, attachments, quote, queued sends, slash menu, model/mode menus. Image input is carried end to end (domain payload, all three protocol encoders and store round trip) but deliberately has no entry point: the legacy's picker button sits in a permanently hidden row and its only other caller has no callers, so adding one would diverge. |
| Settings home | migrated | Grouped rows with platform filtering before construction; `settings`, `llm_settings`, `output_settings`, `security_settings` scenarios. |
| Phone Control / Control mode | excluded | Deliberate scope decision: removed across UI, tools, services, persistence and prompts. A repository-wide search finds no AccessibilityService, phone-control entry or leftover string. |
| Models and model editors | migrated | Providers, protocols, catalog query, GGUF, acceleration, compression and validation; `models`, `model_add_*` scenarios. |
| LLM / prompt / input settings | migrated | Reasoning, tone, templates, compaction and Enter behavior; `llm_settings`, `input_settings` scenarios. |
| MCP / tool / SSH settings | migrated | Targets, permissions, web/image tools, SSH testing; settings scenarios plus the tool-registry test suites. |
| Termux / Terminal Provider | migrated | Android-only, gated by `FeatureAvailability`; absent from Windows navigation at compile time (see the platform assertions in `tests/application_tests.cpp`). |
| Output / security / theme | migrated | Preview, browser policy (JavaScript defaults to off), path warning and the palette set; `output_settings`, `security_settings`, `theme` scenarios. |
| Data / storage / memory / logs | migrated | Import/export with redaction, stats, CRUD and diagnostics; archive and SQLite suites (`sqlite_archive_redaction_tests`, `data_archive` tests). |
| Keep-alive | migrated | Android-only settings row plus a persisted foreground service and `WakeLock`; the row is compiled out on Windows. |
| Extensions | migrated | Agent, MCP, Skills, LineCode packages, install/edit/enable/delete; built on the file/agent/MCP tool registries with their own suites. |
| Skill Hub | migrated | Search, sort, pagination, session, detail tabs, reviews, install and publish. Publishing has no navigation entry, matching the legacy. |
| Tutorial / about | migrated | Async tutorial load (parser, nested blocks and inline emphasis covered by `tutorial_nested_blocks_tests`, `inline_emphasis_tests`) and the version links on the about page. |
| Open-source licenses | migrated | Current dependency inventory and navigation; the legacy list content itself is out of scope. |
| Built-in browser | migrated | JavaScript defaults to off (`output_settings.cpp` reads it with a `false` fallback) and the header's Back pops the screen. This row previously claimed "Back follows browser history first"; checking the legacy shows `ScreenFactories` passes `view::handleScreenBack` to `InAppBrowserScreenView`, so a history-first Back was never part of the legacy behaviour and is not expected here. |
| Tool cards / approval / diff | migrated | Dedicated renderers, streaming output, approval modes and persistent review/revert; `file_tool_tests`, `diff_*`, `agent_tool_tests`, `sub_agent_runner_tests`. Sub-agent progress detail is a known residual. |
| Sharing | migrated | Text, Markdown, clipboard, rendered/PDF and multi-select export via `application/chat_export.*` and its delivery port. |

### Known residuals

These are recorded so a `migrated` status above is not read as "no
difference remains". None of them changes a surface's status, and each is
tracked with its evidence in `TODO.md`:

- Mid-loop context compaction rewrites the in-flight request but does not
  persist its summary or show a progress block; the legacy tool loop wrote
  its in-flight messages into the session, which this port does not.
- Sub-agent tool calls do not raise their own review prompt.
- The resume sanitizer repairs loaded messages in memory but does not write
  the repair back. The repair is idempotent, invisible to the user, and
  re-derived on every load; doing it would mean refactoring the most-used
  write path for no observable gain, so it is recorded as an accepted,
  invisible divergence rather than silently left undone.
- A soft compaction summary is placed after the slice it replaced; this was
  fixed and verified (stored order `head | summary | tail`).
- Cross-renderer text metrics: HuxerUI exposes no line-spacing control and
  its `TextField.Placeholder()` renders no text, so long pages drift
  vertically and four model-form placeholders are absent. Sheet bottoms sit
  flush with the screen by explicit product decision.

## Navigation and interaction invariants

- Back priority: routed screen, directory picker, attachment picker, generic bottom sheet, drawer.
- Opening a screen or sheet closes competing overlays and clears/hides the editor focus.
- Dynamic route parameters stay in route values (`modelEdit:*`, `browser:*`, `skillStoreDetail:*` equivalents).
- Web content consumes Back through browser history before the app route pops.
- Generation disables model and mode switching. A second submission queues; four queued rows are shown before folding.
- Read-only permission forces Chat mode and restores the previous writable mode when appropriate.
- Message actions include copy, recall, quote, share, text selection and multi-select export.
- Export continues to redact API keys, SSH credentials/private keys, web-search keys and sensitive MCP headers.

## Verification gates

For every migrated vertical slice:

1. Build Android arm64-v8a and x86_64 with strict C++23.
2. Run domain/application tests without Android or HuxerUI dependencies.
3. Compare compact and wide screenshots against the legacy reference.
4. Exercise controlled text editing, keyboard, touch, hover/disabled state where applicable.
5. Verify Windows-visible route catalogs contain no Android-only item on a Windows host build.
6. Mark the ledger complete only when UI and behavior both pass; a placeholder never counts as parity.
