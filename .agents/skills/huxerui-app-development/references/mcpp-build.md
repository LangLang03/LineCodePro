# Standalone mcpp Builds

> **Prefer the native path when the HuxerUI source tree is available.**
> HuxerUI is an mcpp package: an application declares `huxerui` as a dependency
> and calls `huxerui::rules::configure()` from its `build.mcpp`, and the SDK
> include directory, the library, the platform link interface, the composable
> transform (`hcg`) and the resource compiler (`hrc`) all arrive through that
> one edge. See `mcpp/README.md` and `mcpp/examples/`. None of the manual
> recovery below applies there.
>
> The rest of this file covers the remaining case: an **independent** mcpp
> project that consumes a released HuxerUI SDK without a source checkout, built
> through the delegating `huxerui mcpp build` frontend.

Use this reference for an independent mcpp project that includes HuxerUI headers or links an HuxerUI SDK library directly.
The `huxerui mcpp` frontend validates `mcpp.toml` and delegates to `mcpp build`; it does not discover the SDK, inject compiler flags, translate CMake link interfaces, run HuxerUI code generation, or run the resource compiler.

## Establish the build truth

Before editing or compiling:

1. Run `huxerui mcpp --help`, `mcpp --version`, and `huxerui doctor` when those executables are available.
2. Locate the active SDK from `HUXERUI_HOME`, the configured project's `HuxerUI_DIR`, or the SDK installation surrounding the selected `huxerui` executable.
3. If the HuxerUI source checkout is available, read `README.md`, `docs/development/building.md`, and the current `.github/workflows/sdk-release.yml` for the release compiler and platform environment.
4. Do not infer the SDK's original compiler from the local compiler, from an installed CMake package, or from the fact that a header compiles.

The SDK CMake package describes how to consume the library, not a complete compiler-provenance or ABI guarantee.
`huxerui doctor` diagnoses host prerequisites, but a successful result does not prove that an mcpp binary has a compatible ABI.

## Configure mcpp deliberately

Keep `mcpp.toml` responsible for every setting the standalone frontend needs:

- Select the C++ standard explicitly; C++20 is the HuxerUI SDK baseline.
- Select the same compiler family, architecture, standard library, and runtime linkage as the SDK release for the target platform whenever ABI compatibility matters.
- Point `include_dirs` at the SDK's public `include` directory only.
- Point library search paths at the SDK's `lib` directory and use the public library name; do not include private HuxerUI source directories or link object files by hand.
- For static linking, reproduce the platform dependencies and link order exposed by the installed HuxerUI CMake target. For shared linking, retain the SDK's runtime library deployment and search-path requirements.
- Keep `static_stdlib`, runtime library directories, platform libraries, and deployment targets consistent with the SDK and the final distribution policy.

Build the selected source directory with:

```text
huxerui mcpp build --source <project> [--release] [--locked] [--offline] [--verbose]
```

The source directory must contain `mcpp.toml`.
Use `mcpp run` only after the mcpp build succeeds and only when the project defines a runnable target.

## Release toolchain reference

The repository's current release CI is the source of truth; re-read `.github/workflows/sdk-release.yml` when an exact version matters.

- The framework baseline is C++20 with required standard support and extensions disabled.
- Release SDK libraries use `HUXERUI_ENABLE_PROFILING=OFF`; an application-side macro cannot add profiling to a prebuilt SDK.
- Linux SDKs are built in `ubuntu:24.04` with `CC=gcc-14`, `CXX=g++-14`, CMake 3.31.10, and Release configuration for x86_64 and aarch64.
- Windows SDKs use the `windows-2022` MSVC developer environment and `Ninja Multi-Config` for x86_64; do not use MinGW.
- macOS SDKs use Xcode 26.2 on `macos-15` or `macos-15-intel`, with macOS deployment target 12.0.
- Android host SDKs use Android NDK 29.0.14206865, Android API 24, and `arm64-v8a`; the shared framework library is enabled and the static library is disabled.
- Web artifacts use Emscripten 4.0.19 and the static library; iOS artifacts use Xcode 26.2 and an iOS 15.0 deployment target.

Linux release binaries are checked against maximum symbol versions of GLIBC 2.38, GLIBCXX 3.4.32, and CXXABI 1.3.15.
The separate Linux host-tool workflow builds `hcg` and `hrc` in a GLIBC 2.28 environment with a static GNU C++ runtime; that host-tool constraint is not the HuxerUI application-library ABI.

## Recover the SDK link information

Prefer an installed SDK selected by the project or user:

1. Read `HUXERUI_HOME` if it is set and verify that it contains `include/huxerui/huxerui.h` and `lib/cmake/HuxerUI/HuxerUIConfig.cmake`.
2. For a configured CMake consumer, read `HuxerUI_DIR` from `CMakeCache.txt` and walk up from `lib/cmake/HuxerUI` to the SDK prefix.
3. Otherwise resolve the `huxerui` executable on `PATH` and inspect the adjacent SDK layout, then run `huxerui doctor`.
4. Read the installed `HuxerUIConfig.cmake`, exported target files, and platform-specific package fragments to identify the library names, include directories, link libraries, link options, and runtime files needed by the selected target.

Do not substitute a source checkout's `src`, private platform paths, or build-directory object files for an installed SDK unless the user explicitly requested a source build.
When the repository itself is the SDK source, use the current checkout's public `include` and built public library outputs and record that the result is a source-build experiment rather than a release-compatibility claim.

An independent manifest normally needs these categories, using the syntax supported by the installed mcpp version:

```toml
[package]
standard = "c++20"

[toolchain]
default = "<compiler family and version matching the SDK release>"

[build]
include_dirs = ["<sdk>/include"]
static_stdlib = false
ldflags = ["<sdk library and platform link options>"]

[runtime]
library_dirs = ["<runtime library directories>"]
```

`mcpp/examples/01-import/mcpp.toml` is not a shape example for this case: it uses the native path (one dependency line) and carries none of the manual SDK wiring described here.

## C++23 and C++26

The SDK libraries are built as C++20 libraries; setting `standard = "c++23"` or `standard = "c++26"` in the mcpp project does not rebuild them.
Use a newer standard only when the selected compiler supports it, the HuxerUI public headers compile under it, and the resulting binary passes the target platform's ABI and runtime checks.
Prefer C++20 when portability or release parity is more important than application-only language features.

A newer `-std` setting is not an ABI compatibility solution.
Do not mix `libstdc++` and `libc++`, MSVC and MinGW, incompatible architectures, or incompatible static/dynamic runtime policies at a library boundary.
When an app-side library exposes standard-library or HuxerUI types across its binary boundary, build the whole boundary with the same compiler family, standard library, architecture, exception/RTTI policy, and compatible runtime linkage.

## Validate the result

Classify failures before changing the manifest:

- Missing headers or libraries usually mean the SDK root or public link interface is wrong.
- Undefined HuxerUI or platform symbols usually mean a missing transitive dependency or incorrect static link order.
- `GLIBC`, `GLIBCXX`, or `CXXABI` version errors indicate that the final binary was built against a newer runtime than the release policy permits.
- Loader failures indicate a deployment or runtime library path problem, not a C++ language-standard problem.

For Linux distribution builds, inspect the final binary with `readelf` and `ldd`, and use `.github/workflows/scripts/check_linux_binary_compatibility.sh` when the repository checkout is available.
For macOS use `otool -L` and deployment-target inspection; for Windows use the active MSVC toolchain and dependency inspection; for Android, Web, and iOS use the platform toolchain and ABI defined by the release workflow.
Do not claim compatibility from a successful compile alone: record the exact compiler, standard library, architecture, linkage, and runtime checks that actually ran.

A standalone project without the source tree still needs an explicit integration for the HuxerUI code generator and resource compiler, or an equivalent source-level implementation.
With the source tree available, `huxerui.rules` already provides it — `mcpp/examples/` holds three applications whose whole manifest is one dependency line.
