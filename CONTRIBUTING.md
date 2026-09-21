# Contributing to LineCode Pro

Thank you for improving LineCode Pro. Use an issue or discussion for substantial design changes before investing in a large implementation.

## Project expectations

- Write application code in modern C++23. Java is limited to the Android platform bridge where the NDK cannot provide the required API.
- Keep domain, application, infrastructure, platform, and presentation responsibilities separated. Depend on ports at boundaries rather than concrete stores or transports.
- Prefer small composable types, templates, concepts, and value semantics where they make contracts clearer. Avoid feature switches built from growing UI `if` chains; use data-driven descriptors or polymorphic behavior.
- Preserve the established LineCode UI and behavior unless the change intentionally addresses an approved difference.
- Add GoogleTest coverage for behavior changes and keep credentials, signing material, generated output, and user data out of commits.

## Local verification

Install the current HuxerUI SDK, then run the same checks as CI:

```sh
cmake -S tests -B build/tests-ninja -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build/tests-ninja --parallel 8
ctest --test-dir build/tests-ninja --output-on-failure -j8
(cd tools && python3 -m unittest test_fake_ai_server.py -v)
./platform/android/gradlew -p platform/android :app:lintDebug :app:assembleDebug --max-workers=8
```

Release builds require a local `platform/android/signing.properties`; never commit it.

## Pull requests and the CLA

Use the pull request template, describe observable behavior, list the checks you ran, and include screenshots for UI changes. Every pull request must accept [CLA.md](CLA.md) and contain a matching electronic signature:

```text
- [x] I have read and agree to the [Contributor License Agreement](https://github.com/LangLang03/LineCodePro/blob/main/CLA.md).
CLA Signature: @your-github-username
```

The signature must match the pull request author's GitHub login. The `CLA / verify` status check fails when either line is missing or altered. Repository maintainers should configure branch protection to require this check and the main CI workflow before merge.

By submitting a pull request, you also confirm that the contribution contains no unlicensed third-party code or confidential material.
