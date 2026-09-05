# Legacy UI specification — settings and detail screens (zone B)

> Canonical source: `/home/LangLang/AndroidStudioProjects/LineCode` at the working tree inspected on 2026-09-06. This document is a migration contract, not a redesign proposal. `dp`/`sp` below are Android density-independent units from the legacy implementation. “1 px” means one physical Android pixel, exactly as coded.

## 1. Scope and parity rules

This zone covers the settings hub, every settings subpage, model management, extension management, MCP/SSH integration, memory, storage, theme, logs, About/Licenses, their dialogs, route factories, and their direct shared theme/components. The HuxerUI implementation must reproduce the visible hierarchy, order, sizing, colors, state, click behavior, scrolling and conditional visibility below.

The only deliberate feature removals/platform splits are:

- **Remove Accessibility / Phone Control completely.** Do not port its page, route, service, permission, strings, settings entry, disclaimer, controller callbacks, or background coupling. The legacy `Advanced features` page becomes empty, so remove that page and its Settings row too. Evidence: `AdvancedFeaturesScreenView.java:30-43`, `SettingsScreenView.java:52-58`, `ScreenFactories.java:644-716`, `MainChatView.java:461-462`.
- **Keep Alive is Android-only.** Android keeps the row/page/functionality; Windows must not construct, register, navigate to, or leave spacing for it. Evidence: `SettingsScreenView.java:72-77`, `KeepAliveSettingsScreenView.java:29-99`, `ScreenFactories.java:580-642`.
- **Termux and Terminal Provider are Android-only.** They depend on Android packages, permissions, intents and service discovery. On Windows hide the MCP `Terminal provider` segment, Termux action, Extensions `terminalProvider` card, and their routes. SSH itself remains cross-platform. Evidence: `MCPSettingsScreenView.java:48-69,86-108`, `ExtensionsScreenView.java:39-44`, `TerminalProviderDetailScreenView.java:58-175`, `TermuxIntegrationScreenView.java:97-150`.
- Android file/URI, package metadata, clipboard, share and “open with” operations become platform-service calls behind typed interfaces. Windows uses native equivalents while retaining the same visible UI wherever the feature remains.

HuxerUI boundary: screens are ordinary C++23 functions returning `View`; page state is controlled and keyed, text editing uses `TextEditingValue`, lists use stable keys and `ScrollView`/`VirtualList`, viewport differences use `UseViewportClass`, and async operations are lifecycle-owned (`UseTaskScope`/worker execution). No detached threads or Android objects may cross the presentation/service boundary.

## 2. Shared visual contract

### 2.1 Runtime theme tokens

`LineTheme` declares bootstrap values at `ui-theme/.../LineTheme.java:14-42`, but its static initializer immediately applies `ThemePalette.forMode("dark")` (`LineTheme.java:59-97`). Therefore the **effective default dark palette**, not the bootstrap literals, is the canonical initial appearance:

| Token | Effective dark | Token | Effective dark |
|---|---:|---|---:|
| `BG` | `#171819` | `SURFACE` | `#242629` |
| `SURFACE_ELEVATED` | `#292D31` | `SURFACE_LIGHT` | `#34393F` |
| `ACCENT` | `#E5E9EE` | `ACCENT_DIM` | `#353A40` |
| `ACCENT_MUTED` | rgba(229,233,238,.08) | `ACCENT_MUTED_2` | rgba(229,233,238,.13) |
| `TEXT` | `#EDF0F2` | `TEXT_SECONDARY` | `#969DA5` |
| `TEXT_TERTIARY` | `#969DA5` | `TEXT_ON_COLOR` | `#24262A` |
| `BORDER` | `#383D42` | `BORDER_LIGHT` | `#30353A` |
| `INPUT_BG` | `#242629` | `USER_BUBBLE` | `#242629` |
| `AI_BUBBLE` | `#171819` | `CODE_BG` | `#1D1F21` |
| `CODE_BORDER` | `#383D42` | `DANGER` | `#D7A6AB` |
| `WARNING` | `#D8B986` | `SUCCESS` | `#ADCEB8` |

Evidence: `core-model/.../ThemePalette.java:273-282`. Light, Coffee, VS Code, GitHub Dark, Gruvbox and High Contrast palettes are exact arrays at `ThemePalette.java:285-355`; Custom starts from Light and overrides surface/input/bubbles/code at `ThemePalette.java:357-367`. HuxerUI should expose these as typed theme tokens and update all constructed views reactively.

Spacing constants are `XS=4`, `SM=8`, `MD=12`, `LG=16`, `XL=20`, `XXL=24dp`; typography is `11,13,16,17,20,22,26sp` (`LineTheme.java:44-57`). All theme-created text disables font padding, adds 2dp line spacing, and uses simple line breaking on Android Q+ (`LineTheme.java:158-177`). `textMedium` is `sans-serif-medium`; bold call sites use platform default bold. Rounded strokes are 1dp with a minimum of 1 physical px (`LineTheme.java:103-114`).

### 2.2 Shared scaffold and primitives

```text
ScreenScaffoldView (vertical, BG)
├─ ScreenHeaderView (wrap height)
└─ ScrollView (weight 1, fillViewport=false)
   └─ content Column (wrap height, default padding 0/0/0/100)
```

- Header: horizontal, center-vertical, BG, padding `16/12/16/12`; left and default right action wells `36×36dp`; back glyph `22dp`; title centered by symmetric wells, weight 1, bold `17sp`; bottom divider is 1 physical px `BORDER`. Text/custom right actions may wrap width but have minimum 36dp. `ScreenHeaderView.java:14-71`; `ScreenScaffoldView.java:9-54`.
- Settings section: uppercase `11sp` medium, `TEXT_TERTIARY`, letter spacing `.05`, header top 20/bottom 12 and horizontal 16; group has `SURFACE_ELEVATED`, radius 12, horizontal margins 16. Dividers are 1 physical px `BORDER_LIGHT`, optionally inset. `SectionHeaderView.java:7-17`; `SettingsSectionView.java:8-68`.
- `ActionRow`: horizontal center, min height 68, padding `16/12`; icon well `36×36`, radius 8, `ACCENT_MUTED` (danger variant uses danger-muted), glyph 20; label column margins 12 and weight 1; title medium 16; description 11 tertiary, top 2, +3dp line spacing; optional chevron well 20/glyph 17. `ActionRowView.java:12-60`.
- `SwitchRow`: horizontal center, padding 16; glyph 20 secondary; label column margin 12; title medium 16, description 11/top 2; entire row toggles controlled switch. Checked thumb/track `ACCENT`/`ACCENT_DIM`; unchecked `TEXT_TERTIARY`/`SURFACE_LIGHT`. `SwitchRowView.java:14-68`.
- `OptionRow`: horizontal center, min height 56, padding `16/12`; glyph 20; labels left 12, weight 1; title 16 and description 11/top 2. Active row uses `ACCENT_MUTED`, accent icon/title and medium font; inactive is transparent/secondary/regular. `OptionRowView.java:12-64`.
- Form field: vertical label medium 13 secondary; input top 4, 16sp, `SURFACE_LIGHT`, radius 8 + `BORDER_LIGHT`, horizontal 12/vertical 8, minimum 44dp (120 multiline); optional 11sp tertiary hint top 4. Secure input masks text. `FormTextFieldView.java:12-60`.
- Disclosure: pressable header min 52; label medium 14 secondary; chevron well 24/glyph 16 rotates 0→90°; body remains mounted and becomes `GONE` when collapsed. `DisclosureSectionView.java:10-39`.
- Bottom-sheet family: top corners 16; handle `36×4`, radius 2, top 8/bottom 4; title bold 17 with horizontal 16/bottom 12; 1px divider; rows horizontal padding 16 and vertical 14, label 16, optional description 11/top 2; bottom inset 12 or 34 depending caller. Examples: `ModelListScreenView.java:281-350`, `ExtensionDetailScreenView.java:555-630`.
- Center dialog width should reproduce source per page. Shared `DialogDimensions.insetDialogWidth` is `min(560dp, screenWidth-32dp)`. Some extension dialogs instead use the legacy bug `max(280dp, screenWidth-32dp)`; preserve only if pixel-parity testing requires it, otherwise classify as an approved defect fix. `DialogDimensions.java:20-27`; `AgentExtensionEditScreenView.java:235-238`; `ExtensionDetailScreenView.java:550-553`.
- Adaptive action rows change from horizontal to vertical if intrinsic child widths plus 16dp per child overflow; stacked actions become match-width/min-height 48 with 8dp gaps. `AdaptiveActionsView.java:8-33`.

## 3. Route and platform matrix

Factories are registered in order at `MainChatView.java:446-490`. Navigation is an explicit string stack (`ScreenNavigationController.java:22-103`). HuxerUI should replace stringly typed IDs with an enum/variant route and `NavigationStack`, while preserving these route relationships:

| Screen | Legacy route | Parent | Android | Windows |
|---|---|---|---|---|
| Settings | `settings` | chat | show | show |
| AI behavior / prompts | `llm`, `promptTemplates` | settings / llm | show | show |
| Input / tools / MCP / output / security / theme | `input`, `toolSettings`, `mcp`, `output`, `security`, `theme` | settings | show | show, with Android-only MCP controls hidden |
| Data / storage / memory / logs | `data`, `storage`, `memory`, `errorLogs` | settings | show | show; native filesystem dialogs/opening |
| Keep Alive | `keepAlive` | settings | show | **hide/unregistered** |
| Advanced / Phone Control | `advanced`, `phoneControl` | settings / advanced | **delete** | **delete** |
| SSH | `sshSettings` | MCP | show | show |
| Termux | `termuxIntegration` | MCP | show | **hide/unregistered** |
| About / Licenses | `about`, `licenses` | settings / about | show | show |
| Models | `models`, `imageUnderstandingModel`, `imageGenerationModel` | settings/tool settings | show | show |
| Model add/edit | `modelAddOptions`, `modelAdd`, `modelAdd:local`, `modelAdd:preset:*`, `modelEdit:*` | models/options | show | show; platform-specific local acceleration text |
| Extensions/detail | `extensions`, `extension:*`, `agentEdit[:id]`, `mcpEdit[:id]` | settings/extensions | show | show |
| Terminal provider | `terminalProvider` | extensions | show | **hide/unregistered** |

Evidence: factory IDs and callbacks in `ScreenFactories.java:145-342,344-642,644-824,853-1250`; fallback parent rules in `ScreenNavigationController.java:114-166`. The legacy parent map omits `security`, `errorLogs`, `advanced`, `phoneControl`, `imageGenerationModel` and tool-call preview; stack navigation normally masks this, but typed routing must define parents explicitly.

## 4. Settings hub and simple settings

### 4.1 Settings

```text
Column BG
├─ Header(Settings)
└─ Scroll(weight 1)
   └─ Column(bottom 100)
      ├─ bare Tutorial ActionRow
      ├─ AI & Models group: Models / AI behavior
      ├─ Tools & Execution: MCP / Tool settings / Extensions / Advanced†
      ├─ UI & Output: Input / Theme / Output
      ├─ Security: Security
      ├─ Data & System: Storage / Memory / Data / Error logs / Keep alive‡
      └─ Info: About
```

`†` delete Advanced; `‡` Android only. The Tutorial row is intentionally not wrapped in a card. Each grouped custom row is padding `16/12`, icon well `36×36` radius 18 with glyph 20, labels margin 12, title medium 16, description 11/top 2, chevron well 20/glyph 16; group is radius 12 and dividers start at x=68. Source/order/icons/click IDs: `SettingsScreenView.java:24-96`; group geometry: `SettingsScreenView.java:98-165`.

### 4.2 SimpleSettings fallback

Header + scroll + content padding `16/20/16/100`; optional subtitle is 13sp secondary, +3 line spacing, bottom 16; group radius 12 elevated; each row horizontal center padding `16/12`, medium 16 label and green `8×8` status dot radius 4; 1px divider inset 16. It is not registered by current factories; keep only as an internal fallback if still referenced. `SimpleSettingsScreenView.java:12-72`.

## 5. AI, input, output and security

### 5.1 AI behavior (`llm`)

Scaffold, default bottom padding 100. Sections and exact order:

1. Thinking depth: six `OptionRow(SPARKLES)` values Off, Auto, Low, Medium, High, Max; selected state is mutually exclusive; divider after all but Max.
2. Learning & memory: `SwitchRow(BRAIN)` Learning mode + divider; `SwitchRow(ROTATE_CCW)` Soft compaction.
3. Conversation tone: `OptionRow(ZAP)` Coding + divider; `OptionRow(SMILE)` Chat.
4. Prompts: clickable `OptionRow(FILE_PEN_LINE)` Custom prompts → `promptTemplates`.
5. Thinking display: switches `SCROLL_TEXT` Scroll to display, `EXPAND` Auto expand, `BRAIN` Keep full reasoning.

Rows immediately update callbacks and option active visuals; no save button. `LLMSettingsScreenView.java:23-180`; route wiring `ScreenFactories.java:167-249`.

### 5.2 Prompt templates

Scaffold. Intro `SettingsSection` contains 13sp secondary text padded 16 with 4dp line spacing; it lists every template description and `{{variable}}` names. Each template then gets its own section and one editor row (`PromptTemplatesScreenView.java:26-74`). Editor tree: vertical padding 16; description 13 secondary; metadata 11 tertiary top 8; monospace multiline input top 12, min height 220, code background/border radius 8, padding 12; actions top 12/height 34 with status (weight 1, 11sp; custom accent vs built-in tertiary), Reset and Save buttons. Buttons min width 72, surface-light/border radius 8, horizontal padding 8, icon `16×16`, label 11 left 5; Save separated by 8. Reset writes default and persists immediately; Save writes current value; both toast. `PromptTemplatesScreenView.java:76-179`.

### 5.3 Input

One `Input` section with a custom row. Row padding 16; left title medium 16 and description 11/top 2; right selector height 34, background surface-light radius 8 + border, padding `12/0/8/0`, left margin 12; selected label medium 13 and chevron well 18/glyph 13. Popup is width 104, height 82, outside-touch dismissible, aligned `x=selectorWidth-popupWidth`, y offset 4; input background radius 12 + border/padding 3; two options (`Send`, `Newline`) each height 38, radius 9, horizontal padding 12, medium 13, active white/accent and inactive secondary/transparent. It is controlled and calls back only when changed. `InputSettingsScreenView.java:28-135`.

### 5.4 Output & browser

- Conversation: switch `EXPAND` Auto-expand processing.
- Code display: switch `SCROLL_TEXT` Code auto-wrap; callback also updates the embedded Markdown preview live.
- Page opening method: two mutually exclusive `OptionRow`s, `GLOBE` built-in and `EXTERNAL_LINK` external, divider inset 52.
- Preview: vertical box padded 16 containing the Markdown resource; links are no-op in the preview.
- Tool-call preview: clickable `ActionRow(FILE_CODE)` opens `toolcall_preview`.

The listener exposes JavaScript state but this page renders no JS row; Security owns that control. `OutputSettingsScreenView.java:23-104`; factory callbacks `ScreenFactories.java:344-384`.

### 5.5 Security

Three single-switch sections: HTTP restrictions → `SHIELD_CHECK` allow arbitrary HTTP; Built-in browser → `CODE` JavaScript; Path protection → `SHIELD` bypass protection. Enabling bypass is intercepted: switch snaps back off, warning `LineAlertDialog` appears, Cancel remains off, Confirm sets on and persists; disabling is immediate. `SecuritySettingsScreenView.java:22-99`.

## 6. Tool and execution settings

### 6.1 Tool settings

Scaffold content padding `16/16/16/100`. Section label is medium 11 tertiary, letter spacing `.05`, top/bottom 8. Cards use elevated/radius 12/padding 16/bottom 12; title bold 16; description 11 tertiary/top 2/+3 line spacing (`ToolSettingsScreenView.java:36-57,316-375`).

- Image understanding card and image generation card: title + description + selected model label (13 bold, top 12; tertiary if none) + full-width primary action height 42/top 12. Primary button is accent/border accent radius 8, glyph `15×15`, label bold 11, gap 6. Each opens its read-only model picker route. `ToolSettingsScreenView.java:59-109`.
- Web search card: title/description; provider `GridLayout` with 3 columns and six equal weighted buttons (Bing RSS Free, Tavily, Brave, SerpAPI, Bing, Custom), each height 34, top/right margin 8, radius 8 + border; active accent/on-color, inactive surface-light/secondary. Below, six `FormTextFieldView`s at 12dp gaps: Base URL, API key (secure), model/source, query parameter, key header, key query parameter. Every keystroke persists. Selecting a provider loads its defaults and clears the API key. Bing RSS Free hides and disables all six fields; other providers show them. `ToolSettingsScreenView.java:111-264`.

### 6.2 MCP settings

Scaffold content padding `16/16/16/100`; each card elevated/radius 12/padding 16/bottom 12.

```text
Execution card
├─ bold 16 title
├─ segmented row (height 42, top 8, surface-light r8, inner padding 3)
│  ├─ Local (weight 1)
│  ├─ SSH (weight 1)
│  └─ Terminal provider (weight 1, Android only)
└─ contextual description 11 tertiary, top 8
[if SSH] SSH connection card
├─ title + description
└─ actions(top 12): SSH settings / Termux integration(Android only), equal width, height 42, gap 8
tool cards…
```

Segment label is bold 13; active accent/on-color, inactive transparent/secondary. SSH settings is primary accent; Termux secondary surface-light, both radius 8 + border, glyph 15/label 11/gap 6. Each visible tool config becomes a card with horizontal header: icon well `36×36` radius 18, glyph 18 and enabled accent/disabled tertiary; label column margins 12 with title medium 16 and description 11/top 2; tinted switch at right. Only configs satisfying `shouldShowForMode` render. `MCPSettingsScreenView.java:34-178,180-223`.

Windows contract: render Local and SSH only; when SSH is active render only the SSH settings action at full width. Do not expose the terminal-provider execution value in stored UI state.

### 6.3 SSH settings

Scaffold content padding `16/16/16/100`.

- Intro card elevated/radius 12/padding 16: bold 16 title, 11 tertiary description top 4, full-width Termux button height 42/top 12. Termux button is secondary surface-light/border radius 8, glyph 16 and bold 13. **Hide this button on Windows**, leaving title/description reflowed.
- Form card same surface/radius/padding: fields Host, Port (numeric), Username, Password (secure), Private key (multiline), Passphrase (secure), each next field top 12. Actions top 12: Save secondary + Test primary, equal width/height 42, gap 8. Status starts `GONE`; once set, monospace 11, top 8, code background/border radius 8, padding `12/8`; failures switch text/border to danger and background to danger-muted. Test disables itself and alpha becomes .65 while running. `SshSettingsScreenView.java:35-224`.

SSH network work must move from the detached legacy thread to a lifecycle task. The visible result/status semantics remain unchanged.

### 6.4 Termux integration — Android only

Scaffold content padding `16/16/16/100`; four elevated/radius-12/padding-16 cards, 12dp apart:

1. Use card: title bold 16 + 11sp tertiary description top 4.
2. Steps card: title + 3 rows top 8; number badge `24×24`, accent radius 12, bold 11 on-color; text left 8.
3. Intent card: selectable monospace 11 command, code background/border radius 8, padding 12, top 8.
4. Actions: two-column grid of four height-38 buttons with 8dp horizontal/vertical gaps—Copy command, Request permission, Open Termux, Auto SSH(primary). Status is initially gone, then monospace 11/code style padding `12/8`, top 8; error colors match SSH. Setup button disabled/alpha .65 while running.

Clipboard, runtime permission, package launch and 15-minute setup/test are Android services. `TermuxIntegrationScreenView.java:38-180,182-272`.

### 6.5 Terminal provider — Android only

Three sections: Scan (clickable search `ActionRow`); conditional Scan results after first scan; Installed. Empty rows are centered 13sp tertiary padded 16. Scan result row uses surface-elevated radius 8 + border, padding `12/8`; terminal well `32×32` radius 16/glyph 16; title medium16 + package 11; plus glyph 20. Clicking opens bottom-sheet add confirmation. Installed rows are switch rows and long-press opens delete sheet. `TerminalProviderDetailScreenView.java:40-149,151-279`.

## 7. Model management

### 7.1 Model list and image-model pickers

```text
Column BG
├─ replaceable HeaderHost
│  ├─ normal: back + title + Plus(management only)
│  └─ multi-select: Close + "Selected: N" + danger Trash
└─ Scroll(weight 1)
   └─ list Column(padding 16/16/16/100)
```

Empty state is 13sp tertiary (+3 line spacing), with different copy for management vs picker. Model card: horizontal center, padding 12, bottom 8, radius 12 + 1dp border. Background is accent-muted when batch checked, otherwise BG; border accent if current or checked, otherwise transparent. Provider badge uses bold 11 on-color, radius 8, padding `8/4`, right 12; protocol badge colors: Codex `#4B8BFF`, Anthropic `#B86F50`, local `#2E7D62`, OpenAI-compatible `#10A37F`. Name medium16 single-line; model ID 11 tertiary/top 2. Normal selected item ends with green `8×8` dot; batch mode ends with `22×22` check circle, checked accent with 14 glyph. Tap selects, long-press opens Modify/Multi-select sheet; in batch mode tap toggles selection, Trash opens destructive confirmation. Read-only image pickers omit Plus/long-press management. `ModelListScreenView.java:46-212,214-375`.

### 7.2 Add options

Scaffold content padding 16. Two large cards first: Custom and Local, min height 92, elevated/radius 12/border, padding16/bottom8; icon well `44×44`, radius8, glyph22; text margins12, bold16 + desc11/top4; chevron well19/glyph17. Provider section header top20/bottom8 contains Boxes glyph16 + bold13 label gap4. Each provider row is elevated/radius12/border, padding12/bottom8; initial badge `38×38`, accent-muted radius8, bold16; title bold16 + single-line subtitle 11/top3; chevron. `ModelAddOptionsScreenView.java:27-158`.

### 7.3 Add/edit model

Custom root/header/scroll; content padding16. Header title Add/Edit; right actions Test then Save, medium16, padding `12/8`, gap8. Test is hidden for local; Save starts tertiary/alpha .45 and becomes accent/1 when valid. `ModelAddScreenView.java:75-120,309-327,619-653`.

Remote form order and states:

1. Provider label then four equal toggles OpenAI/Codex/Anthropic/Local, height 46/radius12; locked preset/edit modes disable nonactive toggles at alpha .45. Provider changes clear fetched IDs and compression selection.
2. Name input.
3. Base URL input + 11sp hint top8; API key secure input.
4. Model ID header with Custom switch. Custom=true shows free text. Otherwise selector + Query: selector height48, surface-light/radius12/border, padding `16/12`, chevron16; query min width76/height48/gap8, search16 + bold16. Query becomes accent/on-color when URL+key are valid; loading changes label and disables click. Cached results reopen the picker.
5. Tool call limit numeric input + hint; context size input + hint.
6. Dedicated compaction subview (only protocols supporting it): Enabled switch; when enabled show hint, Auto switch, and custom-ID switch. If Auto is off, show the same selector/query pair or custom text. Disabled controls use alpha .45. Missing required manual ID blocks Save.

Evidence: `ModelAddScreenView.java:122-307,388-491`; compression tree/state `ModelCompressionSectionView.java:47-208,214-335`. Catalog picker is bottom sheet, max-height 420 scroll, rows padding `16/14`, 16sp; selected row check glyph16, final Custom ID row accent. Dialog width `min(560dp, viewport-32dp)`. `ModelPickerDialog.java:27-105`.

Local form: Name, model-file card min74 (surface-light/radius12/border/padding12; `38×38` icon well, title/desc and chevron), context length numeric default 4096 + hint, acceleration toggles Auto/CPU/NPU + hint. In the inspected legacy build file selection and local inference are explicitly pending; Save is permanently disabled and tapping the file card/to-save path only toasts. Preserve visible disabled/pending state only until the C++ local backend is implemented; then this is an approved functional improvement, not a UI redesign. `ModelAddScreenView.java:165-177,329-386,494-498`.

All catalog/compaction fetches must be cancellable HuxerUI tasks, replacing detached threads at `ModelAddScreenView.java:441-472` and `ModelCompressionSectionView.java:294-319`.

## 8. Theme settings

The page title is the themes section label. First section has nine `OptionRow`s: System, Light, Dark, Coffee, VS Code, GitHub Dark, Gruvbox, High Contrast, Custom, with icons Monitor/Sun/Moon/Coffee/Code/GitBranch/Code/Contrast/Paintbrush; single active row (`ThemeSettingsScreenView.java:35-45,156-172`).

Custom editor follows:

- Header horizontal padding16, top20/bottom8: uppercase section label weight1; reset circle `34×34`, radius17, glyph15; Save height34, left8, horizontal padding12, glyph15 + bold13 gap4. Save is accent/radius17 when all colors valid, otherwise surface-light/tertiary disabled. Reset restores custom default. `ThemeSettingsScreenView.java:174-225,480-499`.
- Starter panel: elevated/radius12/padding12, margins16/bottom12; 3-column grid. Each tile radius8/border/padding8, top/right margin8; selected uses accent-muted/accent border. It shows three overlapping `18×18` palette chips (−4 left overlap), icon14 top6, bold11 title top4. Presets are Default, Light, Dark, Coffee, VS Code, GitHub, Gruvbox, High Contrast, and Saved if custom colors exist. `ThemeSettingsScreenView.java:227-307`.
- Preview panel: padded12; bubble padded12/radius8; title bold16, text13/top4; accent pill bold11, padding `12/4`, radius999, top12. Background, bubble, text and accent update live. `ThemeSettingsScreenView.java:115-132,309-328`.
- Swatches panel: label medium13; 7-column grid of 32 predefined colors. Each swatch `34×34`, radius17, top/right margin8, active accent border and 14px check. `ThemeSettingsScreenView.java:69-78,134-142,330-358`.
- Editor group: elevated/radius12, margins16/top12. Nineteen rows for background, surfaces, input, text variants, accent, bubbles, borders, code, danger/warning/success. Row min66/padding `12/0`, active accent-muted; preview `30×30` radius15; label medium16 + desc11/top2; monospace hex input `92×38`, 13sp, radius8/border, horizontal padding8, max 9 chars. Invalid text/input/border become danger; divider 1px inset58. `ThemeSettingsScreenView.java:47-67,144-151,360-465`.

## 9. Data, storage, memory and logs

### 9.1 Data management

One `All data` section: clickable Export all `ActionRow(DOWNLOAD)` + divider, then Import `.linecode` `ActionRow(UPLOAD)`. The importer is destructive/overwrite per copy and must retain confirmation/validation in the controller layer. `DataSettingsScreenView.java:15-25`.

### 9.2 Storage management

Scaffold with right Refresh glyph (36dp action well, default glyph 18, 2dp rounded stroke); content padding16. Summary panel elevated/radius12/padding16/bottom12: medium11 tertiary label, total bold26/top4, summary 11 tertiary/top4. Four cards (database, chats, configs, workspace) are horizontal, elevated/radius12, padding12/bottom8; icon well `38×38` radius19/glyph19; center title bold16 and desc11/top2 with 12dp side margins; right size bold16 and count11/top2, end aligned. `StorageManagementScreenView.java:27-165`; refresh icon geometry `RefreshCwButtonView.java:10-78`.

Stats loading/refresh currently spawns raw threads; C++ must use lifecycle task + worker and suppress stale completion. Android and Windows use the same UI but platform filesystem/storage services.

### 9.3 Memory

Custom root with replaceable header and scroll; content bottom 100. Normal header has accent Plus; batch header has Close and danger Trash with title count. Current-project hint is 11sp tertiary, margins16/top12. Sections: Long-term (database icon), Project (folder), Environment (globe), Short-term working memory (clock), Chat index (book), each title suffixed by a count. Empty row is 13 tertiary padded16. Item rows reuse `ActionRow`: title is content preview capped at 80 chars, description is metadata/time; long/project/environment support long-press actions, while short-term/history are read-only details. `MemorySettingsScreenView.java:52-224`.

Long press opens Edit/Multi-select/Delete actions. Multi-select highlights rows accent-muted, changes header and allows batch deletion. Detail dialogs show full content and metadata. Editor dialog: elevated radius12/padding16, title bold17/bottom12; horizontal radio scope User/Project/Environment (13sp), multiline input height132/top12, input-bg/radius8/border/padding12, 16sp/min 5 lines; right-aligned Cancel/Save actions top16. Empty input toasts. All dialogs are scrollable and width `min(560dp, viewport-32dp)`. `MemorySettingsScreenView.java:226-458`; metadata formatting `MemorySettingsScreenView.java:461-519`.

### 9.4 Error logs

Scaffold header right Trash action colored danger (`36×36`, glyph20); click clears all, toasts, and rerenders. Empty body is centered 16sp tertiary padded `16/24`. Nonempty logs are a section of clickable `ActionRow(FILE_TEXT)` with chevrons and dividers inset68; clicking opens the text file via Android `FileProvider`/chooser. Windows uses native open-with while preserving the row/UI. `ErrorLogsScreenView.java:22-73`; factory platform action `ScreenFactories.java:553-579`.

## 10. Extensions

### 10.1 Extensions landing

Custom scaffold; content padding `16/16/16/100`. Five cards in order: Agent, MCP, Skills, LineCode LIP, Terminal Provider (Android only). Card is horizontal elevated radius12 + border, padding `16/12/12/12`, bottom8; icon well `44×44` radius12/glyph22; text margins12; title bold17 followed by pill badge bold11 accent, accent-muted radius999, padding `8/3`, gap8; description 13 tertiary/top4/+3 line spacing; chevron well20/glyph17. `ExtensionsScreenView.java:24-100`.

### 10.2 Extension detail

Scaffold title/icon/content derive from `ExtensionKindUiModel`; right Plus is `36×36`, radius18 accent-muted, glyph19. Content bottom100. It begins with an inline Add/Install `ActionRow`; Skills additionally shows SkillHub store and Share workspace sections. Installed section shows empty text or rows. Normal installed rows are `SwitchRow`; long-press on Agent/MCP gives Modify/Delete, on Skills enters multi-select. Skills batch mode replaces rows with `OptionRow`, adds a count/cancel/delete bar padded `16/8`, and destructive confirm. `ExtensionDetailScreenView.java:65-237`.

Skills Add bottom sheet actions: Install ZIP/document, Install GitHub, Create, Install path, and conditional Multi-select. File picker accepts `.zip` or `.md`, then target sheet Project/Global. Create dialog fields Name, Description, multiline Content + Project/Global radio and Create button. Path install fields Source path + optional Name + location; GitHub dialog URL + location. Action button is medium16 accent on accent-muted radius8, padding `16/12`. `ExtensionDetailScreenView.java:281-453`.

Delete/modify and confirmation sheets use the shared sheet metrics. Center form panels are elevated/radius16/padding16. Legacy form width uses `max(280dp, viewport-32dp)` (`ExtensionDetailScreenView.java:519-553`); see defect note in §2.2. Android `Environment`, document URI, FileProvider workspace sharing become native platform services; visible dialogs stay identical.

### 10.3 Agent add/edit

Scaffold with medium16 accent Save action. Sections:

1. Quick create: one `ActionRow(SPARKLES)` “Let AI write”.
2. Basic form group: elevated/radius12/padding16, margins16; Name then Identifier at 12dp gap.
3. Behavior group: Prompt multiline then Trigger multiline at 12dp gap.
4. Tools section title includes selected count; each available tool is selectable `OptionRow(SETTINGS)`.
5. MCP section likewise combines built-ins and enabled custom MCPs using `OptionRow(MCP)`.

New agents preselect File Read and Glob when available. Save requires name, normalized slug and prompt; identifier normalizes to max 48 lowercase ASCII letters/digits/underscore/dashes and prefixes `agent-` if needed. `AgentExtensionEditScreenView.java:55-189,280-413`.

AI-writer dialog: elevated/radius16/padding16; header title medium17 + 11sp description and Close `34×34` radius17/glyph17; multiline input min160/input-bg/radius8/border/padding12; Generate button top12, min44, accent/radius8/padding `16/12`, glyph18 + medium16. Busy hides icon/label, shows `22×22` progress, disables controls, button alpha .85 and close alpha .5. `AgentExtensionEditScreenView.java:191-278,326-340,427-465`. Replace raw thread with lifecycle task.

### 10.4 HTTP/S MCP add/edit

Scaffold with accent Save. Connection group uses Name and URL form fields. Headers section starts with Add header ActionRow then zero or more custom rows; header row padding `16/12`, Name and Value equal-width inputs height42/radius8/border, gap8, trash well `34×42`/glyph16. Query section is ActionRow Search, or busy row min68/padding `16/12`, alpha .85 with `34×34` progress and title/desc. Tools section title displays enabled count; before query shows centered pending state, while loading spinner state, after query one SwitchRow per tool. `McpExtensionEditScreenView.java:47-213,313-360`.

Query requires `http://` or `https://`; Save requires name, valid URL, a completed nonempty tool query, and re-query if URL changed. Tool switches update local queried state. `McpExtensionEditScreenView.java:91-167,216-310`. Query must become cancellable/lifecycle-owned.

## 11. Keep Alive, About and Licenses

### 11.1 Keep Alive — Android only

Scaffold. Coding section: Wake lock `SwitchRow(ZAP)` + divider; Foreground service `SwitchRow(BELL)` + divider; Fake audio `SwitchRow(MUSIC)`. System section: Ignore battery optimization `SwitchRow(BATTERY)`. Each app toggle persists then immediately starts/stops or updates the keep-alive service. Foreground/fake-audio enable paths request notification permission when required and toast. Battery row reflects `PowerManager.isIgnoringBatteryOptimizations`; toggling on opens the system whitelist request if needed; toggling off has no direct revoke path. `KeepAliveSettingsScreenView.java:29-99`; Android factory glue `ScreenFactories.java:580-642`.

Windows must not render the Settings row/page/route. The settings domain should expose a platform capability so hidden UI cannot be deep-linked.

### 11.2 About

Scaffold; content padding `16/16/16/100`. Header column centered with vertical padding20: logo `88×88` accent-muted radius44, code glyph48; app name bold20/top12; version 16 secondary/top4. Group labels are 13 tertiary, left4/top16/bottom8. Each info/action row is an `ActionRow` given its own elevated/radius12 background and bottom8: Version/package (no click), Developer/user (no click), QQ/message (no click), GitHub (chevron/click), Licenses (chevron/click). Footer is 11 tertiary centered/top20. Android reads package label/version; Windows injects build metadata. `AboutScreenView.java:26-130`.

### 11.3 Licenses

Scaffold content padding `12/12/12/100`. Four elevated radius12 cards, padding12/bottom8: CommonMark core, GFM tables, JSch, Lucide. Name 16 normal; artifact/license metadata 13 secondary; description 11 tertiary/+3 line spacing. `LicensesScreenView.java:15-44`.

## 12. Accessibility deletion and coupling checklist

This is deletion scope only; there is no target view tree.

- Remove `AdvancedFeaturesScreenView` and `PhoneControlScreenView`, their `ScreenFactories` factories, registration in `MainChatView`, route IDs/back mappings, controller handlers, disclaimer state, accessibility-enabled checks, and all related strings/icons only used by them.
- Remove the Settings Tools-group Advanced row; close the preceding divider logic so Extensions becomes the last row with no trailing divider.
- Remove the manifest accessibility service declaration and `android.permission.BIND_ACCESSIBILITY_SERVICE`/metadata XML; remove service implementation, node/action helpers, gesture/screenshot commands, repositories/settings keys, AI tool exposure and tests.
- Do not leave disabled placeholders, “unsupported” cards, empty Advanced screens, searchable settings entries, deep links, or Windows stubs.
- Validate with repository-wide case-insensitive search for `accessibility`, `PhoneControl`, `BIND_ACCESSIBILITY_SERVICE`, service class/action IDs and the disclaimer key.

### Android manifest, Gradle and resource impact

- Delete `android.permission.BIND_ACCESSIBILITY_SERVICE` and the exported `LineCodeAccessibilityService` block plus `@xml/accessibility_service_config`: `app/src/main/AndroidManifest.xml:16,64-74`. Delete that XML after all references are gone.
- Retain the keep-alive permissions and service only in the Android target: foreground service, special-use foreground service, notifications, wake lock, ignore-battery-optimization and `KeepAliveService` (`AndroidManifest.xml:6-10,55-62`). They must not leak into the portable C++ settings domain or Windows packaging.
- Retain Termux permission/package query and terminal-provider permission/query only in Android manifests (`AndroidManifest.xml:11,18-29`). Windows has neither UI route nor stub permission.
- Retain the three Android providers used by log opening, sharing and workspace document exposure (`AndroidManifest.xml:76-96`); Windows implementations use native file handles/dialogs instead of URI authorities.
- Settings layouts are programmatic Java; there are no XML layout files to port. Text comes from `values/strings.xml` with Chinese and Russian locale siblings. Remove `screen_advanced_*`, phone-control/accessibility/service-description/disclaimer strings only after repository-wide reference checks; keep all strings named by retained rows and dialogs.
- About/Licenses exposes CommonMark `0.28.0`, GFM tables `0.28.0`, JSch `2.28.2`, Lucide `1.14.0` (`LicensesScreenView.java:20-27`). Dependency declarations are in `gradle/libs.versions.toml:6-15`, `markdown/build.gradle.kts:12-13`, `feature-ssh/build.gradle.kts:14`; the C++ build must update the visible license inventory to the libraries actually shipped rather than blindly preserving Gradle coordinates.

## 13. Complete read ledger

The following files were read for this zone. Paths are relative to the legacy repository.

### Page implementations

- `app/src/main/java/cn/lineai/ui/component/SettingsScreenView.java` (1-166)
- `SimpleSettingsScreenView.java` (1-72)
- `LLMSettingsScreenView.java` (1-180)
- `PromptTemplatesScreenView.java` (1-181)
- `InputSettingsScreenView.java` (1-136)
- `OutputSettingsScreenView.java` (1-105)
- `SecuritySettingsScreenView.java` (1-100)
- `ToolSettingsScreenView.java` (1-377)
- `MCPSettingsScreenView.java` (1-225)
- `SshSettingsScreenView.java` (1-225)
- `TermuxIntegrationScreenView.java` (1-273)
- `DataSettingsScreenView.java` (1-26)
- `StorageManagementScreenView.java` (1-166)
- `MemorySettingsScreenView.java` (1-524)
- `ErrorLogsScreenView.java` (1-74)
- `KeepAliveSettingsScreenView.java` (1-100)
- `AboutScreenView.java` (1-131)
- `LicensesScreenView.java` (1-45)
- `AdvancedFeaturesScreenView.java` (1-96; deletion analysis only)
- `ModelListScreenView.java` (1-375)
- `ModelAddOptionsScreenView.java` (1-158)
- `ModelAddScreenView.java` (1-757)
- `ModelCompressionSectionView.java` (1-337)
- `ModelPickerDialog.java` (1-106)
- `ExtensionsScreenView.java` (1-101)
- `ExtensionDetailScreenView.java` (1-666)
- `AgentExtensionEditScreenView.java` (1-466)
- `McpExtensionEditScreenView.java` (1-362)
- `TerminalProviderDetailScreenView.java` (1-279)

### Shared UI, route and direct data/theme dependencies

- `ScreenScaffoldView.java`, `ScreenHeaderView.java`, `SettingsSectionView.java`, `SectionHeaderView.java`
- `ActionRowView.java`, `SwitchRowView.java`, `OptionRowView.java`, `FormTextFieldView.java`, `DisclosureSectionView.java`
- `ModelFormHelper.java`, `CardViewHelper.java`, `AdaptiveActionsView.java`, `DialogDimensions.java`, `RefreshCwButtonView.java`
- `ui-theme/src/main/java/cn/lineai/ui/theme/LineTheme.java`, `FlowLayoutView.java`, `IconButtonView.java`
- `core-model/src/main/java/cn/lineai/model/ThemePalette.java`
- `app/src/main/java/cn/lineai/ui/component/ScreenFactories.java`
- `app/src/main/java/cn/lineai/mvp/ScreenNavigationController.java`
- `app/src/main/java/cn/lineai/ui/MainChatView.java:446-490` (factory registration)
- `app/src/main/AndroidManifest.xml` (permissions/services/providers relevant to this zone)
- `gradle/libs.versions.toml`, `markdown/build.gradle.kts`, `feature-ssh/build.gradle.kts` (license/dependency references)
- All directly referenced `screen_*`/common keys in `app/src/main/res/values/strings.xml` and locale siblings `values-zh/strings.xml`, `values-ru/strings.xml`

## 14. Intentionally not expanded here / referenced follow-up

- `ToolCallPreviewScreenView` is a preview/detail destination linked from Output but belongs to the chat/tool rendering audit, not settings zone B.
- SkillHub store/login/center/web/publish and store-detail pages are linked from the Skills extension detail but are a separate online-store flow; only the entry and transition are specified here.
- Tutorial is linked as a bare Settings row but its body belongs to onboarding.
- `PhoneControlScreenView` and every accessibility implementation file are intentionally excluded from target specification and are covered only by the deletion checklist.
- Controller/repository/network/storage implementations were followed only far enough to define UI state and platform seams. Their persistence schemas, import archive validation, SSH protocol, model clients, MCP transport and memory database belong to functional migration specs.
- Icon vector path geometry is owned by `IconButtonView`; this document records icon semantic type, well and glyph bounds, not each SVG path command.
