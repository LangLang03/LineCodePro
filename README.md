![LineCode Pro](https://socialify.git.ci/LangLang03/LineCodePro/image?description=1&font=KoHo&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2FLangLang03%2FLineCodePro%2Frefs%2Fheads%2Fmaster%2F.idea%2Ficon.svg&name=1&pulls=1&stargazers=1&pattern=Circuit%20Board&theme=Auto)

# LineCode Pro

[中文](README_CN.md) · English

[![CI](https://img.shields.io/github/actions/workflow/status/LangLang03/LineCodePro/ci.yml?branch=master&style=flat-square&label=CI)](https://github.com/LangLang03/LineCodePro/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/actions/workflow/status/LangLang03/LineCodePro/release.yml?style=flat-square&label=Release)](https://github.com/LangLang03/LineCodePro/releases)
[![License: AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--3.0--or--later-663399?style=flat-square)](LICENSE)
[![C++23](https://img.shields.io/badge/C%2B%2B-23-00599C?style=flat-square&logo=cplusplus)](CMakeLists.txt)
[![HuxerUI](https://img.shields.io/badge/HuxerUI-0.3.0-ff6b6b?style=flat-square)](https://github.com/HuxerUI/HuxerUI)
[![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84?style=flat-square&logo=android)](platform/android/gradle.properties)

**LineCode Pro is a native, cross-platform AI coding workspace built with modern C++23 and HuxerUI.** It combines streaming conversations, project-aware tools, multi-agent workflows, remote execution, extensibility, and local data management in one focused interface.

It is not just a chat client. A model can inspect a selected workspace, propose or apply file changes, run approved commands, search the web, work with images, delegate tasks to Agents, and continue through multi-step tool calls while the app keeps the complete execution timeline visible and reviewable.

> LineCode Pro is under active development. Review AI-generated code and commands before using them in important environments.

## Screenshots

<table>
  <tr>
    <td align="center"><img src=".github/assets/chat.png" alt="New conversation" width="260"><br><sub>Conversation</sub></td>
    <td align="center"><img src=".github/assets/conversations.png" alt="Conversation drawer" width="260"><br><sub>Projects and history</sub></td>
    <td align="center"><img src=".github/assets/settings.png" alt="Settings" width="260"><br><sub>Models, tools and appearance</sub></td>
  </tr>
</table>

## What it does

- **Model-independent conversations** — OpenAI-compatible Chat Completions, OpenAI/Codex Responses, Anthropic Messages, and local GGUF models share one streaming interface.
- **A complete tool loop** — read, create, edit, search, and organize project files; execute shell commands; search and fetch web content; understand or generate images; update task lists and memory.
- **Agents and pipelines** — delegate focused work to coding or exploration Agents, run dependent stages, inspect live progress, and collect the result back into the parent conversation.
- **Workspace choices** — operate on local Android storage, an SSH workspace, Termux, or a compatible Android terminal-provider service.
- **Reviewable changes** — file operations produce diff records; reasoning, retries, approvals, tool calls, output, and final answers remain ordered in the conversation timeline.
- **Extensible by design** — connect HTTP MCP servers, define custom Agents, install project or global `SKILL.md` packages, and discover community Skills through SkillHub.
- **Long-session support** — context usage tracking, automatic compaction, conversation search, durable memories, and resumable histories keep larger tasks manageable.
- **Native themes and layouts** — the HuxerUI interface includes light, dark, system, coffee-paper, high-contrast, custom-color, drawer, sheet, dialog, Markdown, and code presentation states.

## Platforms

| Platform | Status | Notes |
| --- | --- | --- |
| Android 6.0+ (API 23+) | Primary | arm64 release; x86_64 is available for emulator verification. Android-only keep-alive and Termux integrations are included. |
| Windows | In development | Native HuxerUI target and installer packaging. Android-only keep-alive controls are intentionally hidden. |

## Privacy and control

LineCode Pro stores conversations, settings, memories, extension metadata, and diff history locally with SQLite. Your model credentials and selected project content are sent only to services that you explicitly configure or invoke. Tool execution supports automatic, confirmation, and read-only policies, and local file tools are constrained to the active workspace.

Export and diagnostic paths are designed to redact credentials, but you should still inspect archives and logs before sharing them. Treat every third-party model, MCP endpoint, Skill, SSH host, and web service according to its own privacy and security policy.

## Get LineCode Pro

Prebuilt artifacts and release notes are published on the [GitHub Releases](https://github.com/LangLang03/LineCodePro/releases) page. Android release packages retain the `cn.lineai` application id and continue the existing LineCode signing and version sequence.

## Build from source

This revision requires **HuxerUI SDK 0.3.0**. The official installer supports `--version`; use it explicitly so the local SDK matches CI instead of silently moving to a newer incompatible release:

```sh
curl -fsSL https://github.com/HuxerUI/HuxerUI/releases/latest/download/install.sh | sh -s -- --version 0.3.0 --yes
export HUXERUI_HOME="$HOME/.local/share/HuxerUI"
export PATH="$HUXERUI_HOME/bin:$PATH"
huxerui doctor android
```

Android builds also require JDK 17, Android platform 36, NDK `29.0.14206865`, CMake 3.22.1, and Ninja. Build an installable debug APK with eight workers:

```sh
./platform/android/gradlew -p platform/android :app:assembleDebug --max-workers=8
```

Run the native GoogleTest suite with Ninja:

```sh
cmake -S tests -B build/tests-ninja -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build/tests-ninja --parallel 8
ctest --test-dir build/tests-ninja --output-on-failure -j8
```

On Windows, install the same HuxerUI SDK version and create the native package with `huxerui package windows --project . --config Release`. Signed Android release builds additionally require private signing properties and are produced by the release workflow; signing material must never be committed.

## Community

- QQ discussion group: **1023548832**
- [GitHub Discussions](https://github.com/LangLang03/LineCodePro/discussions) for design discussion and general questions
- [GitHub Issues](https://github.com/LangLang03/LineCodePro/issues) for reproducible bugs and scoped feature requests

The QQ group is for community discussion only. For commercial licensing or CLA/legal questions, contact **jiyu03@qq.com**.

## Contributing

Contributions are welcome. The project uses C++23, HuxerUI, dependency inversion, single-responsibility boundaries, and automated GoogleTest/Android checks. See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

Every pull request must include the contributor's GitHub username as an electronic signature accepting [CLA.md](CLA.md). The required `CLA / verify` CI check rejects unsigned pull requests.

## License

LineCode Pro uses a dual-license model:

- Open-source use is available under **GNU AGPL v3 or any later version**; see [LICENSING.md](LICENSING.md) and [LICENSE](LICENSE).
- Organizations that cannot meet the AGPL obligations may request a separate commercial license; see [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md).

Unless a written commercial agreement says otherwise, receiving or using the source code does not grant a commercial-license exception.

## Acknowledgements

Special thanks to [HuxerUI](https://github.com/HuxerUI/HuxerUI), the native cross-platform UI framework that powers LineCode Pro. HuxerUI is open source under the **MIT License**; its copyright and full terms are available in the [HuxerUI LICENSE](https://github.com/HuxerUI/HuxerUI/blob/main/LICENSE). HuxerUI and every other third-party component remain governed by their own licenses.
