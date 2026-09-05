# Project Workflow

Use the actual installed CLI and generated project as the contract. CLI arguments can change between SDK versions.

## Locate the tools and SDK

For an existing configured project, read `HuxerUI_DIR` in the active `CMakeCache.txt` and prefer that package over `HUXERUI_HOME` or another CLI installation.

When the task creates, configures, builds, runs, or diagnoses a project:

1. Identify the `huxerui` executable selected by the shell.
2. Run `huxerui --version` and inspect `huxerui --help` before relying on its commands.
3. Run `huxerui doctor` for setup or environment diagnosis, scoped to the affected platform when possible.
4. Try the relevant subcommand help before use. If that CLI does not implement subcommand `--help`, rely on its top-level usage and an isolated generated project rather than guessing flags.

The packaged CLI exposes these top-level forms at the time this Skill is distributed:

```text
huxerui create app <name> [--id <project-id>] [-p|--platform <platform-list>] [--agent <agent-list>]
huxerui create library <name> [--namespace <cpp-namespace>] [--target <public-cmake-target>] [--id <project-id>] [-p|--platform <platform-list>] [--agent <agent-list>]
huxerui platform add <platform-list>
huxerui doctor [platform-list]
huxerui setup <platform-list> [--yes]
huxerui devices [platform]
huxerui build [platform-list] [--device <id>] [--profile debug|release] [--generator <name>] [--source <path>] [--java-home <path>]
huxerui run <platform> [--device <id>] [--profile debug|release] [--generator <name>] [--source <path>] [--java-home <path>]
huxerui package <platform-list> [--device <id>] [--profile debug|release] [--generator <name>] [--source <path>] [--java-home <path>]
huxerui open ios [--source <path>]
```

Re-read current help before copying these commands.

The CLI copies this Skill into new projects by default.
`--agent` accepts `codex`, `claude`, `antigravity`, `opencode`, `command-code`, `omp`, `dsh`, and `zcode`; `all` selects every distinct supported Skill directory, while `none` omits the Skill.
An explicit list replaces the default `codex` selection.

## Create a minimal application

Choose the name, project identifier, and platforms from the user's request or current CLI defaults. Do not add unrequested platforms.

```powershell
huxerui doctor windows
huxerui create app SampleApp --id org.example.sampleapp --platform windows
Set-Location SampleApp
huxerui build windows --profile debug
huxerui run windows --profile debug
```

Use `--source <path>` only when the user explicitly wants the application to compile HuxerUI from a local source checkout instead of the installed SDK binaries.
The path must name the HuxerUI repository root, and the override applies only to that `build`, `run`, `package`, or `open ios` invocation; do not rewrite the user's persistent `HUXERUI_HOME` for this workflow.
For an Android build that requires a specific JDK, pass `--java-home <path>` instead of changing the user's persistent `JAVA_HOME` or generated Gradle files.

On another host, replace `windows` with an available requested platform. Do not claim a platform ran unless it did.

The generated application has this common shape; each requested platform contributes its own subtree:

```text
SampleApp/
  CMakeLists.txt
  src/
    app.cpp
  resources/
    images/
    raw/
    strings/
      default.properties
  platform/
    <selected-platform>/
  <selected-skill-root>/
    huxerui-app-development/
```

The selected Skill root is `.agents/skills` by default and may instead or additionally be `.claude/skills` or `.zcode/skills` according to `--agent`. The root CMake file recursively discovers C++ sources under `src`, calls `huxerui_add_app`, registers `resources` under namespace `app`, and enables composable code generation through that helper.

A generated library has its public header and implementation at the root, plus a normal consumer application under `examples/preview`:

```text
LibraryName/
  CMakeLists.txt
  include/<public-target-package>/<public-target-product>.h
  src/<project-identifier>.cpp
  resources/
    images/
    raw/
    strings/default.properties
  platform/<selected-platform>/
  examples/preview/
    CMakeLists.txt
    src/app.cpp
    resources/
      images/
      raw/
      strings/default.properties
    platform/<selected-platform>/
  <selected-skill-root>/huxerui-app-development/
```

The library name owns the repository directory, display name, and private source identity.
`--namespace` selects the exact generated C++ namespace, while `--target` selects the exact public CMake target and deterministically projects the lowercase public header path, implementation target, and resource namespace.
For example, `--namespace acme::camera --target Acme::Camera` produces `<acme/camera.h>`, namespace `acme::camera`, implementation target and resource namespace `acme_camera`, and public alias `Acme::Camera`.
An unqualified target such as `camera` uses `camera` directly without an alias and produces `<camera/camera.h>`.
The library root uses `huxerui_add_library`; its preview consumes the public target through `huxerui_use_library`. Some platform package subtrees are present only when the selected platform needs them.

Inspect the current generated result because this layout can evolve. Do not hand-create a stale replacement when the CLI is available.

## Understand an existing application

Before the smallest safe edit:

1. Read root and nested `CMakeLists.txt` files, relevant CLI configuration, `src`, `resources`, and only the platform configuration involved in the task.
2. Identify the application target and whether it uses `huxerui_add_app`, `HuxerUI::huxerui`, or `HuxerUI::huxerui_static`.
3. Locate the active build directory and its `HuxerUI_DIR`, generator, compiler, configuration, and platform options.
4. Identify the application root, `Application` declaration, theme boundary, state ownership, codegen-enabled sources, resource namespaces, and app-side libraries.
5. Preserve the existing generated structure. Do not regenerate or scaffold over the project to make a small edit.

## Public CMake contract

Installed SDK consumption uses:

```cmake
find_package(HuxerUI CONFIG REQUIRED)
```

The package can expose `HuxerUI::huxerui` and `HuxerUI::huxerui_static`. Android selects shared, Web and iOS select static, and desktop packages can expose both. Prefer `huxerui_add_app` for generated applications because it selects the platform-appropriate framework target, enables C++20/codegen, and owns app resource integration.

The macOS SDK carries the iOS device and Simulator static slices in `share/huxerui/platform/ios/HuxerUI.xcframework`.
Installed iOS projects consume the selected slice through the canonical HuxerUI CMake target; do not link an XCFramework archive path directly.

For manually defined consumer targets, call `huxerui_enable_codegen(target)` after all marked source files are added. Do not include SDK-private paths or link binary files by hand when an exported target owns them.

Acquire an app-side library from a local path or an HTTPS Git repository through `huxerui_use_library`:

```cmake
huxerui_use_library(my_app
        TARGET CameraKit::CameraKit
        PATH "${CMAKE_CURRENT_SOURCE_DIR}/libraries/CameraKit"
)

huxerui_use_library(my_app
        TARGET Charts::Charts
        URL "https://github.com/example/Charts.git"
        COMMIT "0123456789abcdef0123456789abcdef01234567"
)

huxerui_use_library(my_app
        TARGET Icons::Icons
        URL "https://github.com/example/Icons.git"
        TAG "v1.2.0"
)
```

PATH and URL are mutually exclusive.
URL requires exactly one of COMMIT or TAG; use a full commit SHA for reproducible builds, because a repository owner can move a tag.
TAG accepts a plain Git tag name rather than a branch or a `refs/tags/` value.
Do not add a second dependency manifest or invoke FetchContent separately for a library already owned by this helper.

## Build and run without changing toolchains

- Reuse the project's compatible build directory and generator.
- On Windows, keep the existing MSVC generator; do not switch to MinGW or pin a Visual Studio release without a project requirement.
- Build only the current host and platforms affected by the task by default.
- Preserve existing Android ABI, NDK, Gradle, iOS Xcode, Web Emscripten, and desktop settings.
- Use `huxerui devices` when a platform needs a device selection.
- On Termux, Android and Web use the current device directly: do not pass `--device` or add ADB. Web uses the CLI-planned Python loopback server and opens its URL through `termux-open`.
- A Termux Android run opens the APK in the system installer; complete installation and choose Open there.

## Package applications

Use `huxerui package <platform-list>` for user-requested distribution output and inspect `dist/<platform>` only after the command succeeds.
Release is the default package profile.
Windows produces one setup executable, macOS one DMG, and Linux one AppImage; Android, iOS, and Web retain their platform build outputs.
Windows setup generation currently supports x64 applications.
Do not require WiX, `appimagetool`, or `hdiutil` for ordinary `build` or `run`.
Windows package mode restores its pinned WiX packages automatically and requires `Microsoft.NETCore.App` 6.0 or newer only to run the restored tool.

Desktop package content comes only from CMake install rules using the application target's `HUXERUI_APPLICATION_INSTALL_COMPONENT` property.
Add an explicit platform-conditional `install()` rule for each third-party dynamic library, plugin, codec, or data file that the application owns; do not scan adjacent output files or copy an inferred dependency directory.

A generated Windows application keeps its editable HuxerUI installer under `platform/windows/package`.
Localize its interface through `package/resources/strings/default.properties` and additional HuxerUI locale catalogs instead of adding an installer-specific locale store.
Use the Windows-only `<huxerui/windows/installer.h>` handle and its one observed `InstallerStatus` when customizing that interface.
Read the authored destination and desktop-shortcut defaults from that status, launch `ChooseDestinationAsync()` from the component's `TaskScope`, and submit user overrides together through `InstallerInstallOptions`; do not add a second installer configuration store.
Keep the Start menu entry in MSI, model optional shortcuts as MSI Features, and never request taskbar pinning from the installer process.
Keep Burn responsible for detection, elevation, caching, apply, cancellation, rollback, repair, and uninstall, and do not create a parallel installer state store or use the installer API in the ordinary application executable.

## Diagnose by phase

Classify a failure before changing code:

- SDK discovery: `HUXERUI_HOME`, `HuxerUI_DIR`, version, package architecture.
- CLI setup: doctor result and platform tools.
- Project planning or platform shell generation.
- CMake configure and target selection.
- composable code generation.
- resource compilation or staging.
- C++ compilation.
- linking and runtime binary deployment.
- platform launch, device, browser, or host integration.

Report the exact failing phase and command. Never use a source checkout as an implicit dependency unless the existing project explicitly does so.
