# Contributing to LineCode Pro

Thank you for improving LineCode Pro. Use an issue or discussion for substantial design changes before investing in a large implementation.

## Project expectations

- Application code is hand-written **Java 11**. Do not add Kotlin, Compose, XML layouts, or new runtime UI frameworks.
- Keep the `modules/` boundaries from `settings.gradle.kts`: features never depend on other features, and core modules stay Android-free. Run `:arch-test` before opening a pull request.
- Depend on ports and data-driven descriptors at boundaries. Do not add UI branches keyed by tool or domain names; `ToolPresentation`/`ToolIcon` own tool presentation metadata.
- Preserve the established LineCode UI and behavior unless the change intentionally addresses an approved difference.
- Add unit coverage for behavior changes. Keep credentials, signing material, generated output, and user data out of commits.
- Build tool arguments with `ToolSchemaBuilder`. Reuse `SkillStore.importSkill` / `RemoteSkillInstall` instead of creating a second install pipeline.

## Local verification

Run the same checks as CI:

```sh
./gradlew verifyAll :arch-test:test
./gradlew test :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew clean :app:assembleDebug
```

The default build is the **slim** variant: it contains no llama.cpp libraries, no local-model Java, and no local-model UI. Test the optional local variant separately:

```sh
./gradlew clean :app:assembleDebug -PlinecodeEnableLocalModel=true
```

The local build needs the NDK and the llama.cpp runtime vendored in `third_party/llama-cpp`. Switching between slim and local requires `clean`, because both variants share the APK output path.

Release builds require a local `signing.properties`; never commit it.

## Pull requests and the CLA

Use the pull request template, describe observable behavior, list the checks you ran, and include screenshots for UI changes. Every pull request must accept [CLA.md](CLA.md) and contain a matching electronic signature:

```text
- [x] I have read and agree to the [Contributor License Agreement](https://github.com/LangLang03/LineCodePro/blob/master/CLA.md).
CLA Signature: @your-github-username
```

The signature must match the pull request author's GitHub login. The `CLA / verify` status check fails when either line is missing or altered. Repository maintainers should configure branch protection to require this check before merge.

By submitting a pull request, you also confirm that the contribution contains no unlicensed third-party code or confidential material.
