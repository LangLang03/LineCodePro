# Repository Guidelines

## Project Structure & Module Organization

Application code lives in `src/`: `domain/` holds core models, `application/` holds workflows and ports, `infrastructure/` implements adapters, `presentation/` builds the HuxerUI interface, and `app/` wires services together. Android Gradle configuration and Java platform bridges live in `platform/android/`; Windows packaging lives in `platform/windows/`. Put localized text in `resources/strings/`, images in `resources/images/`, native tests in `tests/`, and development scripts in `tools/`.

## Build, Test, and Development Commands

Use HuxerUI SDK 0.3.0. Android builds also need JDK 17, Android SDK 36, NDK `29.0.14206865`, CMake, and Ninja.

```sh
cmake -S tests -B build/tests-ninja -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build/tests-ninja --parallel 8
ctest --test-dir build/tests-ninja --output-on-failure -j8
(cd tools && python3 -m unittest test_fake_ai_server.py -v)
./platform/android/gradlew -p platform/android :app:lintDebug :app:assembleDebug --max-workers=8
```

The first three commands configure, build, and run native GoogleTest targets. The Python command checks the fake AI server; Gradle runs Android lint and produces a debug APK. Signed release builds require a local `platform/android/signing.properties`.

## Release Notes

`update.md` is the versioned Chinese changelog. Before a release, add a `## v<versionName>` section matching Android's `versionName`. `.github/workflows/release.yml` extracts that section for GitHub release notes; if absent, it falls back to recent commits.

## Coding Style & Naming Conventions

Write application code in C++23; use Java only for Android APIs that require a platform bridge. Follow nearby formatting: C++ uses two-space indentation, Java uses four. Use `snake_case` C++ filenames (for example, `project_workspace_service.cpp`), descriptive PascalCase types, and `snake_case` resource keys. Keep domain, application, infrastructure, and presentation responsibilities separate; depend on ports across boundaries. No repository-wide C++ formatter is configured; Android Lint and native compiler warnings are enforced in verification.

## Testing Guidelines

Add focused GoogleTest coverage for behavior changes in `tests/*_tests.cpp`. Put Android JVM tests in `platform/android/app/src/test/` with `*Test.java` names when Java bridge behavior changes. Run the relevant suite before opening a PR, and verify UI changes on a device or emulator when available. Keep user-facing strings in both `default.properties` and `zh.properties`.

## Commit & Pull Request Guidelines

Recent commits use short prefixes such as `feat:`, `fix:`, `test:`, `docs:`, `ci:`, and `release:` followed by an imperative summary. Use `.github/pull_request_template.md`: link the issue, describe visible behavior, record validation commands, and include before/after screenshots for UI changes. Complete its CLA checkbox and `CLA Signature: @your-github-username` with the PR author's exact GitHub login. Never commit credentials, signing keys, user data, or generated build output.
