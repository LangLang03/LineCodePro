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

Status values are `started`, `pending`, and `excluded`.

| Surface | Status | Required behavior |
| --- | --- | --- |
| Main chat shell | started | Header, 792dp content, message flow, composer, safe areas |
| Conversation/file drawer | started | Tabs, history CRUD, file tree, refresh, file actions |
| Empty chat | started | Exact copy plus add-model/open-workspace actions |
| Composer | started | Full editing value, attachments, image, quote, queued sends, slash menu, model/mode menus |
| Settings home | started | Exact grouped rows; platform filtering before construction |
| Phone Control / Control mode | excluded | Complete removal across UI, tools, services, persistence and prompts |
| Models and model editors | pending | Providers, protocols, catalog query, GGUF, acceleration, compression, validation |
| LLM / prompt / input settings | pending | Reasoning, tone, templates, compaction and Enter behavior |
| MCP / tool / SSH settings | pending | Targets, permissions, web/image tools, SSH testing |
| Termux / Terminal Provider | pending | Android-only; absent from Windows navigation |
| Output / security / theme | pending | Preview, browser policy, path warning, nine palette modes and custom editor |
| Data / storage / memory / logs | pending | Import/export, redaction, stats, CRUD, diagnostics |
| Keep-alive | started | Android-only settings, persisted foreground service/Wake Lock and RAII generation lease; no Windows row |
| Extensions | pending | Agent, MCP, Skills, LineCode packages, install/edit/enable/delete flows |
| Skill Hub | pending | Search, sort, pagination, session, detail tabs, reviews, install and publish |
| Tutorial / about | pending | Async tutorial load and version links |
| Open-source licenses | started | Current dependency inventory and navigation; legacy list/content parity is excluded |
| Built-in browser | pending | JavaScript default-off and browser-history-first Back |
| Tool cards / approval / diff | pending | Dedicated renderers, streaming output, approval modes, persistent review/revert |
| Sharing | pending | Text, Markdown, PDF, clipboard/system share and multi-select export |

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
