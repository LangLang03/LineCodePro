## Summary

<!-- What changed, why it is needed, and which issue it resolves. -->

Closes #

## User-visible behavior

<!-- Describe the behavior before and after this pull request. -->

## Screenshots or recordings

<!-- Required for UI changes. Include comparable before/after states. -->

## Validation

- [ ] `./gradlew verifyAll :arch-test:test` passes
- [ ] `./gradlew test :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:lintDebug` passes
- [ ] `./gradlew clean :app:assembleDebug` builds the slim APK
- [ ] Verified on a device or emulator, or explained why this is not applicable

Local-model changes additionally require:

- [ ] `./gradlew clean :app:assembleDebug -PlinecodeEnableLocalModel=true` builds
- [ ] The local APK contains `lib/arm64-v8a/libllama.so` and the local model UI
- [ ] The default slim APK contains no `libllama*.so`, no `libggml*.so`, and no local-model classes

Commands and environment used:

```text

```

## Design and safety checklist

- [ ] The change respects the module boundaries in `settings.gradle.kts` and `:arch-test`.
- [ ] New behavior is behind an abstraction or data-driven descriptor where extension is expected; it does not grow a UI `if` chain or branch on tool/domain names.
- [ ] Tool arguments are built with `ToolSchemaBuilder`; no hand-concatenated JSON.
- [ ] Skill/SkillHub/GitHub installs reuse `SkillStore.importSkill` or `RemoteSkillInstall` instead of a second pipeline.
- [ ] New or changed behavior has unit coverage and stays within the 600-line budget.
- [ ] No credential, signing key, personal data, generated build output, or incompatible third-party code is included.
- [ ] User-facing text is in `R.string` for English, Chinese and Russian where applicable.
- [ ] UI changes preserve LineCode behavior and were visually checked.

## CLA — required

Checking the following box and typing the pull request author's exact GitHub username constitutes an electronic signature of the [Contributor License Agreement](https://github.com/LangLang03/LineCodePro/blob/master/CLA.md). Both lines are validated by the required `CLA / verify` CI check.

- [ ] I have read and agree to the [Contributor License Agreement](https://github.com/LangLang03/LineCodePro/blob/master/CLA.md).

CLA Signature: @your-github-username
